package com.viewer;

import android.content.Context;
import android.util.Log;

import org.webrtc.*;
import java.util.ArrayList;
import java.util.List;

public class WebRTCManager {
    private static final String TAG = "ViewerWebRTC";
    
    private Context context;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private boolean isViewing = false;
    private String currentConnectionType = "Unknown";
    private EglBase rootEglBase;
    
    // Callbacks
    private ConnectionTypeCallback connectionTypeCallback;
    private VideoTrackCallback videoTrackCallback;
    
    public interface ConnectionTypeCallback {
        void onConnectionTypeChanged(String type, String description);
    }
    
    public interface VideoTrackCallback {
        void onVideoTrack(VideoTrack videoTrack);
    }
    
    // ICE Servers (must match broadcaster)
    private static final List<PeerConnection.IceServer> ICE_SERVERS = new ArrayList<>();
    static {
        // STUN servers
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer());
        
        // TURN server (your Metered credentials)
        ICE_SERVERS.add(PeerConnection.IceServer.builder("turn:asia.relay.metered.ca:80")
            .setUsername("ef24325ba7b48683eea77921")
            .setPassword("A7u6UJTnE6KimONG")
            .createIceServer());
    }
    
    public WebRTCManager(Context context) {
        this.context = context;
        initializeWebRTC();
    }
    
    public void setConnectionTypeCallback(ConnectionTypeCallback callback) {
        this.connectionTypeCallback = callback;
    }
    
    public void setVideoTrackCallback(VideoTrackCallback callback) {
        this.videoTrackCallback = callback;
    }
    
    private void initializeWebRTC() {
        // FIXED: Create EGL base properly
        rootEglBase = EglBase.create();
        
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions());
        
        // FIXED: Use rootEglBase.getEglBaseContext() instead of ContextImpl
        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());
        
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }
    
    public void startViewing() {
        if (isViewing) return;
        
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(ICE_SERVERS);
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        
        peerConnection = factory.createPeerConnection(rtcConfig, new ViewerPeerConnectionObserver());
        isViewing = true;
        Log.d(TAG, "Started viewing mode");
    }
    
    public void stopViewing() {
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        isViewing = false;
        Log.d(TAG, "Stopped viewing");
    }
    
    public void setRemoteDescription(SessionDescription offer) {
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(new SimpleSdpObserver(), offer);
        }
    }
    
    public void createAnswer() {
        if (peerConnection != null) {
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            
            peerConnection.createAnswer(new SimpleSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription answer) {
                    peerConnection.setLocalDescription(new SimpleSdpObserver(), answer);
                    FirebaseSignalingClient.getInstance().sendAnswer(answer);
                }
            }, constraints);
        }
    }
    
    public void addIceCandidate(IceCandidate candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }
    
    public boolean isViewing() {
        return isViewing;
    }
    
    private void updateConnectionType(String type, String description) {
        currentConnectionType = type;
        if (connectionTypeCallback != null) {
            connectionTypeCallback.onConnectionTypeChanged(type, description);
        }
    }
    
    // PeerConnection Observer for Viewer
    private class ViewerPeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            if (iceCandidate.sdp.contains("typ relay")) {
                updateConnectionType("TURN", "🔄 Relay: Using TURN server (500MB/month limit)");
            } else if (iceCandidate.sdp.contains("typ srflx")) {
                updateConnectionType("STUN", "⚡ Direct: STUN connection (no limit)");
            } else if (iceCandidate.sdp.contains("typ host")) {
                updateConnectionType("HOST", "🏠 Local: Same network");
            }
            
            // Send to broadcaster via Firebase
            FirebaseSignalingClient.getInstance().sendIceCandidate(iceCandidate);
        }
        
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.d(TAG, "ICE connection state: " + newState);
            if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                updateConnectionType(currentConnectionType, 
                    currentConnectionType.equals("TURN") ? "🔄 TURN relay active" : "⚡ Direct STUN connected");
            } else if (newState == PeerConnection.IceConnectionState.FAILED) {
                updateConnectionType("FAILED", "❌ Connection failed - check TURN credentials");
            } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                updateConnectionType("DISCONNECTED", "⚠️ Connection lost");
            }
        }
        
        @Override
        public void onAddStream(MediaStream stream) {
            if (stream.videoTracks.size() > 0 && videoTrackCallback != null) {
                VideoTrack videoTrack = stream.videoTracks.get(0);
                videoTrackCallback.onVideoTrack(videoTrack);
                updateConnectionType("STREAMING", "📺 Live video stream received");
            }
        }
        
        @Override public void onSignalingChange(PeerConnection.SignalingState newState) {}
        @Override public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onDataChannel(DataChannel channel) {}
        @Override public void onRenegotiationNeeded() {}
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {}
    }
    
    // Simple SDP Observer
    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sd) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) { Log.e(TAG, "SDP error: " + error); }
        @Override public void onSetFailure(String error) { Log.e(TAG, "SDP set error: " + error); }
    }
}
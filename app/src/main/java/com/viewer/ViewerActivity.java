package com.viewer;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

public class ViewerActivity extends AppCompatActivity {
    private TextView statusText;
    private TextView connectionTypeText;
    private SurfaceViewRenderer videoView;
    private Button connectButton, disconnectButton;
    
    private WebRTCManager webRTCManager;
    private FirebaseSignalingClient signalingClient;
    private EglBase rootEglBase;
    
    private String deviceId;
    private boolean isConnected = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);
        
        deviceId = getIntent().getStringExtra("deviceId");
        
        // Initialize views
        statusText = findViewById(R.id.statusText);
        connectionTypeText = findViewById(R.id.connectionTypeText);
        videoView = findViewById(R.id.videoView);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        
        // Initialize EGL context for video rendering
        rootEglBase = EglBase.create();
        videoView.init(rootEglBase.getEglBaseContext(), null);
        videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        videoView.setEnableHardwareScaler(true);
        
        // Initialize WebRTC and Firebase
        webRTCManager = new WebRTCManager(this);
        signalingClient = new FirebaseSignalingClient();
        
        // Set callback for connection type updates (shows STUN vs TURN)
        webRTCManager.setConnectionTypeCallback(new WebRTCManager.ConnectionTypeCallback() {
            @Override
            public void onConnectionTypeChanged(String type, String description) {
                runOnUiThread(() -> {
                    connectionTypeText.setText(description);
                    
                    // Color coding based on connection type
                    if (type.equals("TURN")) {
                        connectionTypeText.setTextColor(ContextCompat.getColor(ViewerActivity.this, 
                            android.R.color.holo_orange_dark));
                    } else if (type.equals("STUN")) {
                        connectionTypeText.setTextColor(ContextCompat.getColor(ViewerActivity.this, 
                            android.R.color.holo_green_dark));
                    } else if (type.equals("HOST")) {
                        connectionTypeText.setTextColor(ContextCompat.getColor(ViewerActivity.this, 
                            android.R.color.holo_blue_dark));
                    } else if (type.equals("STREAMING")) {
                        connectionTypeText.setTextColor(ContextCompat.getColor(ViewerActivity.this, 
                            android.R.color.holo_green_light));
                    } else if (type.equals("FAILED")) {
                        connectionTypeText.setTextColor(ContextCompat.getColor(ViewerActivity.this, 
                            android.R.color.holo_red_dark));
                    }
                });
            }
        });
        
        // Set callback for video track (to display the stream)
        webRTCManager.setVideoTrackCallback(new WebRTCManager.VideoTrackCallback() {
            @Override
            public void onVideoTrack(VideoTrack videoTrack) {
                runOnUiThread(() -> {
                    videoTrack.addSink(videoView);
                    statusText.setText("📺 Streaming Live");
                });
            }
        });
        
        // Set Firebase signaling callbacks
        signalingClient.setOnOfferReceived(offer -> {
            webRTCManager.setRemoteDescription(offer);
            webRTCManager.createAnswer();
        });
        
        signalingClient.setOnIceCandidateReceived(candidate -> {
            webRTCManager.addIceCandidate(candidate);
        });
        
        connectButton.setOnClickListener(v -> connectToDevice());
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());
        
        statusText.setText("Ready to connect to: " + deviceId);
        connectionTypeText.setText("⏳ Tap CONNECT to start");
    }
    
    private void connectToDevice() {
        signalingClient.connectToBroadcaster(deviceId);
        webRTCManager.startViewing();
        isConnected = true;
        statusText.setText("Connecting to device...");
        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();
    }
    
    private void disconnectFromDevice() {
        if (isConnected) {
            signalingClient.disconnectFromBroadcaster(deviceId);
            webRTCManager.stopViewing();
            isConnected = false;
            statusText.setText("Disconnected");
            connectionTypeText.setText("⏳ Not connected");
            videoView.clearImage();
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (isConnected) {
            disconnectFromDevice();
        }
        if (videoView != null) {
            videoView.release();
        }
        if (rootEglBase != null) {
            rootEglBase.release();
        }
        super.onDestroy();
    }
}
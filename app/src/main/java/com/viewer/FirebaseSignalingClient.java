package com.viewer;

import android.util.Log;
import com.google.firebase.database.*;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class FirebaseSignalingClient {
    private static final String TAG = "FirebaseSignaling";
    private static FirebaseSignalingClient instance;
    private DatabaseReference databaseRef;
    private String currentDeviceId;
    
    private OnOfferReceivedListener offerListener;
    private OnAnswerReceivedListener answerListener;
    private OnIceCandidateReceivedListener iceCandidateListener;
    
    public interface OnOfferReceivedListener {
        void onOfferReceived(SessionDescription offer);
    }
    
    public interface OnAnswerReceivedListener {
        void onAnswerReceived(SessionDescription answer);
    }
    
    public interface OnIceCandidateReceivedListener {
        void onIceCandidateReceived(IceCandidate candidate);
    }
    
    public FirebaseSignalingClient() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseRef = database.getReference();
        instance = this;
    }
    
    public static FirebaseSignalingClient getInstance() {
        return instance;
    }
    
    public void connectToBroadcaster(String deviceId) {
        this.currentDeviceId = deviceId;
        
        Log.d(TAG, "Connecting to broadcaster: " + deviceId);
        
        // FIXED: Match Broadcaster's signaling path
        DatabaseReference signalingRef = databaseRef.child("signaling").child(deviceId);
        
        // Listen for offers from broadcaster
        signalingRef.orderByChild("type").equalTo("offer").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String data = snapshot.child("data").getValue(String.class);
                if (data != null && offerListener != null) {
                    SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, data);
                    Log.d(TAG, "Offer received from broadcaster");
                    offerListener.onOfferReceived(offer);
                    snapshot.getRef().removeValue();
                }
            }
            
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });
        
        // Listen for answers from broadcaster (if broadcaster sends answer)
        signalingRef.orderByChild("type").equalTo("answer").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String data = snapshot.child("data").getValue(String.class);
                if (data != null && answerListener != null) {
                    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, data);
                    Log.d(TAG, "Answer received from broadcaster");
                    answerListener.onAnswerReceived(answer);
                    snapshot.getRef().removeValue();
                }
            }
            
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });
        
        // Listen for ICE candidates from broadcaster
        signalingRef.orderByChild("type").equalTo("ice").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String data = snapshot.child("data").getValue(String.class);
                if (data != null && iceCandidateListener != null) {
                    try {
                        IceCandidate candidate = new IceCandidate("", 0, data);
                        Log.d(TAG, "ICE candidate received from broadcaster");
                        iceCandidateListener.onIceCandidateReceived(candidate);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing ICE candidate: " + e.getMessage());
                    }
                    snapshot.getRef().removeValue();
                }
            }
            
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });
    }
    
    public void disconnectFromBroadcaster(String deviceId) {
        if (deviceId != null) {
            databaseRef.child("signaling").child(deviceId).removeValue();
            Log.d(TAG, "Disconnected from broadcaster: " + deviceId);
        }
    }
    
    public void sendAnswer(SessionDescription answer) {
        if (currentDeviceId != null && answer != null) {
            sendSignal(currentDeviceId, "answer", answer.description);
            Log.d(TAG, "Answer sent to broadcaster");
        }
    }
    
    public void sendIceCandidate(IceCandidate candidate) {
        if (currentDeviceId != null && candidate != null) {
            sendSignal(currentDeviceId, "ice", candidate.sdp);
            Log.d(TAG, "ICE candidate sent to broadcaster");
        }
    }
    
    private void sendSignal(String deviceId, String type, String data) {
        DatabaseReference signalsRef = databaseRef.child("signaling").child(deviceId);
        String key = signalsRef.push().getKey();
        if (key != null) {
            signalsRef.child(key).child("type").setValue(type);
            signalsRef.child(key).child("data").setValue(data);
            signalsRef.child(key).child("timestamp").setValue(System.currentTimeMillis());
        }
    }
    
    public void setOnOfferReceived(OnOfferReceivedListener listener) {
        this.offerListener = listener;
    }
    
    public void setOnAnswerReceived(OnAnswerReceivedListener listener) {
        this.answerListener = listener;
    }
    
    public void setOnIceCandidateReceived(OnIceCandidateReceivedListener listener) {
        this.iceCandidateListener = listener;
    }
}
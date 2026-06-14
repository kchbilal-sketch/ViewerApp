package com.viewer;

import android.content.Intent;  // ✅ ADD THIS MISSING IMPORT
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends AppCompatActivity {
    private ListView deviceList;
    private ArrayAdapter<String> adapter;
    private List<String> deviceNames = new ArrayList<>();
    private List<String> deviceIds = new ArrayList<>();
    private DatabaseReference databaseRef;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        
        deviceList = findViewById(R.id.deviceList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        deviceList.setAdapter(adapter);
        
        databaseRef = FirebaseDatabase.getInstance().getReference();
        
        loadDevices();
        
        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            String selectedDeviceId = deviceIds.get(position);
            Intent intent = new Intent(this, ViewerActivity.class);
            intent.putExtra("deviceId", selectedDeviceId);
            startActivity(intent);
        });
    }
    
    private void loadDevices() {
        // FIXED: Match the broadcaster's Firebase path ("devices" not "broadcasters")
        databaseRef.child("devices").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                deviceNames.clear();
                deviceIds.clear();
                
                for (DataSnapshot device : snapshot.getChildren()) {
                    String deviceId = device.getKey();
                    String status = device.child("status").getValue(String.class);
                    
                    if (status != null && status.equals("online")) {
                        deviceIds.add(deviceId);
                        // Show shortened device ID for display
                        String shortId = deviceId.length() > 8 ? deviceId.substring(0, 8) : deviceId;
                        deviceNames.add("Device: " + shortId + " ● Online");
                    }
                }
                
                adapter.notifyDataSetChanged();
                
                if (deviceNames.isEmpty()) {
                    Toast.makeText(DeviceListActivity.this, "No devices online", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(DeviceListActivity.this, "Error loading devices: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
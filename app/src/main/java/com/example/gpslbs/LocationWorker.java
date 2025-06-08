package com.example.gpslbs;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;



public class LocationWorker extends Worker {
    private static final String TAG = "LocationWorker";
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;

    // Data class for JSON serialization
    public static class LocationData {
        double latitude;
        double longitude;
        String timestamp;
        String method;

        LocationData(double latitude, double longitude, String timestamp, String source) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.method = method;
        }
    }

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {

        try {
            // Get location
            Location location = Tasks.await(fusedLocationClient.getLastLocation());
            if (location != null) {
                // Log location data
                String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
                String provider = location.getProvider();
                String method = provider.equals("gps") ? "GPS" : "LBS";
                Log.d(TAG, "Location acquired: " +
                        "Latitude=" + location.getLatitude() +
                        ", Longitude=" + location.getLongitude() +
                        ", Timestamp=" + timestamp +
                        ", Method=" + method +
                        ",Accuracy=" + location.getAccuracy() + "meters");

                Map<String, Object> data = new HashMap<>();
                data.put("latitude", location.getLatitude());
                data.put("longitude", location.getLongitude());
                data.put("timestamp", timestamp);
                data.put("method", method);
                data.put("accuracy", location.getAccuracy());

                // Prepare JSON data with Gson
                LocationData locationdata = new LocationData(
                        location.getLatitude(),
                        location.getLongitude(),
                        timestamp,
                        method
                );
                Gson gson = new Gson();
                String json = gson.toJson(locationdata);
                Log.d(TAG, "JSON data: " + json);

                db.collection("locations")
                        .add(data)
                        .addOnSuccessListener(documentReference ->{
                        Log.d(TAG, "Location data stored successfuly: " + documentReference.getId());
                        String details = "Latitude: " + location.getLatitude() +
                                ", Longitude: " + location.getLongitude() +
                                ", Timestamp: " + timestamp +
                                ", Method: " + method;
                        Intent broadcastIntent = new Intent("com.example.gpslbs.LOCATION_SENT");
                        broadcastIntent.putExtra("details", details);
                        getApplicationContext().sendBroadcast(broadcastIntent);
            })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to store location data, retrying: " + e.getMessage());
                        });
                return Result.success();
            } else {
                Log.d(TAG, "Location is null, retrying");
                return Result.retry();
            }
            }catch (SecurityException e) {
            Log.e(TAG, "SecurityException:" + e.getMessage());
            return Result.retry();
        }catch (Exception e) {
            Log.e(TAG, "Exception:" + e.getMessage());
            return Result.retry();

        }

    }
}




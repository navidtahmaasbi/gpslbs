package com.example.gpslbs;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class LocationWorker extends Worker {
    private static final String TAG = "LocationWorker";
    private static final String SERVER_URL = "https://your-server.com/api/location";
    private FusedLocationProviderClient fusedLocationClient;

    // Data class for JSON serialization
    public static class LocationData {
        double latitude;
        double longitude;
        String timestamp;
        String source;

        LocationData(double latitude, double longitude, String timestamp, String source) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.source = source;
        }
    }

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
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
                Log.d(TAG, "Location acquired: " +
                        "Latitude=" + location.getLatitude() +
                        ", Longitude=" + location.getLongitude() +
                        ", Timestamp=" + timestamp +
                        ", Source=" + location.getProvider());

                // Prepare JSON data with Gson
                LocationData data = new LocationData(
                        location.getLatitude(),
                        location.getLongitude(),
                        timestamp,
                        location.getProvider()
                );
                Gson gson = new Gson();
                String json = gson.toJson(data);
                Log.d(TAG, "JSON data: " + json);

                // Send to server
                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
                Request request = new Request.Builder().url(SERVER_URL).post(body).build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "Location data sent successfully");
                    return Result.success();
                } else {
                    Log.d(TAG, "Failed to send location data, retrying");
                    return Result.retry();
                }
            } else {
                Log.d(TAG, "Location is null, retrying");
                return Result.retry();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e.getMessage());
            return Result.retry();
        }
    }
}



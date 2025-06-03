package com.example.gpslbs;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.common.api.Response;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;



public class LocationWorker extends Worker {
    private static final String SERVER_URL = "https://example.com/api/location";
    private FusedLocationProviderClient fusedLocationClient;

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters params){
        super(context, params);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @NonNull
    @Override
    public Result doWork(){
        try {
            Location location = Tasks.await(fusedLocationClient.getLastLocation());
            if (location != null) {
                JSONObject json = new JSONObject();
                json.put("latitude", location.getLatitude());
                json.put("longitude", location.getLongitude());
                json.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
                json.put("source", location.getProvider());

                OkHttpClient client = new OkHttpClient();
                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder().url(SERVER_URL).post(body).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    return Result.success();
                } else {
                    return Result.retry();
                }
            }
        } catch (Exception e){
                e.printStackTrace();
                return Result.retry();
            }
return Result.retry();

    }
}

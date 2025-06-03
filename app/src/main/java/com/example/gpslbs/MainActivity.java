package com.example.gpslbs;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private Button toggleButton;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        toggleButton = findViewById(R.id.toggle_button);

        requestLocationPermissions();

        toggleButton.setOnClickListener(v -> {
            if (WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("LocationWork").getValue() != null) {
                WorkManager.getInstance(this).cancelUniqueWork("locationWork");
                statusText.setText("Service: Stopped");
                toggleButton.setText("Start Service");
            } else {
                startLocationWork();
                statusText.setText("Service: Running");
                toggleButton.setText("Stop Service");
            }
        });
    }
    private void requestLocationPermissions(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS-COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);

        }
    }
    private void startLocationWork(){
        PeriodicWorkRequest locationRequest = new PeriodicWorkRequest.Builder(LocationWorker.class 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("locationWork", ExistingPeriodicWorkPolicy.KEEP, locationRequest);

    }
}

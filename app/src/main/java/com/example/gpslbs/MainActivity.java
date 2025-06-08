package com.example.gpslbs;
import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.gpslbs.LocationWorker;
import com.example.gpslbs.R;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private TextView statusText;
    private Button toggleButton;
    private boolean isServiceRunning = false;
    private TextView lastSendText;
    private SharedPreferences prefs;
    private LocationSentReceiver locationSentReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        lastSendText = findViewById(R.id.last_send_text);
        toggleButton = findViewById(R.id.toggle_button);
        prefs = getSharedPreferences("LocationAppPrefs", MODE_PRIVATE);
        locationSentReceiver = new LocationSentReceiver(this);
        registerReceiver(locationSentReceiver, new IntentFilter("com.example.gpslbs.LOCATION_SENT"));

        // Load last send details
        updateLastSendUI();

        // Request battery optimization exemption
        requestBatteryOptimizationExemption();

        // Request permissions
        requestLocationPermissions();


        promptAutoStartPermission();




        // Toggle service
        toggleButton.setOnClickListener(v -> {
            Log.d(TAG, "Toggle button clicked, isServiceRunning: " + isServiceRunning);
            if (isServiceRunning) {
                Log.d(TAG, "Attempting to stop service");
                WorkManager.getInstance(this).cancelUniqueWork("locationWork");
                statusText.setText("Service: Stopped");
                toggleButton.setText("Start Service");
                isServiceRunning = false;
                Intent broadcastIntent = new Intent("com.example.gpslbs.SERVICE_STOPPED");
                sendBroadcast(broadcastIntent);
            } else {
                Log.d(TAG, "Attempting to start service");
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    startLocationWork();
                    statusText.setText("Service: Running");
                    toggleButton.setText("Stop Service");
                    isServiceRunning = true;
                } else {
                    statusText.setText("Service: Stopped - Permission Required");
                    requestLocationPermissions();
                }
            }
        });

    }
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
            }
        }
    }

    private void requestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting location permissions");
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("This app needs location permissions to track your location. Please grant the permissions.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        statusText.setText("Service: Stopped - Permission Denied");
                    })
                    .setCancelable(false)
                    .show();
        } else {
            Log.d(TAG, "Location permissions already granted");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting background location permission");
                new AlertDialog.Builder(this)
                        .setTitle("Background Location Permission Required")
                        .setMessage("This app needs background location access to work in the background. Please grant the permission.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                    LOCATION_PERMISSION_REQUEST_CODE);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            statusText.setText("Service: Stopped - Background Permission Denied");
                        })
                        .setCancelable(false)
                        .show();
            } else {
                Log.d(TAG, "All permissions granted, starting service");
                startLocationWork();
                statusText.setText("Service: Running");
                toggleButton.setText("Stop Service");
                isServiceRunning = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions granted");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Requesting background location permission after foreground permission granted");
                    new AlertDialog.Builder(this)
                            .setTitle("Background Location Permission Required")
                            .setMessage("This app needs background location access to work in the background. Please grant the permission.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                        LOCATION_PERMISSION_REQUEST_CODE);
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                statusText.setText("Service: Stopped - Background Permission Denied");
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    startLocationWork();
                    statusText.setText("Service: Running");
                    toggleButton.setText("Stop Service");
                    isServiceRunning = true;
                }
            } else {
                Log.d(TAG, "Permissions denied");
                statusText.setText("Service: Stopped - Permission Denied");
                toggleButton.setText("Start Service");
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Log.d(TAG, "Permissions permanently denied");
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Denied")
                            .setMessage("Location permissions are required. Please enable them in Settings.")
                            .setPositiveButton("Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .setCancelable(false)
                            .show();
                }
            }
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        unregisterReceiver(locationSentReceiver);
    }

    private void startLocationWork() {
        Log.d(TAG, "Starting location work");
        PeriodicWorkRequest locationRequest = new PeriodicWorkRequest.Builder(LocationWorker.class, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("locationWork", ExistingPeriodicWorkPolicy.KEEP, locationRequest);
        isServiceRunning = true;
    }

    private void updateLastSendUI() {
        String lastSend = prefs.getString("last_send", "No successful send yet");
        lastSendText.setText("Last Successful Send:\n" + lastSend);
    }

    // Called by BroadcastReceiver to update last send
    public void onLocationSent(String details) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_send", details);
        editor.apply();
        runOnUiThread(() -> updateLastSendUI());
    }
    private void promptAutoStartPermission() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Auto-Start")
                .setMessage("To ensure the app runs after device restart, please enable auto-start in settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
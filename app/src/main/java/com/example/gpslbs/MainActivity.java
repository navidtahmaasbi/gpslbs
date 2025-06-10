package com.example.gpslbs;
import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.gpslbs.LocationWorker;
import com.example.gpslbs.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final String WORK_NAME = "locationWork";

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
        OneTimeWorkRequest locationRequest = new OneTimeWorkRequest.Builder(LocationWorker.class)
                .setInputData(new Data.Builder().putBoolean("isRepeating", true).build())
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(this).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, locationRequest);
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
        // Skip if user dismissed prompt
        if (prefs.getBoolean("auto_start_prompt_dismissed", false)) {
            Log.d(TAG, "Auto-start prompt dismissed, skipping");
            return;
        }

        String manufacturer = Build.MANUFACTURER.toLowerCase();
        // OEMs with auto-start restrictions
        Set<String> restrictedOems = new HashSet<>();
        restrictedOems.add("xiaomi");
        restrictedOems.add("huawei");
        restrictedOems.add("honor");
        restrictedOems.add("oppo");
        restrictedOems.add("vivo");
        restrictedOems.add("oneplus");
        restrictedOems.add("realme");
        restrictedOems.add("samsung");

        if (!restrictedOems.contains(manufacturer)) {
            Log.d(TAG, "No auto-start prompt needed for: " + manufacturer);
            return;
        }

        Intent intent = new Intent();
        String message;
        boolean isOemSpecific = true;

        // OEM-specific settings and messages
        if (manufacturer.contains("xiaomi")) {
            intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            message = "Please enable 'Run at startup' in Settings > Apps > Permissions > Other app capabilities. Find '" + getString(R.string.app_name) + "' and toggle it on.";
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            intent.setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            message = "Please enable 'Auto-launch' in Settings > Apps > Battery > App launch > Manage manually for '" + getString(R.string.app_name) + "'.";
        } else if (manufacturer.contains("oppo")) {
            intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            message = "Please enable 'Auto-start' in Settings > App management > Auto-start for '" + getString(R.string.app_name) + "'.";
        } else if (manufacturer.contains("vivo")) {
            intent.setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            message = "Please enable 'Auto-start' in Settings > Apps > Auto-start for '" + getString(R.string.app_name) + "'.";
        } else if (manufacturer.contains("oneplus")) {
            intent.setAction("com.android.settings.ACTION_APPLICATION_DETAILS_SETTINGS");
            intent.setData(Uri.parse("package:" + getPackageName()));
            message = "Please enable 'Allow auto-launch' and 'Allow background activity' in Settings > Battery > App battery management for '" + getString(R.string.app_name) + "'. Also, lock the app in Recent Apps.";
            isOemSpecific = false; // No reliable auto-launch intent
        } else if (manufacturer.contains("realme")) {
            intent.setAction("com.android.settings.ACTION_APPLICATION_DETAILS_SETTINGS");
            intent.setData(Uri.parse("package:" + getPackageName()));
            message = "Please enable 'Auto-start' in Settings > App management > Auto-start for '" + getString(R.string.app_name) + "'. Also, lock the app in Recent Apps.";
            isOemSpecific = false; // No reliable auto-start intent
        } else if (manufacturer.contains("samsung")) {
            intent.setAction("com.android.settings.ACTION_APPLICATION_DETAILS_SETTINGS");
            intent.setData(Uri.parse("package:" + getPackageName()));
            message = "Please add '" + getString(R.string.app_name) + "' to 'Never auto sleeping apps' in Settings > Battery > Background usage limits, and set Battery optimization to 'Don’t optimize'.";
            isOemSpecific = false; // No specific auto-start intent
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            message = "Please enable auto-start or disable battery optimization for '" + getString(R.string.app_name) + "' in Settings > Apps > Permissions.";
            isOemSpecific = false;
        }

        // Verify intent resolves
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (list.size() > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Enable Background Operation")
                    .setMessage(message)
                    .setPositiveButton("Go to Settings", (dialog, which) -> {
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start settings: " + e.getMessage());
                            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            fallback.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(fallback);
                        }
                    })
                    .setNegativeButton("Dismiss", (dialog, which) -> {
                        prefs.edit().putBoolean("auto_start_prompt_dismissed", true).apply();
                    })
                    .setCancelable(false)
                    .show();
        } else {
            // Fallback intent
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            new AlertDialog.Builder(this)
                    .setTitle("Enable Background Operation")
                    .setMessage(message)
                    .setPositiveButton("Go to Settings", (dialog, which) -> startActivity(intent))
                    .setNegativeButton("Dismiss", (dialog, which) -> {
                        prefs.edit().putBoolean("auto_start_prompt_dismissed", true).apply();
                    })
                    .setCancelable(false)
                    .show();
        }
    }






}
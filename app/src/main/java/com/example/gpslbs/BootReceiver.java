package com.example.gpslbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent){
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            PeriodicWorkRequest locationRequest = new PeriodicWorkRequest.Builder(LocationWorker.class, 30, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("locationWork", ExistingPeriodicWorkPolicy.KEEP, locationRequest);

        }
    }
}

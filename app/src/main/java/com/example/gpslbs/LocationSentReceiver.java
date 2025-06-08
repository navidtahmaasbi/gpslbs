package com.example.gpslbs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LocationSentReceiver extends BroadcastReceiver {
    private final MainActivity activity;

    public LocationSentReceiver(MainActivity activity){
        this.activity = activity;

    }
    @Override
    public void onReceive(Context context, Intent intent){
        if ("com.example.gpslbs.LOCATION_SENT".equals(intent.getAction())){
            String details = intent.getStringExtra("details");
            if (details != null){
                activity.onLocationSent(details);
            }
        }
    }
}

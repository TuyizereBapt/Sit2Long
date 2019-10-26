package com.cmu.bapt.sit2long;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Switch;

import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

public class ActivityUpdatesReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                UpdateUI activity = (UpdateUI) context;
                activity.updateUI(getActivityName(event.getActivityType()));
            }
        }
    }

    String getActivityName(int code) {
        switch (code) {
            case DetectedActivity.WALKING:
                return "Walking";
            case DetectedActivity.STILL:
                return "STILL";
            default:
                return "UNKNOWN";
        }
    }
}

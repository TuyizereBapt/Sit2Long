package com.cmu.bapt.sit2long;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private TextView txtWarning;
    private TextView txtElapsedMinutes;
    private EditText edtSittingMaxMinutes;
    private Button btnStart;
    private Button btnStop;
    private ImageButton btnEdit;
    private ImageView activityImage;
    private final String TAG = "ACTIVITY_API";
    private Context context;
    private ActivityRecognitionReceiver transitionReceiver;
    private PendingIntent pendingIntent;
    private boolean editing = false;
    private Timer timer;
    private long minutes = 0;
    private int seconds = 0;
    private int maxSittingMinutes = 0;
    private long timeStartedMoving = 0;
    private long recentMinutesSpentStill = 0;
    private int currentActivity = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_main);

        txtWarning = (TextView) findViewById(R.id.txt_warning);
        edtSittingMaxMinutes = (EditText) findViewById(R.id.edt_sitting_max_min);
        txtElapsedMinutes = (TextView) findViewById(R.id.txt_elapsed_min);
        btnStart = (Button) findViewById(R.id.btn_start);
        btnStop = (Button) findViewById(R.id.btn_stop);
        btnEdit = (ImageButton) findViewById(R.id.btn_edit);
        activityImage = (ImageView) findViewById(R.id.activity_image);

        transitionReceiver = new ActivityRecognitionReceiver();
        registerReceiver(transitionReceiver, new IntentFilter(Constants.INTENT_ACTION));

        Intent intent = new Intent(Constants.INTENT_ACTION);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupActivityTransitions();
                minutes = 0;
                seconds = 0;
                startTimer();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (timer != null)
                    timer.cancel();
                removeActivityTransitions();
            }
        });
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editing) {
                    try {
                        maxSittingMinutes = Integer.parseInt(String.valueOf(edtSittingMaxMinutes.getEditableText()));
                    } catch (NumberFormatException e) {
                        return;
                    }
                    edtSittingMaxMinutes.setEnabled(false);
                    edtSittingMaxMinutes.setInputType(InputType.TYPE_NULL);
                    edtSittingMaxMinutes.setFocusable(false);
                    btnEdit.setImageResource(R.drawable.ic_edit);
                } else {
                    edtSittingMaxMinutes.setEnabled(true);
                    edtSittingMaxMinutes.setInputType(InputType.TYPE_CLASS_NUMBER);
                    edtSittingMaxMinutes.setFocusable(true);
                    btnEdit.setImageResource(R.drawable.ic_check);
                }
                editing = !editing;
            }
        });
    }

    public void startTimer() {
        if (timer != null)
            timer.cancel();

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        seconds += 1;
                        if (seconds == 60) {
                            minutes += 1;
                            seconds = 0;
                        }
                        if (minutes >= maxSittingMinutes && currentActivity == DetectedActivity.STILL) {
                            //Show notification
                            AppNotificationsManager.showNotification(
                                    context,
                                    getString(R.string.notification_title),
                                    String.format(getString(R.string.over_sitting_warning_notification), minutes)
                            );
                            txtWarning.setText(getString(R.string.over_sitting_warning));
                        }

                        txtElapsedMinutes.setText(String.format(getString(R.string.activity_elapsed_minutes), minutes, seconds));
                    }
                });
            }
        }, 0, 1000);
    }

   /* @Override
    protected void onResume() {
        super.onResume();
        setupActivityTransitions();
    }

    @Override
    protected void onPause() {
        removeActivityTransitions();
        super.onPause();
    }*/

    @Override
    protected void onStop() {
        if (transitionReceiver != null) {
            unregisterReceiver(transitionReceiver);
            transitionReceiver = null;
        }
        super.onStop();
    }

    public void setupActivityTransitions() {
        ActivityTransitionRequest request = getActivityTransitionRequest();
        Task<Void> task = ActivityRecognition.getClient(this).requestActivityTransitionUpdates(request, pendingIntent);
        ApiRegistrationListener listener = new ApiRegistrationListener();
        task.addOnSuccessListener(listener);
        task.addOnFailureListener(listener);

        activityImage.setImageResource(R.drawable.ic_error);
        txtWarning.setText(getString(R.string.current_activity));
    }

    public void removeActivityTransitions() {
        ActivityRecognition.getClient(this).removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG, "Transitions successfully unregistered.");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Transitions could not be unregistered: " + e);
                    }
                });
    }

    public ActivityTransitionRequest getActivityTransitionRequest() {
        List<ActivityTransition> transitions = new ArrayList<>();

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build());

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.STILL)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.STILL)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build());
        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build());

        return new ActivityTransitionRequest(transitions);
    }

    class ApiRegistrationListener implements OnSuccessListener<Void>, OnFailureListener {
        @Override
        public void onSuccess(Void aVoid) {
            Log.d(TAG, "onSuccess: API successfully registered!");
        }

        @Override
        public void onFailure(@NonNull Exception e) {
            Log.d(TAG, "OnFailure: API could not be registered!");
            e.printStackTrace();
        }
    }

    public class ActivityRecognitionReceiver extends BroadcastReceiver {
        public ActivityRecognitionReceiver() {
            timeStartedMoving = System.currentTimeMillis();
        }

        @Override
        public void onReceive(Context ctx, Intent intent) {
            Log.d(TAG, "Broadcast running");
            if (TextUtils.equals(Constants.INTENT_ACTION, intent.getAction())) {
                if (ActivityTransitionResult.hasResult(intent)) {
                    ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);

                    for (ActivityTransitionEvent event : result.getTransitionEvents()) {

                        String activity = toActivityString(event.getActivityType());
                        String transitionType = toTransitionType(event.getTransitionType());

                        String output = "Transition: " + activity + " (" + transitionType + ")" + "   " + new SimpleDateFormat("HH:mm:ss", Locale.US)
                                .format(new Date());
                        Toast.makeText(ctx, output, Toast.LENGTH_LONG).show();

                        Log.d(TAG, output);
                        //Update UI
                        handleActivity(event.getActivityType(), event.getTransitionType());
                    }
                }
            }
        }

        public void handleActivity(int activity, int transitionType) {
            if (activity == DetectedActivity.STILL && transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                long differenceMinutes = (System.currentTimeMillis() - timeStartedMoving) / (60 * 1000);
                if (differenceMinutes < 5) {
                    minutes = recentMinutesSpentStill + differenceMinutes;
                } else {
                    minutes = 0;
                }
                seconds = 0;
            } else {
                if (currentActivity == DetectedActivity.STILL) {
                    recentMinutesSpentStill = minutes;
                    timeStartedMoving = System.currentTimeMillis();
                }
                minutes = 0;
                seconds = 0;
            }

            currentActivity = activity;
            updateUI(activity);
            startTimer();
        }

        private void updateUI(int activity) {
            String activityName = "Unknown";
            int imageId = R.drawable.ic_error;
            switch (activity) {
                case DetectedActivity.STILL:
                    activityName = "STILL(SITTING,STANDING)";
                    imageId = R.drawable.ic_sitting;
                    break;
                case DetectedActivity.WALKING:
                    activityName = "WALKING";
                    imageId = R.drawable.ic_walking;
                    break;
                case DetectedActivity.IN_VEHICLE:
                    activityName = "IN VEHICLE";
                    imageId = R.drawable.ic_car;
                    break;
                case DetectedActivity.RUNNING:
                    activityName = "RUNNING";
                    imageId = R.drawable.ic_running;
                    break;
            }
            activityImage.setImageResource(imageId);
            txtWarning.setText(String.format("Current Activity: %s", activityName));
        }

        private String toActivityString(int activity) {
            switch (activity) {
                case DetectedActivity.STILL:
                    return "STILL";
                case DetectedActivity.WALKING:
                    return "WALKING";
                case DetectedActivity.IN_VEHICLE:
                    return "IN_VEHICLE";
                case DetectedActivity.RUNNING:
                    return "RUNNING";
                default:
                    return "UNKNOWN";
            }
        }

        private String toTransitionType(int transitionType) {
            switch (transitionType) {
                case ActivityTransition.ACTIVITY_TRANSITION_ENTER:
                    return "ENTER";
                case ActivityTransition.ACTIVITY_TRANSITION_EXIT:
                    return "EXIT";
                default:
                    return "UNKNOWN";
            }
        }
    }
}

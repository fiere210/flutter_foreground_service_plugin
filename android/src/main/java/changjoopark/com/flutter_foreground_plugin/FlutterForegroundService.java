package changjoopark.com.flutter_foreground_plugin;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;

import androidx.core.app.NotificationCompat;


public class FlutterForegroundService extends Service {
    private static String TAG = "FlutterForegroundService";
    public static int ONGOING_NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "CHANNEL_ID";
    public static final String ACTION_STOP_SERVICE = "STOP";
    public static final String ACTION_CLICKED = "CLICKED";

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private boolean userStopForegroundService = false;
    private NotificationCompat.Builder builder;
    private ResultReceiver stoppedReceiver;
    private ResultReceiver openedReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(TAG, "onStartCommand: Intent is null");
            return START_NOT_STICKY;
        }
        if (intent.getAction() == null) {
            Log.d(TAG, "onStartCommand: Intent action is null");
            return START_NOT_STICKY;
        }
        final String action = intent.getAction();
        Log.d(TAG, String.format("onStartCommand: %s", action));
        Bundle bundle = intent.getExtras();
        switch (action) {
            case FlutterForegroundPlugin.START_FOREGROUND_ACTION:
                Intent openAction = new Intent(this, FlutterForegroundService.class);
                openAction.setAction(ACTION_CLICKED);
                PendingIntent pOpenAction = PendingIntent
                        .getService(this, 0, openAction, PendingIntent.FLAG_CANCEL_CURRENT);
                final ResultReceiver stopResultReceiver = intent.getParcelableExtra(FlutterForegroundPlugin.STOP_LISTENER);
                final ResultReceiver openResultReceiver = intent.getParcelableExtra(FlutterForegroundPlugin.OPEN_LISTENER);
                if (stopResultReceiver != null) {
                    stoppedReceiver = stopResultReceiver;
                }
                if (openResultReceiver != null) {
                    openedReceiver = openResultReceiver;
                }


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                            "flutter_foreground_service_channel",
                            NotificationManager.IMPORTANCE_DEFAULT);

                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                            .createNotificationChannel(channel);
                }
                builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(getNotificationIcon("ic_stat"))
                        .setLargeIcon(
                                BitmapFactory.decodeResource(getApplicationContext().getResources(),
                                        getNotificationIcon("ic_timer"))
                        )
                        .setColor(bundle.getInt("color"))
                        .setContentTitle(bundle.getString("title"))
                        .setContentText(bundle.getString("content"))
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setContentIntent(pOpenAction)
                        .setUsesChronometer(bundle.getBoolean("chronometer"))
                        .setOnlyAlertOnce(true)
                        .setOngoing(true);

                if (bundle.getBoolean("stop_action")) {
                    Intent stopSelf = new Intent(this, FlutterForegroundService.class);
                    stopSelf.setAction(ACTION_STOP_SERVICE);

                    PendingIntent pStopSelf = PendingIntent
                            .getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT);
                    builder.addAction(getNotificationIcon(bundle.getString("stop_icon")),
                            bundle.getString("stop_text"),
                            pStopSelf);
                }

                if (bundle.getString("subtext") != null && !bundle.getString("subtext").isEmpty()) {
                    builder.setSubText(bundle.getString("subtext"));
                }

                startForeground(ONGOING_NOTIFICATION_ID, builder.build());
                break;
            case FlutterForegroundPlugin.STOP_FOREGROUND_ACTION:
                stopFlutterForegroundService();
                break;
            case ACTION_STOP_SERVICE:
                if (stoppedReceiver != null) {
                    stoppedReceiver.send(Activity.RESULT_OK, new Bundle());
                }
                stopFlutterForegroundService();
                break;
            case ACTION_CLICKED:
                if (openedReceiver != null) {
                    openedReceiver.send(Activity.RESULT_OK, new Bundle());
                }
                break;
            case FlutterForegroundPlugin.UPDATE_FOREGROUND_ACTION:
                updateForegroundContent(bundle.getString("content"));
                break;
            default:
                break;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (!userStopForegroundService) {
            Log.d(TAG, "User close app, kill current process to avoid memory leak in other plugin.");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateForegroundContent(String content) {
        if (builder != null) {
            builder.setContentText(content);
            Notification notification = builder.build();
            getNotificationManager().notify(ONGOING_NOTIFICATION_ID, notification);
        }
    }

    private int getNotificationIcon(String iconName) {
        return getApplicationContext().getResources().getIdentifier(iconName, "drawable", getApplicationContext().getPackageName());
    }

    private void stopFlutterForegroundService() {
        userStopForegroundService = true;
        stopForeground(true);
        stopSelf();
    }
}

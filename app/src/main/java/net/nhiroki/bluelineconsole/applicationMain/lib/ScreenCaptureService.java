package net.nhiroki.bluelineconsole.applicationMain.lib;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.nhiroki.bluelineconsole.R;

public class ScreenCaptureService extends Service {
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 9923;
    private static boolean sIsRunning = false;

    public interface ServiceReadyListener {
        void onReady();
    }

    private static ServiceReadyListener sListener = null;

    public static void setServiceReadyListener(ServiceReadyListener listener) {
        sListener = listener;
        if (sIsRunning && sListener != null) {
            sListener.onReady();
            sListener = null;
        }
    }

    public static boolean isRunning() {
        return sIsRunning;
    }

    public static void stopService(Context context) {
        try {
            Intent intent = new Intent(context, ScreenCaptureService.class);
            context.stopService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            sIsRunning = true;
        } catch (Exception e) {
            // Log or fallback
        }

        if (sListener != null) {
            ServiceReadyListener listener = sListener;
            sListener = null;
            listener.onReady();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        sIsRunning = false;
        sListener = null;
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Screen Capture Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Required for background screen understanding");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Analyzing Screen")
                .setContentText("Reading screen context for Gemini...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        return builder.build();
    }
}

package com.clipstack.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

/**
 * خدمة أمامية — تبقى نشطة لمنع النظام من إيقاف التطبيق.
 * قراءة الحافظة تتم في AppTrackerService (AccessibilityService).
 */
public class ClipboardService extends Service {

    public static final String ACTION_REFRESH  = "com.clipstack.app.REFRESH";
    public static final String ACTION_SAVE_NOW = "com.clipstack.app.SAVE_NOW";

    private static final String CH_SERVICE = "clip_service";
    private static final String CH_ALERT   = "clip_alert";
    private static final int    ID_SERVICE  = 1;
    private static final int    ID_ALERT    = 2;

    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        createChannels();
        startForeground(ID_SERVICE, buildServiceNotif());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SAVE_NOW.equals(intent.getAction())) {
            cancelAlert();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() { super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }

    private Notification buildServiceNotif() {
        boolean show = prefs.getBoolean("pref_notification", true);
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CH_SERVICE)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("Clip Stack")
                .setContentText("مراقبة الحافظة نشطة")
                .setContentIntent(pi)
                .setPriority(show
                        ? NotificationCompat.PRIORITY_LOW
                        : NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    void cancelAlert() {
        getSystemService(NotificationManager.class).cancel(ID_ALERT);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel svc = new NotificationChannel(
                CH_SERVICE, "حالة الخدمة", NotificationManager.IMPORTANCE_MIN);
        svc.setSound(null, null);
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);

        NotificationChannel alert = new NotificationChannel(
                CH_ALERT, "تنبيه نسخ", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(alert);
    }

    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, ClipboardService.class));
    }
    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ClipboardService.class));
    }
}

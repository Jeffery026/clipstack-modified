package com.clipstack.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

public class ClipboardService extends Service {
    public static final String ACTION_REFRESH  = "com.clipstack.app.REFRESH";
    public static final String ACTION_SAVE_NOW = "com.clipstack.app.SAVE_NOW";
    private static final String CH = "clip_ch";
    private static final int    NOTIF_ID  = 1;
    private static final int    ALERT_ID  = 2;

    private android.content.ClipboardManager cm;
    private ClipDatabase       db;
    private SharedPreferences  prefs;

    private final android.content.ClipboardManager.OnPrimaryClipChangedListener listener = () -> {
        try {
            if (cm == null || !cm.hasPrimaryClip()) return;
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();

            if (cs == null) {
                // Android 10+ لا يسمح بقراءة الحافظة من الخلفية
                // نعرض إشعاراً يفتح MainActivity لالتقاطها
                showCaptureNotification();
                return;
            }

            String text = cs.toString().trim();
            if (text.isEmpty()) return;

            String lastClip = prefs.getString(AppTrackerService.KEY_LAST_CLIP, "");
            if (text.equals(lastClip)) return;

            String pkg = prefs.getString(AppTrackerService.KEY_PKG, "");
            if (!pkg.isEmpty() && db.isBlacklisted(pkg)) return;

            prefs.edit().putString(AppTrackerService.KEY_LAST_CLIP, text).apply();
            db.insert(text, pkg);
            String days = prefs.getString("pref_days", "-1");
            db.deleteOlderThan(Integer.parseInt(days));
            sendBroadcast(new Intent(ACTION_REFRESH));

        } catch (SecurityException e) {
            showCaptureNotification();
        } catch (Exception ignored) {}
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        db    = ClipDatabase.get(this);
        cm    = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.addPrimaryClipChangedListener(listener);
        createChannel();
        boolean showNotif = prefs.getBoolean("pref_notification", true);
        if (showNotif) startForeground(NOTIF_ID, buildPersistentNotif());
        else           startForeground(NOTIF_ID, buildSilentNotif());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SAVE_NOW.equals(intent.getAction())) {
            // MainActivity فتحت بسببنا — ستقرأ الحافظة هناك
            cancelCaptureNotification();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (cm != null) cm.removePrimaryClipChangedListener(listener);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }

    // ── الإشعار الدائم ──
    private Notification buildPersistentNotif() {
        return baseNotif()
                .setContentTitle("Clip Stack")
                .setContentText(getString(R.string.service_notification))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
    private Notification buildSilentNotif() {
        return baseNotif()
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    // ── إشعار "اضغط لحفظ" ──
    private void showCaptureNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .setAction(ACTION_SAVE_NOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, ALERT_ID, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = baseNotif()
                .setContentTitle("📋 تم النسخ")
                .setContentText("اضغط لحفظه في Clip Stack")
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ALERT_ID, n);
    }

    private void cancelCaptureNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_ID);
    }

    private NotificationCompat.Builder baseNotif() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pi)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET);
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CH,"Clip Stack",
                NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, ClipboardService.class));
    }
    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ClipboardService.class));
    }
}

package com.clipstack.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

public class ClipboardService extends Service {
    public static final String ACTION_REFRESH  = "com.clipstack.app.REFRESH";
    public static final String ACTION_SAVE_NOW = "com.clipstack.app.SAVE_NOW";
    private static final String CH_SERVICE = "clip_service";
    private static final String CH_ALERT   = "clip_alert";
    private static final int ID_SERVICE = 1;
    private static final int ID_ALERT   = 2;

    private android.content.ClipboardManager cm;
    private SharedPreferences prefs;
    private ClipDatabase      db;

    private final android.content.ClipboardManager.OnPrimaryClipChangedListener listener = () -> {
        try {
            if (cm == null || !cm.hasPrimaryClip()) return;
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;

            CharSequence cs = clip.getItemAt(0).getText();

            if (cs == null) {
                // Android 12+ منع القراءة من الخلفية — أرسل إشعاراً فورياً
                showCaptureAlert();
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
            // Android 12+ رفض الوصول — أرسل إشعاراً
            showCaptureAlert();
        } catch (Exception ignored) {}
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        db    = ClipDatabase.get(this);
        cm    = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.addPrimaryClipChangedListener(listener);
        createChannels();
        startForeground(ID_SERVICE, buildServiceNotif());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SAVE_NOW.equals(intent.getAction())) {
            cancelAlert();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (cm != null) cm.removePrimaryClipChangedListener(listener);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ── إشعار دائم للخدمة ──
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
                .setPriority(show ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    // ── إشعار "اضغط لحفظ" ──
    private void showCaptureAlert() {
        Intent open = new Intent(this, MainActivity.class)
                .setAction(ACTION_SAVE_NOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, ID_ALERT, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("📋 تم النسخ")
                .setContentText("اضغط لحفظه في Clip Stack")
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .setAutoCancel(true)
                .setTimeoutAfter(30000) // يختفي بعد 30 ثانية
                .build();

        getSystemService(NotificationManager.class).notify(ID_ALERT, n);
    }

    private void cancelAlert() {
        getSystemService(NotificationManager.class).cancel(ID_ALERT);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);

        // قناة الخدمة الدائمة (صامتة)
        NotificationChannel svc = new NotificationChannel(
                CH_SERVICE, "حالة الخدمة", NotificationManager.IMPORTANCE_MIN);
        svc.setSound(null, null);
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);

        // قناة تنبيه النسخ (مع صوت)
        NotificationChannel alert = new NotificationChannel(
                CH_ALERT, "تنبيه نسخ جديد", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(alert);
    }

    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, ClipboardService.class));
    }
    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ClipboardService.class));
    }
}

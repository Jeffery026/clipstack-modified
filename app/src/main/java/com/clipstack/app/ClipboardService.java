package com.clipstack.app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

public class ClipboardService extends Service {
    public static final String ACTION_REFRESH = "com.clipstack.app.REFRESH";
    private static final String CH = "clip_ch";

    private ClipboardManager   cm;
    private ClipDatabase       db;
    private SharedPreferences  prefs;

    private final ClipboardManager.OnPrimaryClipChangedListener listener = () -> {
        try {
            if (!cm.hasPrimaryClip()) return;
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
            if (cs==null) return;
            String text = cs.toString().trim();
            if (text.isEmpty()) return;
            String pkg = prefs.getString(AppTrackerService.KEY_PKG,"");
            if (!pkg.isEmpty() && db.isBlacklisted(pkg)) return;
            db.insert(text, pkg);
            // cleanup
            String days = prefs.getString("pref_days","-1");
            db.deleteOlderThan(Integer.parseInt(days));
            sendBroadcast(new Intent(ACTION_REFRESH));
        } catch (Exception ignored) {}
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        db    = ClipDatabase.get(this);
        cm    = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.addPrimaryClipChangedListener(listener);
        createChannel();
        startForeground(1, buildNotif());
    }

    @Override public int onStartCommand(Intent i, int f, int s) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (cm!=null) cm.removePrimaryClipChangedListener(listener);
        super.onDestroy();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CH,"Clip Stack",
                NotificationManager.IMPORTANCE_MIN);
        ch.setSound(null,null);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotif() {
        PendingIntent pi = PendingIntent.getActivity(this,0,
                new Intent(this,MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this,CH)
                .setContentTitle("Clip Stack")
                .setContentText(getString(R.string.service_notification))
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, ClipboardService.class));
    }
    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, ClipboardService.class));
    }
}

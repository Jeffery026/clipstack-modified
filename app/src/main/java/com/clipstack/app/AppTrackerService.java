package com.clipstack.app;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

/**
 * خدمة إمكانية الوصول — تتتبع التطبيق الأمامي وتقرأ الحافظة
 * (على Android 10+ لا يمكن قراءة الحافظة من الخلفية العادية،
 *  لكن خدمة إمكانية الوصول تملك صلاحيات أعلى)
 */
public class AppTrackerService extends AccessibilityService {
    public static final String KEY_PKG = "foreground_pkg";
    public static final String KEY_LAST_CLIP = "last_captured_clip";

    private SharedPreferences prefs;
    private ClipDatabase db;
    private android.content.ClipboardManager cm;

    @Override protected void onServiceConnected() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        db    = ClipDatabase.get(this);
        cm    = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        super.onServiceConnected();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String pkgStr = pkg.toString();

        // تجاهل تطبيقنا
        if (pkgStr.contains("com.clipstack.app")) return;

        // حفظ التطبيق الحالي
        prefs.edit().putString(KEY_PKG, pkgStr).apply();

        // تجاهل التطبيقات المحظورة
        if (db.isBlacklisted(pkgStr)) return;

        // قراءة الحافظة هنا — الخدمة تملك صلاحية أعلى
        try {
            if (cm == null || !cm.hasPrimaryClip()) return;
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence cs = clip.getItemAt(0).getText();
            if (cs == null) return;
            String text = cs.toString().trim();
            if (text.isEmpty()) return;

            // تجنب التكرار
            String lastClip = prefs.getString(KEY_LAST_CLIP, "");
            if (text.equals(lastClip)) return;
            prefs.edit().putString(KEY_LAST_CLIP, text).apply();

            // حفظ في قاعدة البيانات
            db.insert(text, pkgStr);

            // تنظيف حسب إعداد المدة
            String days = prefs.getString("pref_days", "-1");
            db.deleteOlderThan(Integer.parseInt(days));

            // إشعار MainActivity بالتحديث
            sendBroadcast(new Intent(ClipboardService.ACTION_REFRESH));

        } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() {}
}

package com.clipstack.app;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.preference.PreferenceManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

public class AppTrackerService extends AccessibilityService {
    public static final String KEY_PKG       = "foreground_pkg";
    public static final String KEY_LAST_CLIP = "last_captured_clip";

    private SharedPreferences prefs;
    private ClipDatabase      db;
    private android.content.ClipboardManager cm;
    private Set<String>       keyboardPackages = new HashSet<>();

    @Override protected void onServiceConnected() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        db    = ClipDatabase.get(this);
        cm    = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        loadKeyboardPackages();
        super.onServiceConnected();
    }

    private void loadKeyboardPackages() {
        keyboardPackages.clear();
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                keyboardPackages.add(info.getPackageName());
            }
        } catch (Exception ignored) {}
        // أضف ألفاظ شائعة للوحات المفاتيح
        keyboardPackages.add("com.google.android.inputmethod.latin");
        keyboardPackages.add("com.samsung.android.honeyboard");
        keyboardPackages.add("com.swiftkey");
        keyboardPackages.add("com.touchtype.swiftkey");
        keyboardPackages.add("com.nuance.swype");
        keyboardPackages.add("com.microsoft.swiftkey");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String pkgStr = pkg.toString();

        // تجاهل تطبيقنا
        if (pkgStr.contains("com.clipstack.app")) return;

        // تجاهل لوحة المفاتيح — نحتفظ بالتطبيق الأخير الحقيقي
        if (isKeyboard(pkgStr)) {
            // عند ظهور لوحة المفاتيح، نحاول قراءة الحافظة
            // لأن المستخدم ربما نسخ للتو
            tryReadClipboard(prefs.getString(KEY_PKG, ""));
            return;
        }

        // تحديث التطبيق الحالي (ليس لوحة مفاتيح)
        prefs.edit().putString(KEY_PKG, pkgStr).apply();

        // محاولة قراءة الحافظة عند تغيير التطبيق
        if (!db.isBlacklisted(pkgStr)) {
            tryReadClipboard(pkgStr);
        }
    }

    private boolean isKeyboard(String pkg) {
        if (keyboardPackages.contains(pkg)) return true;
        // أنماط شائعة لأسماء تطبيقات لوحة المفاتيح
        return pkg.contains("keyboard") || pkg.contains("inputmethod") ||
               pkg.contains("ime.") || pkg.contains(".ime") ||
               pkg.contains("honeyboard") || pkg.contains("swiftkey");
    }

    private void tryReadClipboard(String sourcePkg) {
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
            if (sourcePkg != null && !sourcePkg.isEmpty() && db.isBlacklisted(sourcePkg)) return;

            prefs.edit().putString(KEY_LAST_CLIP, text).apply();
            db.insert(text, sourcePkg != null ? sourcePkg : "");

            String days = prefs.getString("pref_days", "-1");
            db.deleteOlderThan(Integer.parseInt(days));

            sendBroadcast(new Intent(ClipboardService.ACTION_REFRESH));
        } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() {}
}

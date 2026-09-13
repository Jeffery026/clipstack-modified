package com.clipstack.app;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.preference.PreferenceManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

/**
 * خدمة إمكانية الوصول — تتولى المهام التالية:
 * 1. تتبّع التطبيق الأمامي الحقيقي (تصفية الكيبورد وواجهات النظام)
 * 2. تسجيل مستمع الحافظة من سياقها المرتفع (Accessibility context)
 *    لأن Android 16 يمنع الخدمات العادية من قراءة الحافظة في الخلفية
 */
public class AppTrackerService extends AccessibilityService {

    public static final String KEY_PKG       = "foreground_pkg";
    public static final String KEY_LAST_CLIP = "last_captured_clip";

    private SharedPreferences prefs;
    private ClipDatabase      db;
    private ClipboardManager  cm;
    private Set<String>       ignoredPkgs = new HashSet<>();

    // آخر تطبيق "حقيقي" قبل ظهور الكيبورد أو قوائم النظام
    private String lastRealPkg = "";

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = () -> {
        // نقرأ الحافظة هنا — نحن داخل سياق AccessibilityService
        try {
            if (cm == null || !cm.hasPrimaryClip()) return;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence cs = clip.getItemAt(0).getText();
            if (cs == null) return;

            String text = cs.toString().trim();
            if (text.isEmpty()) return;

            // تجنب التكرار
            String lastClip = prefs.getString(KEY_LAST_CLIP, "");
            if (text.equals(lastClip)) return;

            // نستخدم آخر تطبيق حقيقي (ليس كيبورد ولا نظام)
            String sourcePkg = lastRealPkg;
            if (!sourcePkg.isEmpty() && db.isBlacklisted(sourcePkg)) return;

            prefs.edit()
                .putString(KEY_LAST_CLIP, text)
                .putString(KEY_PKG, sourcePkg)
                .apply();

            db.insert(text, sourcePkg);

            // تنظيف حسب إعداد المدة
            try {
                int days = Integer.parseInt(prefs.getString("pref_days", "-1"));
                db.deleteOlderThan(days);
            } catch (Exception ignored) {}

            sendBroadcast(new Intent(ClipboardService.ACTION_REFRESH));

        } catch (Exception ignored) {}
    };

    @Override
    protected void onServiceConnected() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        db    = ClipDatabase.get(this);
        cm    = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        // بناء قائمة الحزم المتجاهَلة
        buildIgnoredPackages();

        // تسجيل مستمع الحافظة من سياق AccessibilityService
        if (cm != null) {
            cm.addPrimaryClipChangedListener(clipListener);
        }

        super.onServiceConnected();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        String pkgStr = pkg.toString();

        // تجاهل تطبيقنا
        if (pkgStr.contains("com.clipstack.app")) return;

        // إذا كان تطبيقاً حقيقياً — حدّث lastRealPkg
        if (!isIgnored(pkgStr)) {
            lastRealPkg = pkgStr;
            prefs.edit().putString(KEY_PKG, pkgStr).apply();
        }
        // إذا كان كيبورد أو نظام — احتفظ بـ lastRealPkg كما هو
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (cm != null) cm.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    // ── قائمة الحزم المتجاهَلة ──
    private void buildIgnoredPackages() {
        ignoredPkgs.clear();

        // لوحات المفاتيح المثبتة
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                ignoredPkgs.add(info.getPackageName());
            }
        } catch (Exception ignored) {}

        // حزم النظام الشائعة
        ignoredPkgs.addAll(Arrays.asList(
            "com.android.systemui",
            "com.android.documentsui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.providers.media",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
            "com.microsoft.swiftkey",
            "com.nuance.swype",
            "com.swiftkey.swiftkeyapp"
        ));
    }

    private boolean isIgnored(String pkg) {
        if (ignoredPkgs.contains(pkg)) return true;
        return pkg.contains("keyboard")     ||
               pkg.contains("inputmethod")  ||
               pkg.contains(".ime")         ||
               pkg.contains("launcher")     ||
               pkg.contains("systemui")     ||
               pkg.contains("documentsui")  ||
               pkg.contains("honeyboard")   ||
               pkg.contains("swiftkey");
    }
}

package com.clipstack.app;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;

public class AppTrackerService extends AccessibilityService {
    public static final String KEY_PKG = "foreground_pkg";

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = e.getPackageName();
            if (pkg!=null && !pkg.toString().contains("com.clipstack.app")) {
                PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putString(KEY_PKG, pkg.toString()).apply();
            }
        }
    }
    @Override public void onInterrupt() {}
}

package com.clipstack.app;

import android.content.Context;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    public static final String PREF_THEME   = "pref_theme";
    public static final String THEME_LIGHT  = "light";
    public static final String THEME_DARK   = "dark";
    public static final String THEME_SYSTEM = "system";

    public static void apply(Context ctx) {
        String t = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(PREF_THEME, THEME_SYSTEM);
        switch (t) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static void set(Context ctx, String theme) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit().putString(PREF_THEME, theme).apply();
        apply(ctx);
    }
}

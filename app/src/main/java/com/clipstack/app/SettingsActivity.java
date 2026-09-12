package com.clipstack.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.*;

public class SettingsActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_settings);
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar()!=null) {
            getSupportActionBar().setTitle(getString(R.string.settings_title));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle s, String r) {
            setPreferencesFromResource(R.xml.preferences, r);

            // مدة الحفظ — تعمل مباشرة عبر SharedPreferences
            ListPreference daysPref = findPreference("pref_days");
            if (daysPref != null) {
                daysPref.setOnPreferenceChangeListener((p, v) -> {
                    int d = Integer.parseInt((String) v);
                    if (d > 0) ClipDatabase.get(requireContext()).deleteOlderThan(d);
                    return true;
                });
            }

            // الإشعار الدائم
            SwitchPreferenceCompat notifPref = findPreference("pref_notification");
            if (notifPref != null) {
                notifPref.setOnPreferenceChangeListener((p, v) -> {
                    boolean on = (Boolean) v;
                    if (on) ClipboardService.start(requireContext());
                    else    ClipboardService.stop(requireContext());
                    return true;
                });
            }

            // الزر العائم — يحتاج صلاحية SYSTEM_ALERT_WINDOW
            SwitchPreferenceCompat floatPref = findPreference("pref_float_button");
            if (floatPref != null) {
                floatPref.setOnPreferenceChangeListener((p, v) -> {
                    boolean on = (Boolean) v;
                    if (on && !Settings.canDrawOverlays(requireContext())) {
                        Toast.makeText(requireContext(),
                                "يرجى السماح للتطبيق بالظهور فوق التطبيقات الأخرى",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:com.clipstack.app")));
                        return false;
                    }
                    return true;
                });
            }

            // إمكانية الوصول — فتح الإعدادات
            Preference accessPref = findPreference("pref_accessibility");
            if (accessPref != null) {
                accessPref.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return true;
                });
            }

            // القائمة السوداء
            Preference blacklistPref = findPreference("pref_blacklist");
            if (blacklistPref != null) {
                blacklistPref.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(requireContext(), BlacklistActivity.class));
                    return true;
                });
            }
        }
    }
}

package com.clipstack.app;

import android.content.Intent;
import android.os.Bundle;
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
            getSupportActionBar().setTitle(R.string.settings_title);
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

            Preference blacklistPref = findPreference("pref_blacklist");
            if (blacklistPref != null) {
                blacklistPref.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(requireContext(), BlacklistActivity.class));
                    return true;
                });
            }

            SwitchPreferenceCompat notifPref = findPreference("pref_notification");
            if (notifPref != null) {
                notifPref.setOnPreferenceChangeListener((p, v) -> {
                    boolean on = (Boolean) v;
                    if (on) ClipboardService.start(requireContext());
                    else    ClipboardService.stop(requireContext());
                    return true;
                });
            }
        }
    }
}

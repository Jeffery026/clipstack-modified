package com.clipstack.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.*;
import java.io.OutputStream;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_EXPORT_ALL     = 201;
    private static final int REQ_EXPORT_STARRED = 202;
    private static final int REQ_IMPORT         = 203;

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

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (req == REQ_EXPORT_ALL || req == REQ_EXPORT_STARRED) {
            try {
                OutputStream os = getContentResolver().openOutputStream(uri);
                boolean ok = BackupManager.writeBackup(this, os, req == REQ_EXPORT_STARRED);
                if (ok) Toast.makeText(this, "✅ تم التصدير بنجاح", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "❌ فشل: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (req == REQ_IMPORT) {
            try {
                int count = BackupManager.importFromStream(this,
                        getContentResolver().openInputStream(uri));
                Toast.makeText(this, "✅ استُورد " + count + " نسخة", Toast.LENGTH_LONG).show();
                sendBroadcast(new Intent(ClipboardService.ACTION_REFRESH));
            } catch (Exception e) {
                Toast.makeText(this, "❌ فشل الاستيراد", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle s, String r) {
            setPreferencesFromResource(R.xml.preferences, r);

            // الثيم
            ListPreference themePref = findPreference("pref_theme");
            if (themePref!=null) {
                themePref.setOnPreferenceChangeListener((p,v) -> {
                    ThemeManager.setTheme(requireContext(), (String)v);
                    requireActivity().recreate();
                    return true;
                });
            }

            // مدة الحفظ
            ListPreference daysPref = findPreference("pref_days");
            if (daysPref!=null) {
                daysPref.setOnPreferenceChangeListener((p,v) -> {
                    int d = Integer.parseInt((String)v);
                    if (d>0) ClipDatabase.get(requireContext()).deleteOlderThan(d);
                    return true;
                });
            }

            // الإشعار
            SwitchPreferenceCompat notifPref = findPreference("pref_notification");
            if (notifPref!=null) {
                notifPref.setOnPreferenceChangeListener((p,v) -> {
                    if ((Boolean)v) ClipboardService.start(requireContext());
                    else            ClipboardService.stop(requireContext());
                    return true;
                });
            }

            // الزر العائم
            SwitchPreferenceCompat floatPref = findPreference("pref_float_button");
            if (floatPref!=null) {
                floatPref.setOnPreferenceChangeListener((p,v) -> {
                    boolean on = (Boolean)v;
                    if (on && !Settings.canDrawOverlays(requireContext())) {
                        Toast.makeText(requireContext(),
                                "اسمح للتطبيق بالظهور فوق التطبيقات الأخرى",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:com.clipstack.app")));
                        return false;
                    }
                    return true;
                });
            }

            // تصدير
            Preference exportPref = findPreference("pref_export");
            if (exportPref!=null) {
                exportPref.setOnPreferenceClickListener(p -> {
                    SettingsActivity act = (SettingsActivity) requireActivity();
                    new AlertDialog.Builder(requireContext())
                        .setTitle("تصدير النسخ")
                        .setItems(new String[]{"تصدير الكل", "تصدير المفضلة فقط"}, (d,w) -> {
                            int req = (w==0) ? REQ_EXPORT_ALL : REQ_EXPORT_STARRED;
                            boolean starred = (w==1);
                            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TITLE,
                                    BackupManager.getDefaultFilename(starred));
                            act.startActivityForResult(i, req);
                        })
                        .setNegativeButton("إلغاء", null)
                        .show();
                    return true;
                });
            }

            // استيراد
            Preference importPref = findPreference("pref_import");
            if (importPref!=null) {
                importPref.setOnPreferenceClickListener(p -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("text/*");
                    ((SettingsActivity)requireActivity()).startActivityForResult(i, REQ_IMPORT);
                    return true;
                });
            }

            // القائمة السوداء
            Preference blacklistPref = findPreference("pref_blacklist");
            if (blacklistPref!=null) {
                blacklistPref.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(requireContext(), BlacklistActivity.class));
                    return true;
                });
            }

            // إمكانية الوصول
            Preference accessPref = findPreference("pref_accessibility");
            if (accessPref!=null) {
                accessPref.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    return true;
                });
            }
        }
    }
}

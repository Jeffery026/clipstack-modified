package com.clipstack.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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

            // مدة الحفظ
            ListPreference daysPref = findPreference("pref_days");
            if (daysPref!=null) {
                daysPref.setOnPreferenceChangeListener((p,v) -> {
                    int d = Integer.parseInt((String)v);
                    if (d>0) ClipDatabase.get(requireContext()).deleteOlderThan(d);
                    Toast.makeText(requireContext(),
                            d<0 ? "حفظ غير محدود" : "تم ضبط المدة على " + d + " يوم",
                            Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            // الإشعار الدائم
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
                                "يرجى السماح للتطبيق بالظهور فوق التطبيقات",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:com.clipstack.app")));
                        return false;
                    }
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

            // التصدير
            Preference exportPref = findPreference("pref_export");
            if (exportPref!=null) {
                exportPref.setOnPreferenceClickListener(p -> {
                    new AlertDialog.Builder(requireContext())
                        .setTitle("تصدير النسخ")
                        .setItems(new String[]{"تصدير الكل", "تصدير المفضلة فقط"}, (d,w) -> {
                            BackupManager.exportToFile(requireContext(), w==1);
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
                    return true;
                });
            }

            // الاستيراد
            Preference importPref = findPreference("pref_import");
            if (importPref!=null) {
                importPref.setOnPreferenceClickListener(p -> {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("text/*");
                    startActivityForResult(pick, 101);
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

        @Override public void onActivityResult(int req, int res, Intent data) {
            super.onActivityResult(req, res, data);
            if (req==101 && res==android.app.Activity.RESULT_OK && data!=null) {
                try {
                    java.io.InputStream is = requireContext()
                            .getContentResolver().openInputStream(data.getData());
                    byte[] bytes = new byte[is.available()];
                    is.read(bytes);
                    is.close();
                    String content = new String(bytes);
                    int count = BackupManager.importFromText(requireContext(), content);
                    Toast.makeText(requireContext(),
                            "✅ تم استيراد " + count + " نسخة", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(requireContext(),
                            "❌ فشل الاستيراد", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}

package com.clipstack.app;

import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.util.*;

public class BlacklistActivity extends AppCompatActivity {

    private ClipDatabase db;
    private Set<String>  blacklisted;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_settings);
        db = ClipDatabase.get(this);
        blacklisted = db.getBlacklist();

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar()!=null) {
            getSupportActionBar().setTitle(R.string.blacklist_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));

        FrameLayout container = findViewById(R.id.settings_container);
        container.addView(rv);

        // Load installed apps in background
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            apps.sort((a,b) -> pm.getApplicationLabel(a).toString()
                    .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
            runOnUiThread(() -> rv.setAdapter(new AppAdapter(apps, pm)));
        }).start();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        final List<ApplicationInfo> apps;
        final PackageManager pm;
        AppAdapter(List<ApplicationInfo> a, PackageManager p) { apps=a; pm=p; }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_app, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            ApplicationInfo info = apps.get(pos);
            h.name.setText(pm.getApplicationLabel(info));
            try { h.icon.setImageDrawable(pm.getApplicationIcon(info.packageName)); }
            catch (Exception ignored) {}
            h.check.setChecked(blacklisted.contains(info.packageName));
            h.itemView.setOnClickListener(v -> {
                if (blacklisted.contains(info.packageName)) {
                    blacklisted.remove(info.packageName);
                    db.removeFromBlacklist(info.packageName);
                    h.check.setChecked(false);
                } else {
                    blacklisted.add(info.packageName);
                    db.addToBlacklist(info.packageName);
                    h.check.setChecked(true);
                }
            });
        }
        @Override public int getItemCount() { return apps.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView name; CheckBox check;
            VH(View v) {
                super(v);
                icon  = v.findViewById(R.id.app_icon);
                name  = v.findViewById(R.id.app_name);
                check = v.findViewById(R.id.app_check);
            }
        }
    }
}

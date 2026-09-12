package com.clipstack.app;

import android.content.pm.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.util.*;

public class BlacklistActivity extends AppCompatActivity {

    private ClipDatabase       db;
    private Set<String>        blacklisted;
    private RecyclerView       rv;
    private ProgressBar        progress;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        // لا نستخدم activity_settings — نبني الـ layout يدوياً
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Toolbar tb = new Toolbar(this);
        tb.setBackgroundColor(getColor(R.color.primary));
        tb.setTitleTextColor(0xFFFFFFFF);
        root.addView(tb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getActionBarSize()));

        progress = new ProgressBar(this);
        progress.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dpToPx(24);
        root.addView(progress, lp);

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setVisibility(View.GONE);
        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.blacklist_title));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        db          = ClipDatabase.get(this);
        blacklisted = db.getBlacklist();

        // تحميل التطبيقات في خيط خلفي
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            // فقط التطبيقات التي ثبّتها المستخدم
            List<PackageInfo> pkgs = pm.getInstalledPackages(0);
            List<ApplicationInfo> userApps = new ArrayList<>();
            for (PackageInfo pi : pkgs) {
                if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    userApps.add(pi.applicationInfo);
                }
            }
            userApps.sort((a, b) ->
                    pm.getApplicationLabel(a).toString()
                      .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                rv.setAdapter(new AppAdapter(userApps, pm));
            });
        }).start();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private int getActionBarSize() {
        int[] attrs = { android.R.attr.actionBarSize };
        android.content.res.TypedArray a = obtainStyledAttributes(attrs);
        int size = a.getDimensionPixelSize(0, dpToPx(56));
        a.recycle();
        return size;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

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

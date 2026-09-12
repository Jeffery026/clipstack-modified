package com.clipstack.app;

import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private ClipDatabase      db;
    private ClipAdapter       adapter;
    private TabLayout         tabs;
    private RecyclerView      recycler;
    private LinearLayout      emptyView;
    private SharedPreferences prefs;

    private String  filterPkg   = null;
    private String  searchQuery = "";
    private boolean showStarred = false;
    private MenuItem starMenuItem;

    private final BroadcastReceiver refreshRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); rebuildTabs(); }
    };

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        db    = ClipDatabase.get(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar()!=null) getSupportActionBar().setTitle("Clip Stack");

        tabs      = findViewById(R.id.tabs);
        recycler  = findViewById(R.id.recycler);
        emptyView = findViewById(R.id.empty_view);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(new DefaultItemAnimator());
        adapter = new ClipAdapter();
        recycler.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                filterPkg = (String) t.getTag(); reload();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showEditDialog(null));

        ClipboardService.start(this);
        rebuildTabs();
        reload();
    }

    @Override protected void onResume() {
        super.onResume();
        // قراءة الحافظة عند فتح التطبيق (يعمل دائماً لأننا في المقدمة)
        captureClipboardNow();

        registerReceiver(refreshRx,
                new IntentFilter(ClipboardService.ACTION_REFRESH),
                Context.RECEIVER_NOT_EXPORTED);
        rebuildTabs();
        reload();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // فتحنا بسبب إشعار "اضغط لحفظ"
        if (ClipboardService.ACTION_SAVE_NOW.equals(intent.getAction())) {
            captureClipboardNow();
        }
    }

    private void captureClipboardNow() {
        try {
            android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
            if (cs == null) return;
            String text = cs.toString().trim();
            if (text.isEmpty()) return;

            String lastClip = prefs.getString(AppTrackerService.KEY_LAST_CLIP, "");
            if (text.equals(lastClip)) return;
            prefs.edit().putString(AppTrackerService.KEY_LAST_CLIP, text).apply();

            String pkg = prefs.getString(AppTrackerService.KEY_PKG, "");
            if (!pkg.isEmpty() && db.isBlacklisted(pkg)) return;

            db.insert(text, pkg);
            String days = prefs.getString("pref_days", "-1");
            db.deleteOlderThan(Integer.parseInt(days));
            reload(); rebuildTabs();
        } catch (Exception ignored) {}
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(refreshRx); } catch (Exception ignored) {}
    }

    // ── Tabs ──
    private void rebuildTabs() {
        String saved = filterPkg;
        tabs.removeAllTabs();
        tabs.addTab(tabs.newTab().setText(getString(R.string.all_clips)).setTag(null));
        for (String pkg : db.distinctPackages()) {
            tabs.addTab(tabs.newTab().setText(appLabel(pkg)).setTag(pkg));
        }
        if (saved != null) {
            for (int i=0; i<tabs.getTabCount(); i++) {
                TabLayout.Tab t = tabs.getTabAt(i);
                if (t!=null && saved.equals(t.getTag())) { tabs.selectTab(t); return; }
            }
        }
        if (tabs.getTabCount()>0) { tabs.selectTab(tabs.getTabAt(0)); filterPkg = null; }
    }

    private String appLabel(String pkg) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg,0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            String[] p = pkg.split("\\."); return p[p.length-1];
        }
    }

    // ── Data ──
    private void reload() {
        List<ClipItem> list;
        if (showStarred)          list = db.getStarred();
        else if (filterPkg!=null) list = db.getByPkg(filterPkg);
        else                      list = db.getAll(searchQuery);
        adapter.setData(list);
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Menu ──
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        starMenuItem = menu.findItem(R.id.action_starred);
        MenuItem si = menu.findItem(R.id.action_search);
        SearchView sv = (SearchView) si.getActionView();
        if (sv != null) {
            sv.setQueryHint(getString(R.string.search_hint));
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String q) { return false; }
                @Override public boolean onQueryTextChange(String q) {
                    searchQuery = q; reload(); return true;
                }
            });
        }
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_starred) {
            showStarred = !showStarred;
            item.setIcon(showStarred
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
            reload();
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_delete_all) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete))
                .setMessage(getString(R.string.confirm_delete_all))
                .setPositiveButton(getString(R.string.delete),
                    (d,w) -> { db.deleteNonStarred(); reload(); rebuildTabs(); })
                .setNegativeButton(getString(R.string.cancel), null).show();
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Edit Dialog ──
    void showEditDialog(ClipItem item) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_edit, null);
        TextInputEditText et = v.findViewById(R.id.edit_text);

        if (item != null) {
            et.setText(item.text);
            et.setSelection(item.text.length());
        } else {
            try {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm!=null && cm.hasPrimaryClip()) {
                    CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                    if (cs!=null) { et.setText(cs.toString()); et.selectAll(); }
                }
            } catch (Exception ignored) {}
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle(item!=null ? getString(R.string.edit_clip) : "إضافة نص")
            .setView(v)
            .setPositiveButton(getString(R.string.save), (d,w) -> {
                String txt = et.getText()!=null ? et.getText().toString().trim() : "";
                if (!txt.isEmpty()) {
                    if (item!=null) db.delete(item.id);
                    db.insert(txt, item!=null ? item.sourcePackage : "");
                    reload(); rebuildTabs();
                }
            })
            .setNegativeButton(getString(R.string.cancel), null);

        if (item!=null) {
            b.setNeutralButton(getString(R.string.delete), (d,w) -> {
                db.delete(item.id); reload(); rebuildTabs();
            });
        }

        AlertDialog dlg = b.create();
        dlg.show();
        try {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.primary));
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getColor(R.color.text_secondary));
            if (item!=null)
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getColor(R.color.delete_red));
        } catch (Exception ignored) {}
    }

    // ══ Adapter ══════════════════════════════════════
    class ClipAdapter extends RecyclerView.Adapter<ClipAdapter.VH> {
        private List<ClipItem> data = new ArrayList<>();
        void setData(List<ClipItem> d) { data=d; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_clip, p, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            ClipItem item = data.get(pos);
            h.tvText.setText(item.text);
            h.tvDate.setText(android.text.format.DateFormat.format("dd/MM · HH:mm", item.date));

            if (item.sourcePackage!=null && !item.sourcePackage.isEmpty()) {
                h.tvSource.setText(appLabel(item.sourcePackage));
                h.tvSource.setVisibility(View.VISIBLE);
            } else {
                h.tvSource.setVisibility(View.GONE);
            }

            // نجمة
            h.btnStar.setIconResource(item.starred
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
            h.btnStar.setOnClickListener(v -> {
                db.toggleStar(item.id, item.starred);
                item.starred = !item.starred;
                h.btnStar.setIconResource(item.starred
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off);
                if (showStarred) { reload(); rebuildTabs(); }
            });

            // نسخ
            h.btnCopy.setOnClickListener(v -> {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm!=null) cm.setText(item.text);
                Toast.makeText(MainActivity.this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
            });

            // مشاركة
            h.btnShare.setOnClickListener(v ->
                startActivity(Intent.createChooser(
                    new Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, item.text)
                        .setType("text/plain"), null)));

            // النقر لتحرير
            h.card.setOnClickListener(v -> showEditDialog(item));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView tvText, tvDate, tvSource;
            MaterialButton btnStar, btnCopy, btnShare;
            VH(View v) {
                super(v);
                card     = (MaterialCardView) v;
                tvText   = v.findViewById(R.id.tv_text);
                tvDate   = v.findViewById(R.id.tv_date);
                tvSource = v.findViewById(R.id.tv_source);
                btnStar  = v.findViewById(R.id.btn_star);
                btnCopy  = v.findViewById(R.id.btn_copy);
                btnShare = v.findViewById(R.id.btn_share);
            }
        }
    }
}

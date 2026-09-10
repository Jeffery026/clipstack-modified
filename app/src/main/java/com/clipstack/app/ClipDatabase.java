package com.clipstack.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.*;

public class ClipDatabase extends SQLiteOpenHelper {
    private static final String DB   = "clips.db";
    private static final int    VER  = 2;
    private static ClipDatabase inst;

    public static synchronized ClipDatabase get(Context c) {
        if (inst == null) inst = new ClipDatabase(c.getApplicationContext());
        return inst;
    }
    private ClipDatabase(Context c) { super(c, DB, null, VER); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clips(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "text TEXT,date INTEGER,starred INTEGER DEFAULT 0,source_pkg TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE blacklist(pkg TEXT PRIMARY KEY)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS clips");
        db.execSQL("DROP TABLE IF EXISTS blacklist");
        onCreate(db);
    }

    public void insert(String text, String pkg) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("clips", "text=?", new String[]{text});
        ContentValues v = new ContentValues();
        v.put("text", text); v.put("date", System.currentTimeMillis());
        v.put("starred", 0); v.put("source_pkg", pkg != null ? pkg : "");
        db.insert("clips", null, v);
        pruneOld(db);
    }
    private void pruneOld(SQLiteDatabase db) { /* handled in service */ }

    public void delete(long id) {
        getWritableDatabase().delete("clips","id=?",new String[]{String.valueOf(id)});
    }
    public void deleteNonStarred() {
        getWritableDatabase().delete("clips","starred=0",null);
    }
    public void deleteOlderThan(int days) {
        if (days < 0) return;
        long cutoff = System.currentTimeMillis() - (long)days*86400_000L;
        getWritableDatabase().delete("clips","starred=0 AND date<?",
                new String[]{String.valueOf(cutoff)});
    }
    public void toggleStar(long id, boolean cur) {
        ContentValues v = new ContentValues();
        v.put("starred", cur ? 0 : 1);
        getWritableDatabase().update("clips",v,"id=?",new String[]{String.valueOf(id)});
    }

    public List<ClipItem> getAll(String search) {
        String w = (search!=null&&!search.isEmpty())
                ? "text LIKE '%"+search.replace("'","''") + "%'" : null;
        return query(w);
    }
    public List<ClipItem> getByPkg(String pkg) {
        return query("source_pkg='"+pkg.replace("'","''")+"'");
    }
    public List<ClipItem> getStarred() { return query("starred=1"); }

    public List<String> distinctPackages() {
        Set<String> set = new LinkedHashSet<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT source_pkg FROM clips WHERE source_pkg!='' ORDER BY date DESC",null);
        while(c.moveToNext()) set.add(c.getString(0));
        c.close();
        return new ArrayList<>(set);
    }
    private List<ClipItem> query(String where) {
        List<ClipItem> list = new ArrayList<>();
        String sql = "SELECT id,text,date,starred,source_pkg FROM clips"
                +(where!=null?" WHERE "+where:"")+" ORDER BY date DESC";
        Cursor c = getReadableDatabase().rawQuery(sql,null);
        while(c.moveToNext()){
            ClipItem i=new ClipItem();
            i.id=c.getLong(0);i.text=c.getString(1);
            i.date=c.getLong(2);i.starred=c.getInt(3)==1;
            i.sourcePackage=c.getString(4); list.add(i);
        }
        c.close(); return list;
    }

    // ── Blacklist ──
    public void addToBlacklist(String pkg) {
        ContentValues v=new ContentValues(); v.put("pkg",pkg);
        getWritableDatabase().insertOrThrow("blacklist",null,v);
    }
    public void removeFromBlacklist(String pkg) {
        getWritableDatabase().delete("blacklist","pkg=?",new String[]{pkg});
    }
    public Set<String> getBlacklist() {
        Set<String> set = new HashSet<>();
        Cursor c=getReadableDatabase().rawQuery("SELECT pkg FROM blacklist",null);
        while(c.moveToNext()) set.add(c.getString(0));
        c.close(); return set;
    }
    public boolean isBlacklisted(String pkg) {
        Cursor c=getReadableDatabase().rawQuery(
                "SELECT 1 FROM blacklist WHERE pkg=?",new String[]{pkg});
        boolean r=c.moveToFirst(); c.close(); return r;
    }
}

package com.clipstack.app;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupManager {

    private static final String BACKUP_DIR = "ClipStack_Backups";
    private static final String SEP = "---CLIP---";

    /** تصدير إلى مجلد خاص بالتطبيق — لا يحتاج أي صلاحية */
    public static void exportToFile(Context ctx, boolean starredOnly) {
        try {
            File dir = new File(ctx.getExternalFilesDir(null), BACKUP_DIR);
            if (!dir.exists()) dir.mkdirs();

            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                    .format(new Date());
            String suffix = starredOnly ? "_starred" : "_all";
            File file = new File(dir, "ClipStack_" + stamp + suffix + ".txt");

            ClipDatabase db = ClipDatabase.get(ctx);
            List<ClipItem> clips = starredOnly ? db.getStarred() : db.getAll(null);

            BufferedWriter w = new BufferedWriter(new FileWriter(file));
            w.write("# Clip Stack Backup — " + stamp + "\n");
            w.write("# العدد: " + clips.size() + " نسخة\n\n");

            for (ClipItem c : clips) {
                w.write(SEP + "\n");
                w.write("DATE=" + c.date + "\n");
                w.write("SOURCE=" + c.sourcePackage + "\n");
                w.write("STARRED=" + (c.starred ? "1" : "0") + "\n");
                w.write(c.text + "\n");
            }
            w.close();

            // مشاركة الملف
            shareFile(ctx, file);

            Toast.makeText(ctx, "✅ تم التصدير:\n" + file.getName(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(ctx, "❌ فشل التصدير: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** استيراد من ملف */
    public static int importFromText(Context ctx, String content) {
        int count = 0;
        try {
            ClipDatabase db = ClipDatabase.get(ctx);
            String[] sections = content.split(SEP);
            for (String section : sections) {
                section = section.trim();
                if (section.isEmpty() || section.startsWith("#")) continue;
                String[] lines = section.split("\n");
                String date = "", source = "", starred = "0", text = "";
                StringBuilder textBuilder = new StringBuilder();
                boolean inText = false;
                for (String line : lines) {
                    if (line.startsWith("DATE="))    { date = line.substring(5); }
                    else if (line.startsWith("SOURCE=")) { source = line.substring(7); }
                    else if (line.startsWith("STARRED=")) { starred = line.substring(8); }
                    else { textBuilder.append(line).append("\n"); }
                }
                text = textBuilder.toString().trim();
                if (!text.isEmpty()) {
                    db.insert(text, source);
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    private static void shareFile(Context ctx, File file) {
        try {
            androidx.core.content.FileProvider fp = null;
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".provider", file);
            Intent share = new Intent(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .setType("text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(Intent.createChooser(share, "مشاركة النسخة الاحتياطية"));
        } catch (Exception ignored) {
            // FileProvider not set up - just notify of path
        }
    }

    public static String getBackupDirPath(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), BACKUP_DIR);
        return dir.getAbsolutePath();
    }
}

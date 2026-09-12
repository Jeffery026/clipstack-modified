package com.clipstack.app;

import android.content.Context;
import android.widget.Toast;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupManager {

    private static final String SEP = "---CLIP_ITEM---";

    /** كتابة النسخ إلى OutputStream (من SAF أو أي مصدر) */
    public static boolean writeBackup(Context ctx, OutputStream os, boolean starredOnly) {
        try {
            ClipDatabase db = ClipDatabase.get(ctx);
            List<ClipItem> clips = starredOnly ? db.getStarred() : db.getAll(null);

            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date());
            w.write("# Clip Stack Backup — " + stamp + "\n");
            w.write("# العدد: " + clips.size() + " نسخة\n\n");

            for (ClipItem c : clips) {
                w.write(SEP + "\n");
                w.write("DATE=" + c.date + "\n");
                w.write("SOURCE=" + c.sourcePackage + "\n");
                w.write("STARRED=" + (c.starred ? "1" : "0") + "\n");
                w.write(c.text);
                w.write("\n");
            }
            w.flush();
            w.close();
            return true;
        } catch (Exception e) {
            Toast.makeText(ctx, "❌ فشل التصدير: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** استيراد من InputStream */
    public static int importFromStream(Context ctx, InputStream is) {
        int count = 0;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder current = new StringBuilder();
            String line;
            boolean inClip = false;
            String date = "", source = "", starred = "0";
            StringBuilder textBuf = new StringBuilder();

            while ((line = r.readLine()) != null) {
                if (line.equals(SEP)) {
                    if (inClip && textBuf.length() > 0) {
                        ClipDatabase.get(ctx).insert(textBuf.toString().trim(), source);
                        count++;
                    }
                    inClip = true;
                    date = ""; source = ""; starred = "0"; textBuf = new StringBuilder();
                } else if (inClip) {
                    if (line.startsWith("DATE="))    date    = line.substring(5);
                    else if (line.startsWith("SOURCE="))  source  = line.substring(7);
                    else if (line.startsWith("STARRED=")) starred = line.substring(8);
                    else if (!line.startsWith("#")) textBuf.append(line).append("\n");
                }
            }
            if (inClip && textBuf.length() > 0) {
                ClipDatabase.get(ctx).insert(textBuf.toString().trim(), source);
                count++;
            }
            r.close();
        } catch (Exception e) {
            Toast.makeText(ctx, "❌ فشل الاستيراد", Toast.LENGTH_SHORT).show();
        }
        return count;
    }

    public static String getDefaultFilename(boolean starred) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return "ClipStack_" + stamp + (starred ? "_starred" : "_all") + ".txt";
    }
}

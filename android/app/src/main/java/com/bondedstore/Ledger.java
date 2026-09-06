package com.bondedstore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * The store's ledger on disk.
 *
 * The WebView's own storage is a cache the system may clear — clear the app's
 * data, run low on space, reinstall, and a month's takings are gone with it. So
 * the record is this file, and the page's storage is only a mirror of it.
 *
 * Deliberately free of Android imports, so the part that must not lose data can
 * be run and tested off-device.
 */
class Ledger {

    /** A backup at most this often — enough to span weeks, not one per keystroke. */
    static final long BACKUP_EVERY_MS = 10 * 60 * 1000;
    static final int  KEEP_BACKUPS    = 20;

    private final File dir;

    Ledger(File baseDir) { this.dir = new File(baseDir, "data"); }

    File file()       { return new File(dir, "state.json"); }
    File backupDir()  { return new File(dir, "backups"); }

    /** The stored ledger, or "" when there is none yet or it cannot be read. */
    String load() {
        try {
            File f = file();
            return f.exists() ? read(f) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Write the ledger. Returns false if it did not reach the disk. */
    boolean save(String json) {
        if (json == null) return false;
        try {
            if (!dir.exists() && !dir.mkdirs()) return false;
            File dst = file();
            rotateBackup(dst);

            // Write beside the target, flush it to the platter, then rename over
            // it. rename(2) either replaces the file completely or does nothing,
            // so a crash or a flat battery mid-write can never leave half a
            // ledger behind — the worst case is losing the newest edit, never
            // the file.
            File tmp = new File(dir, "state.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } finally {
                out.close();
            }
            if (!tmp.renameTo(dst)) { tmp.delete(); return false; }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Where the ledger is and when it was last written, for the app to show. */
    String info() {
        File f = file();
        File[] bs = backupDir().listFiles();
        return "{\"mode\":\"file\",\"path\":\"" + f.getAbsolutePath()
                + "\",\"bytes\":" + (f.exists() ? f.length() : 0)
                + ",\"savedAt\":" + (f.exists() ? f.lastModified() : 0)
                + ",\"backups\":" + (bs == null ? 0 : bs.length) + "}";
    }

    /**
     * Keep a thinned history. Saving happens on every edit, so copying each time
     * would fill the disk with a hundred versions of one afternoon; one every
     * ten minutes spans weeks in the same space.
     */
    private void rotateBackup(File state) {
        if (!state.exists()) return;
        try {
            File bdir = backupDir();
            if (!bdir.exists() && !bdir.mkdirs()) return;

            File[] existing = bdir.listFiles();
            long newest = 0;
            if (existing != null) for (File b : existing) newest = Math.max(newest, b.lastModified());
            if (now() - newest < BACKUP_EVERY_MS) return;

            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(now()));
            File out = new File(bdir, "state-" + stamp + ".json");
            FileOutputStream os = new FileOutputStream(out);
            try {
                os.write(read(state).getBytes(StandardCharsets.UTF_8));
            } finally {
                os.close();
            }
            out.setLastModified(now());

            File[] all = bdir.listFiles();
            if (all != null && all.length > KEEP_BACKUPS) {
                Arrays.sort(all, new Comparator<File>() {
                    public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
                });
                for (int i = KEEP_BACKUPS; i < all.length; i++) all[i].delete();
            }
        } catch (Exception e) {
            // A missing backup must never stop the save that follows it.
        }
    }

    /** Overridable so the backup schedule can be exercised without waiting. */
    long now() { return System.currentTimeMillis(); }

    static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}

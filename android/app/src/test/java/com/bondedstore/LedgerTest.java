package com.bondedstore;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * The ledger is the one part of this app that must not lose data, so it is the
 * one part with tests. Ledger has no Android imports precisely so these can run
 * on an ordinary JVM, with no device and no emulator.
 */
public class LedgerTest {

    private Ledger at(File dir) { return new Ledger(dir); }
    private File tmp(String name) throws Exception { return Files.createTempDirectory(name).toFile(); }

    @Test public void emptyStoreReadsAsEmptyString() throws Exception {
        assertEquals("", at(tmp("l")).load());
    }

    @Test public void writesAndReadsBackExactly() throws Exception {
        File base = tmp("l");
        Ledger l = at(base);
        String json = "{\"v\":1,\"sales\":[{\"id\":\"S1\"}]}";
        assertTrue(l.save(json));
        assertEquals(json, l.load());
        assertTrue(l.file().getPath().endsWith("data/state.json"));
    }

    @Test public void survivesRestart() throws Exception {
        File base = tmp("l");
        at(base).save("{\"a\":1}");
        assertEquals("{\"a\":1}", at(base).load());       // a fresh instance, as after a restart
    }

    @Test public void leavesNoTempFileBehind() throws Exception {
        File base = tmp("l");
        Ledger l = at(base);
        l.save("{\"a\":1}");
        assertFalse(new File(l.file().getParentFile(), "state.json.tmp").exists());
    }

    @Test public void keepsUnicodeIntact() throws Exception {
        Ledger l = at(tmp("l"));
        String tr = "{\"n\":\"ÇAY & KAHVE — ĞÜŞİÖ\"}";
        l.save(tr);
        assertEquals(tr, l.load());
    }

    /** A shorter ledger must replace the old one, not leave its tail in place. */
    @Test public void overwriteTruncates() throws Exception {
        Ledger l = at(tmp("l"));
        l.save("{\"long\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");
        l.save("{\"a\":1}");
        assertEquals("{\"a\":1}", l.load());
    }

    /** Saving happens on every edit; backups must not follow one for one. */
    @Test public void backupsAreThinned() throws Exception {
        Ledger l = at(tmp("l"));
        for (int i = 0; i < 40; i++) l.save("{\"i\":" + i + "}");
        File[] backups = l.backupDir().listFiles();
        assertTrue("40 rapid saves should not make 40 backups",
                backups == null || backups.length <= 1);
        assertEquals("{\"i\":39}", l.load());
    }

    @Test public void oldestBackupsArePruned() throws Exception {
        Ledger l = at(tmp("l"));
        l.save("{\"seed\":1}");
        File bdir = l.backupDir();
        assertTrue(bdir.exists() || bdir.mkdirs());
        for (int i = 0; i < 30; i++) {
            File b = new File(bdir, String.format("state-old-%02d.json", i));
            try (FileOutputStream o = new FileOutputStream(b)) { o.write(("{\"old\":" + i + "}").getBytes("UTF-8")); }
            assertTrue(b.setLastModified(1_500_000_000_000L + i * 1000L));
        }
        l.save("{\"seed\":2}");                       // triggers rotate + prune

        File[] left = bdir.listFiles();
        assertNotNull(left);
        assertEquals(Ledger.KEEP_BACKUPS, left.length);
        long oldest = Long.MAX_VALUE;
        for (File b : left) oldest = Math.min(oldest, b.lastModified());
        assertTrue("the pruned ones should be the oldest", oldest > 1_500_000_000_000L + 9 * 1000L);
    }

    /** Losing a backup is bad; losing the sale because of it would be worse. */
    @Test public void backupFailureDoesNotBlockTheSave() throws Exception {
        File base = tmp("l");
        Ledger l = at(base);
        l.save("{\"first\":1}");
        File bdir = l.backupDir();
        assertTrue(bdir.exists() || bdir.mkdirs());
        assertTrue(l.save("{\"second\":2}"));
        assertEquals("{\"second\":2}", l.load());
    }

    /** A refused write has to come back as false so the app can warn the officer. */
    @Test public void refusedWriteReportsFalse() throws Exception {
        File base = tmp("l");
        try (FileOutputStream o = new FileOutputStream(new File(base, "data"))) { o.write(1); }
        Ledger l = at(base);                          // a file sits where data/ must go
        assertFalse(l.save("{\"y\":2}"));
        assertEquals("", l.load());
    }

    @Test public void aFailedWriteLeavesTheOldLedgerIntact() throws Exception {
        Ledger l = at(tmp("l"));
        l.save("{\"good\":1}");
        File blocked = new File(l.file().getParentFile(), "state.json.tmp");
        assertTrue(blocked.mkdirs());                 // cannot be opened as a file
        assertFalse(l.save("{\"bad\":2}"));
        assertEquals("{\"good\":1}", l.load());
    }

    @Test public void nullIsRefused() throws Exception {
        assertFalse(at(tmp("l")).save(null));
    }

    @Test public void infoReportsModeAndSize() throws Exception {
        Ledger l = at(tmp("l"));
        l.save("{\"good\":1}");
        String info = l.info();
        assertTrue(info, info.contains("\"mode\":\"file\""));
        assertTrue(info, info.contains("\"bytes\":10"));
    }
}

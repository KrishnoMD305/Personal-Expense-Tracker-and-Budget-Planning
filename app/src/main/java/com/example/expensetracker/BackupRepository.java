package com.example.expensetracker;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Stage 5: complete backup and restore of the Room database (plus the chosen theme).
 *
 * <p><b>Restore is all-or-nothing.</b> The file is parsed and validated first ({@link BackupJson}); a safety copy
 * of the current data is written to the app's private storage; then the old rows are replaced inside ONE
 * database transaction. If anything fails, SQLite rolls the transaction back and the existing data stays exactly
 * as it was.
 */
final class BackupRepository {
    /** Backups bigger than this are rejected before they are read into memory. */
    static final long MAX_BACKUP_BYTES = 64L * 1024 * 1024;
    private static final String SAFETY_DIR = "auto_backups";
    private static final int SAFETY_KEEP = 3;

    private BackupRepository() {}

    /** Reads every table in one consistent transaction. Call from a background thread. */
    static BackupData snapshot(final Context context) {
        BackupData d = snapshot(AppDatabase.getInstance(context));
        d.theme = AppSettings.get(context).theme();
        d.createdAtMillis = System.currentTimeMillis();
        d.appVersion = BuildConfigInfo.versionName(context);
        return d;
    }

    /** The database part of {@link #snapshot(Context)}; takes the database so tests can use an in-memory one. */
    static BackupData snapshot(final AppDatabase db) {
        final BackupData d = db.runInTransaction(new java.util.concurrent.Callable<BackupData>() {
            @Override
            public BackupData call() {
                BackupDao dao = db.backupDao();
                BackupData data = new BackupData();
                data.transactions = dao.transactions();
                data.budgets = dao.budgets();
                data.goals = dao.goals();
                data.contributions = dao.contributions();
                data.rules = dao.rules();
                data.postings = dao.postings();
                data.bills = dao.bills();
                return data;
            }
        });
        d.dbVersion = AppDatabase.VERSION;
        return d;
    }

    /** Writes a full backup to {@code out} (UTF-8 JSON). */
    static BackupData writeBackup(Context context, OutputStream out) throws IOException, JSONException {
        BackupData d = snapshot(context);
        out.write(BackupJson.toJson(d).getBytes(StandardCharsets.UTF_8));
        out.flush();
        AppSettings.get(context).setLastBackupMillis(d.createdAtMillis);
        return d;
    }

    /** Reads and validates a backup chosen by the user. Nothing is changed in the database. */
    static BackupData readBackup(ContentResolver resolver, Uri uri) throws IOException, BackupException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new BackupException("The file could not be opened.");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > MAX_BACKUP_BYTES) throw new BackupException("The file is too large to be a backup.");
                buffer.write(chunk, 0, n);
            }
            return BackupJson.parse(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Replaces ALL current data with {@code data}. Writes a safety copy first; if that copy cannot be written
     * the restore is cancelled (better no restore than an unrecoverable one).
     */
    static void restore(Context context, final BackupData data) throws IOException, JSONException {
        writeSafetyCopy(context);
        replaceAll(AppDatabase.getInstance(context), data);
        if (data.theme != null && !data.theme.isEmpty()) {
            AppSettings.get(context).setTheme(data.theme); // unknown names fall back to the default theme
        }
    }

    /** Replaces every table's content with {@code data} in ONE transaction (all-or-nothing). */
    static void replaceAll(final AppDatabase db, final BackupData data) {
        db.runInTransaction(new Runnable() {
            @Override
            public void run() {
                BackupDao dao = db.backupDao();
                dao.clearContributions();
                dao.clearPostings();
                dao.clearGoals();
                dao.clearRules();
                dao.clearBills();
                dao.clearBudgets();
                dao.clearTransactions();
                dao.insertTransactions(data.transactions);
                dao.insertBudgets(data.budgets);
                dao.insertGoals(data.goals);
                dao.insertContributions(data.contributions);
                dao.insertRules(data.rules);
                dao.insertPostings(data.postings);
                dao.insertBills(data.bills);
            }
        });
    }

    private static void writeSafetyCopy(Context context) throws IOException, JSONException {
        File dir = new File(context.getFilesDir(), SAFETY_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "before_restore_" + stamp + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(BackupJson.toJson(snapshot(context)).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        File[] old = dir.listFiles();
        if (old != null && old.length > SAFETY_KEEP) {
            Arrays.sort(old, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            for (int i = 0; i < old.length - SAFETY_KEEP; i++) {
                //noinspection ResultOfMethodCallIgnored
                old[i].delete();
            }
        }
    }
}

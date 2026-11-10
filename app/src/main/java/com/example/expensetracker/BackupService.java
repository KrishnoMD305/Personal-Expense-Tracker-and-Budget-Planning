package com.example.expensetracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stage 7: "Back up now" and "Restore" as a <b>foreground service</b> (type {@code dataSync}).
 *
 * <p>A backup or restore can take a while on a slow connection and must not be killed just because the user
 * switches to another app. While it runs, the service shows an ongoing notification whose progress bar advances as
 * rows are uploaded ({@link NotificationCompat.Builder#setProgress}). The Cloud account screen follows the same
 * job through {@link BackupJob}; when the job ends a normal notification reports the result.
 *
 * <p>Start it with {@link #start}, which claims the single job slot first and so refuses a second job.
 */
public class BackupService extends Service {
    static final String ACTION_BACKUP = "com.example.expensetracker.action.CLOUD_BACKUP";
    static final String ACTION_RESTORE = "com.example.expensetracker.action.CLOUD_RESTORE";
    static final String EXTRA_FORCE = "force";

    private static final int ID_PROGRESS = 7101;
    private static final int ID_RESULT = 7102;
    private static final long NOTIFY_INTERVAL_MILLIS = 400;

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastNotifyAt;
    private int lastNotifyStage = -1;

    /**
     * Starts a backup ({@code restore == false}) or a restore. Returns false when a job is already running.
     *
     * @param force for a backup: replace the cloud copy even if another device wrote it (the user confirmed)
     */
    static boolean start(Context context, boolean restore, boolean force) {
        BackupJob.Status s = BackupJob.begin(restore ? BackupJob.Kind.RESTORE : BackupJob.Kind.BACKUP);
        if (s == null) return false;
        Intent i = new Intent(context, BackupService.class)
                .setAction(restore ? ACTION_RESTORE : ACTION_BACKUP)
                .putExtra(EXTRA_FORCE, force);
        try {
            ContextCompat.startForegroundService(context, i);
        } catch (RuntimeException e) { // e.g. the system refuses a foreground service while the app is in the background
            BackupJob.publish(s.finished(BackupJob.Phase.FAILED,
                    context.getString(R.string.job_cannot_start, String.valueOf(e.getMessage())), null));
            BackupJob.release();
        }
        return true;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final boolean restore = intent != null && ACTION_RESTORE.equals(intent.getAction());
        final boolean force = intent != null && intent.getBooleanExtra(EXTRA_FORCE, false);
        final BackupJob.Kind kind = restore ? BackupJob.Kind.RESTORE : BackupJob.Kind.BACKUP;

        // startForegroundService() obliges us to call startForeground() within a few seconds: do it first.
        NotificationHelper.ensureChannel(this);
        BackupJob.Status first = new BackupJob.Status(0, kind, BackupJob.Phase.RUNNING, 0, 0, 0, null, null);
        startInForeground(buildProgress(first));

        if (intent == null || !BackupJob.isRunning()) { // restarted by the system without a job
            finish(startId);
            return START_NOT_STICKY;
        }
        final Context app = getApplicationContext();
        WORK.execute(() -> {
            try {
                if (restore) runRestore(app); else runBackup(app, force);
            } catch (RuntimeException e) { // never leave the job "running" forever
                fail(app, kind, CloudLog.ACTION_BACKUP, String.valueOf(e.getMessage()));
            } finally {
                BackupJob.release();
                main.post(() -> finish(startId));
            }
        });
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------------ the two jobs

    private void runBackup(Context app, boolean force) {
        final BackupJob.Kind kind = BackupJob.Kind.BACKUP;
        String uid = CloudConfig.currentUid(app);
        if (uid == null) {
            fail(app, kind, CloudLog.ACTION_BACKUP, app.getString(R.string.job_not_signed_in));
            return;
        }
        if (DeviceStateReader.read(app).blocksManualBackup()) {
            fail(app, kind, CloudLog.ACTION_BACKUP, app.getString(R.string.job_airplane));
            return;
        }
        CloudSync.ProgressListener listener = progressListener(app, kind);
        try {
            // 1) Receipt photos first, so the bills uploaded below already point to them. A photo problem is
            //    reported but never stops the data backup.
            ReceiptSync.Result receipts = ReceiptSync.uploadPending(app, listener);

            // 2) The data itself (changed rows only).
            CloudSync.UploadResult result = CloudSync.upload(app, force, listener);
            if (result.outcome == CloudSync.Outcome.CONFLICT) {
                publishFinal(app, kind, BackupJob.Phase.CONFLICT, "", result.remote);
                CloudLog.log(app, CloudLog.ACTION_BACKUP, CloudLog.RESULT_CONFLICT, 0, "Another device wrote the cloud copy");
                return;
            }
            StringBuilder msg = new StringBuilder(result.outcome == CloudSync.Outcome.UPLOADED
                    ? app.getString(R.string.cloud_backup_done_message, result.rows)
                    : app.getString(R.string.cloud_backup_unchanged_message));
            if (receipts.done > 0) msg.append("\n\n").append(app.getString(R.string.job_receipts_uploaded, receipts.done));
            if (receipts.failed > 0) {
                msg.append("\n\n").append(app.getString(R.string.job_receipts_failed, receipts.failed,
                        receipts.error == null ? "" : receipts.error));
            }
            publishFinal(app, kind, BackupJob.Phase.DONE, msg.toString(), null);
            CloudLog.log(app, CloudLog.ACTION_BACKUP,
                    result.outcome == CloudSync.Outcome.UPLOADED ? CloudLog.RESULT_OK : CloudLog.RESULT_UNCHANGED,
                    result.rows, receipts.done > 0 ? receipts.done + " receipt(s) uploaded" : "");
            CloudLog.touchProfile(app);
        } catch (CloudException e) {
            fail(app, kind, CloudLog.ACTION_BACKUP, app.getString(R.string.cloud_backup_failed_message, e.getMessage()));
        }
    }

    private void runRestore(Context app) {
        final BackupJob.Kind kind = BackupJob.Kind.RESTORE;
        final String uid = CloudConfig.currentUid(app);
        if (uid == null) {
            fail(app, kind, CloudLog.ACTION_RESTORE, app.getString(R.string.job_not_signed_in));
            return;
        }
        if (DeviceStateReader.read(app).blocksManualBackup()) {
            fail(app, kind, CloudLog.ACTION_RESTORE, app.getString(R.string.job_airplane));
            return;
        }
        CloudSync.ProgressListener listener = progressListener(app, kind);
        try {
            BackupData data = CloudSync.download(app, listener); // validated exactly like a backup file
            listener.onProgress(CloudSync.STAGE_RESTORE, 0, 0);
            BackupRepository.restore(app, data); // safety copy first, then one all-or-nothing transaction
            AppSettings settings = AppSettings.get(app);
            settings.setCloudBase(uid, data.createdAtMillis); // this device now matches the cloud copy
            settings.setCloudFingerprint(uid, "");
            settings.setLastCloudBackupMillis(data.createdAtMillis);

            ReceiptSync.Result receipts = ReceiptSync.downloadMissing(app, listener);
            BackgroundWork.runNow(app); // catch up recurring items / reminders for the restored rules

            StringBuilder msg = new StringBuilder(app.getString(R.string.job_restore_done, data.totalRows()));
            if (receipts.done > 0) msg.append("\n\n").append(app.getString(R.string.job_receipts_downloaded, receipts.done));
            if (receipts.failed > 0) {
                msg.append("\n\n").append(app.getString(R.string.job_receipts_download_failed, receipts.failed));
            }
            publishFinal(app, kind, BackupJob.Phase.DONE, msg.toString(), null);
            CloudLog.log(app, CloudLog.ACTION_RESTORE, CloudLog.RESULT_OK, data.totalRows(), "");
            CloudLog.touchProfile(app);
        } catch (CloudException | BackupException e) {
            fail(app, kind, CloudLog.ACTION_RESTORE, e.getMessage());
        } catch (IOException | JSONException e) {
            fail(app, kind, CloudLog.ACTION_RESTORE, app.getString(R.string.restore_failed_message, String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ progress + results

    private CloudSync.ProgressListener progressListener(final Context app, final BackupJob.Kind kind) {
        return (stage, done, total) -> {
            BackupJob.Status base = new BackupJob.Status(currentId(), kind, BackupJob.Phase.RUNNING, stage, done, total, null, null);
            BackupJob.publish(base);
            long now = System.currentTimeMillis();
            boolean last = total > 0 && done >= total;
            // The system rate-limits notification updates: at most one every ~0.4 s, but always on a new stage.
            if (stage != lastNotifyStage || last || now - lastNotifyAt >= NOTIFY_INTERVAL_MILLIS) {
                lastNotifyStage = stage;
                lastNotifyAt = now;
                notify(ID_PROGRESS, buildProgress(base));
            }
        };
    }

    private static long currentId() {
        BackupJob.Status s = BackupJob.current();
        return s == null ? 0 : s.id;
    }

    private void fail(Context app, BackupJob.Kind kind, String logAction, String message) {
        publishFinal(app, kind, BackupJob.Phase.FAILED, message == null ? "" : message, null);
        CloudLog.log(app, logAction, CloudLog.RESULT_FAILED, 0, message);
    }

    private void publishFinal(Context app, BackupJob.Kind kind, BackupJob.Phase phase, String message,
                              CloudSync.RemoteInfo remote) {
        long id = currentId();
        BackupJob.Status done = new BackupJob.Status(id, kind, phase, 0, 0, 0, message, remote);
        BackupJob.publish(done);
        // A normal (swipe-away) notification with the outcome, for when the user has left the app.
        int title = phase == BackupJob.Phase.DONE
                ? (kind == BackupJob.Kind.RESTORE ? R.string.job_done_restore : R.string.job_done_backup)
                : phase == BackupJob.Phase.CONFLICT ? R.string.job_conflict_title
                : (kind == BackupJob.Kind.RESTORE ? R.string.job_failed_restore : R.string.job_failed_backup);
        String text = phase == BackupJob.Phase.CONFLICT ? app.getString(R.string.job_conflict_text) : firstLine(message);
        Notification n = new NotificationCompat.Builder(app, NotificationHelper.CHANNEL_BACKUP)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(app.getString(title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(phase == BackupJob.Phase.CONFLICT ? text : message))
                .setContentIntent(openCloudScreen(app))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        notify(ID_RESULT, n);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private Notification buildProgress(BackupJob.Status s) {
        boolean restore = s.kind == BackupJob.Kind.RESTORE;
        int percent = s.percent();
        return new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_BACKUP)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(restore ? R.string.job_title_restore : R.string.job_title_backup))
                .setContentText(BackupJob.stageText(this, s))
                .setProgress(percent < 0 ? 0 : 100, Math.max(0, percent), percent < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openCloudScreen(this))
                .build();
    }

    private static PendingIntent openCloudScreen(Context c) {
        Intent open = new Intent(c, CloudAccountActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void notify(int id, Notification n) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(id, n);
        } catch (SecurityException ignored) {
            // notifications are switched off / permission denied: the job still runs
        }
    }

    private void startInForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ID_PROGRESS, n);
        }
    }

    @SuppressWarnings("deprecation")
    private void finish(int startId) {
        stopForeground(true); // removes the progress notification; the result notification stays
        stopSelf(startId);
    }
}

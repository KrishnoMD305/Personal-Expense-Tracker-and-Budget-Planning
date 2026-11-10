package com.example.expensetracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Stage 6: optional automatic cloud backup, once a day while the device is online.
 *
 * <p>It never overwrites a cloud copy written by another device ({@link CloudSync.Outcome#CONFLICT} is skipped;
 * the user decides in Cloud account), never uploads when nothing changed, and does nothing when the switch is
 * off, nobody is signed in or Firebase is not set up.
 *
 * <p>Stage 7: it also <b>respects the device state</b> that the dashboard banner shows. In airplane mode it
 * could not upload anyway, and on a low battery a background upload is the first thing to postpone, so the job
 * is deferred (WorkManager's own constraints hold it back, and the check in {@link #doWork} covers the moments
 * in between). A backup the user starts by hand is not affected.
 */
public class CloudSyncWorker extends Worker {
    static final String UNIQUE_NAME = "cloud_auto_backup";
    /** How often a deferred run is retried (with WorkManager's growing delay) before waiting for the next day. */
    static final int MAX_DEFERRALS = 5;

    public CloudSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        if (!AppSettings.get(app).cloudAutoBackup() || CloudConfig.currentUid(app) == null) {
            return Result.success();
        }
        // Stage 7: airplane mode / low battery -> defer.
        DeviceStatus device = DeviceStateReader.read(app);
        if (device.deferAutoBackup()) {
            if (getRunAttemptCount() == 0) {
                CloudLog.log(app, CloudLog.ACTION_AUTO_BACKUP, CloudLog.RESULT_DEFERRED,
                        0, device.airplaneMode ? "Airplane mode is on" : "Battery is low");
            }
            return getRunAttemptCount() < MAX_DEFERRALS ? Result.retry() : Result.success();
        }
        // Do not run next to a manual backup / restore that the foreground service is performing.
        if (BackupJob.isRunning()) return Result.retry();
        try {
            ReceiptSync.Result receipts = ReceiptSync.uploadPending(app, null); // photos first, see BackupService
            CloudSync.UploadResult result = CloudSync.upload(app, false);
            if (result.outcome == CloudSync.Outcome.UPLOADED) {
                CloudLog.log(app, CloudLog.ACTION_AUTO_BACKUP, CloudLog.RESULT_OK, result.rows,
                        receipts.done > 0 ? receipts.done + " receipt(s) uploaded" : "");
            } else if (result.outcome == CloudSync.Outcome.CONFLICT) {
                CloudLog.log(app, CloudLog.ACTION_AUTO_BACKUP, CloudLog.RESULT_CONFLICT, 0,
                        "Another device wrote the cloud copy; open Cloud account to choose");
            }
            return Result.success();
        } catch (CloudException e) {
            // Transient problems (no network) are retried a few times; anything else waits for the next daily run.
            if (!e.retryable) CloudLog.log(app, CloudLog.ACTION_AUTO_BACKUP, CloudLog.RESULT_FAILED, 0, e.getMessage());
            return e.retryable && getRunAttemptCount() < 3 ? Result.retry() : Result.success();
        } catch (RuntimeException e) {
            return Result.success();
        }
    }

    /** Schedules or cancels the daily job to match the user's switch. Safe to call any number of times. */
    static void apply(Context context) {
        try {
            WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
            if (AppSettings.get(context).cloudAutoBackup()) {
                // Stage 7: needs a network AND a battery that is not low (WorkManager watches both).
                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build();
                // UPDATE (WorkManager 2.8+) replaces the Stage 6 job's constraints without restarting its schedule.
                wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                        new PeriodicWorkRequest.Builder(CloudSyncWorker.class, 24, TimeUnit.HOURS)
                                .setConstraints(constraints).build());
            } else {
                wm.cancelUniqueWork(UNIQUE_NAME);
            }
        } catch (RuntimeException ignored) {
            // Scheduling must never crash the app; it is applied again on the next launch.
        }
    }
}

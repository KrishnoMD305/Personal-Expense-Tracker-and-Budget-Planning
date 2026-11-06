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
 */
public class CloudSyncWorker extends Worker {
    static final String UNIQUE_NAME = "cloud_auto_backup";

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
        try {
            CloudSync.upload(app, false);
            return Result.success();
        } catch (CloudException e) {
            // Transient problems (no network) are retried a few times; anything else waits for the next daily run.
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
                Constraints online = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
                wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP,
                        new PeriodicWorkRequest.Builder(CloudSyncWorker.class, 24, TimeUnit.HOURS)
                                .setConstraints(online).build());
            } else {
                wm.cancelUniqueWork(UNIQUE_NAME);
            }
        } catch (RuntimeException ignored) {
            // Scheduling must never crash the app; it is applied again on the next launch.
        }
    }
}

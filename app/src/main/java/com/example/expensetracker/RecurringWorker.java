package com.example.expensetracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** WorkManager job: creates the transactions of recurring rules that are due. Idempotent. */
public class RecurringWorker extends Worker {
    public RecurringWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            RecurringRepository.processDue(AppDatabase.getInstance(getApplicationContext()),
                    System.currentTimeMillis());
            return Result.success();
        } catch (RuntimeException e) {
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}

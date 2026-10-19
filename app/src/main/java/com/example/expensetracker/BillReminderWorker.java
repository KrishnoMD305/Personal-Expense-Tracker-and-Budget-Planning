package com.example.expensetracker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BillReminderWorker extends Worker {
    public BillReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    @NonNull
    @Override
    public Result doWork() {
        try {
            BillReminders.run(getApplicationContext(), System.currentTimeMillis());
            return Result.success();
        } catch (RuntimeException e) {
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}

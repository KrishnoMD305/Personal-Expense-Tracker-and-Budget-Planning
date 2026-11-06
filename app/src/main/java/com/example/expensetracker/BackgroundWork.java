package com.example.expensetracker;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Registers the Stage 4 background jobs. Every job is unique-named, so calling this again is harmless. */
public final class BackgroundWork {
    static final String PERIODIC_RECURRING = "recurring_transactions";
    static final String PERIODIC_REMINDERS = "bill_reminders";
    static final String NOW_RECURRING = "recurring_transactions_now";
    static final String NOW_REMINDERS = "bill_reminders_now";
    /** Local hour at which the daily reminder job first runs (Android may shift it a little). */
    static final int REMINDER_HOUR = 9;

    private BackgroundWork() {}

    public static void scheduleAll(Context context) {
        try {
            WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
            wm.enqueueUniquePeriodicWork(PERIODIC_RECURRING, ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(RecurringWorker.class, 12, TimeUnit.HOURS).build());
            long delay = PlanDates.nextHourOfDay(System.currentTimeMillis(), REMINDER_HOUR)
                    - System.currentTimeMillis();
            wm.enqueueUniquePeriodicWork(PERIODIC_REMINDERS, ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(BillReminderWorker.class, 24, TimeUnit.HOURS)
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build());
            runNow(context);
        } catch (RuntimeException ignored) {
            // Scheduling must never crash the app; the app-start catch-up below still runs next launch.
        }
    }

    /** One immediate run of both jobs (app start / returning to the app). KEEP: ignored if one is already queued. */
    public static void runNow(Context context) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        wm.enqueueUniqueWork(NOW_RECURRING, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(RecurringWorker.class).build());
        wm.enqueueUniqueWork(NOW_REMINDERS, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(BillReminderWorker.class).build());
    }
}

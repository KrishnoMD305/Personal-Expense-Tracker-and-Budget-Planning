package com.example.expensetracker;

import android.app.Application;

/** Application class: creates the notification channel, registers the WorkManager jobs and the app-lock tracking. */
public class ExpenseTrackerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLock.install(this);
        NotificationHelper.ensureChannel(this);
        BackgroundWork.scheduleAll(this);
        CloudSyncWorker.apply(this); // Stage 6: daily cloud backup, only when the user switched it on
    }
}

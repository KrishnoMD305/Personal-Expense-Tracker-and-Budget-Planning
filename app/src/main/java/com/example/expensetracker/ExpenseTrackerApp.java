package com.example.expensetracker;

import android.app.Application;
public class ExpenseTrackerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannel(this);
        BackgroundWork.scheduleAll(this);
    }
}

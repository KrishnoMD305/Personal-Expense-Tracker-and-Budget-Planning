package com.example.expensetracker;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Stage 5: decides when the optional PIN / biometric lock must be shown.
 *
 * <p>The app is locked after a cold start and every time it comes back from the background. Screen
 * rotation is not "going to the background", and neither is a short trip to a system screen the app
 * started itself (the file picker, the notification settings): those call {@link #beginExternalFlow()}.
 * All state lives on the main thread.
 */
final class AppLock {
    /** A system screen the app opened may keep the app in the background for this long without re-locking. */
    private static final long EXTERNAL_FLOW_MS = 5 * 60_000L;

    private static boolean unlocked = false;
    private static boolean lockScreenShowing = false;
    private static boolean wasInBackground = false;
    private static int startedCount = 0;
    private static long externalFlowUntil = 0L;
    private static boolean installed = false;

    private AppLock() {}

    /** Registers the foreground/background tracking. Call once from {@link ExpenseTrackerApp#onCreate()}. */
    static void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                if (startedCount++ == 0 && wasInBackground) {
                    wasInBackground = false;
                    long now = SystemClock.elapsedRealtime();
                    if (now <= externalFlowUntil) {
                        externalFlowUntil = 0L; // returning from our own file picker / settings page
                    } else {
                        unlocked = false;
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (--startedCount <= 0) {
                    startedCount = 0;
                    if (!activity.isChangingConfigurations()) wasInBackground = true;
                }
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    /** Call right before starting a system screen (document picker, settings) so coming back does not re-lock. */
    static void beginExternalFlow() {
        externalFlowUntil = SystemClock.elapsedRealtime() + EXTERNAL_FLOW_MS;
    }

    static boolean isLocked(Context context) {
        return AppSettings.get(context).lockEnabled() && !unlocked;
    }

    /** Called by {@link BaseActivity}: shows the lock screen on top of {@code activity} when it is needed. */
    static void showIfNeeded(Activity activity) {
        if (lockScreenShowing || !isLocked(activity)) return;
        lockScreenShowing = true;
        activity.startActivity(new Intent(activity, LockActivity.class));
        activity.overridePendingTransition(0, 0);
    }

    static void onLockScreenClosed() {
        lockScreenShowing = false;
    }

    /** Called after a successful PIN / biometric check, and right after the user sets up the lock (they are in the app). */
    static void markUnlocked() {
        unlocked = true;
    }
}

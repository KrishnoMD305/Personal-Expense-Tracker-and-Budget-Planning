package com.example.expensetracker;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;

/**
 * Stage 7: reads the current airplane-mode and battery state from the system. Used to initialise the dashboard
 * banner (the broadcasts only report <em>changes</em>) and by {@link CloudSyncWorker} / {@link BackupService},
 * which run without a screen and so have no receiver of their own.
 */
final class DeviceStateReader {
    private DeviceStateReader() {}

    static DeviceStatus read(Context context) {
        return new DeviceStatus(isAirplaneModeOn(context), isBatteryLow(context));
    }

    static boolean isAirplaneModeOn(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** ACTION_BATTERY_CHANGED is a "sticky" broadcast: registering with a null receiver just returns the last one. */
    static boolean isBatteryLow(Context context) {
        try {
            Intent battery = context.getApplicationContext()
                    .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            boolean charging = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            return DeviceStatus.isLowReading(level, scale, charging);
        } catch (RuntimeException e) {
            return false;
        }
    }
}

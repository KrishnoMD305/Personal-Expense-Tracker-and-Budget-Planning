package com.example.expensetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * Stage 7: listens for airplane-mode and battery-low / battery-okay broadcasts and keeps a {@link DeviceStatus}.
 *
 * <p>These three actions cannot be declared in the manifest on modern Android (implicit broadcasts), so the
 * receiver is registered <b>dynamically</b>: {@link MainActivity} calls {@link #register} in {@code onStart} and
 * {@link #unregister} in {@code onStop}. While the dashboard is not visible nobody needs the banner, and
 * {@link CloudSyncWorker} asks {@link DeviceStateReader} directly instead.
 */
final class DeviceStateReceiver extends BroadcastReceiver {
    /** Called on the main thread whenever the state may have changed. */
    interface Listener {
        void onDeviceStatus(DeviceStatus status);
    }

    private final Listener listener;
    private DeviceStatus status;
    private boolean registered;

    DeviceStateReceiver(DeviceStatus initial, Listener listener) {
        this.status = initial;
        this.listener = listener;
    }

    static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_BATTERY_OKAY);
        return f;
    }

    /** registerReceiver(): re-reads the real state first, because broadcasts were not delivered while stopped. */
    void register(Context context) {
        if (registered) return;
        status = DeviceStateReader.read(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Only system broadcasts are expected, so nothing outside the app may send to this receiver.
            context.registerReceiver(this, filter(), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(this, filter());
        }
        registered = true;
        listener.onDeviceStatus(status);
    }

    /** unregisterReceiver(): safe to call twice. */
    void unregister(Context context) {
        if (!registered) return;
        registered = false;
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException ignored) {
            // not registered any more
        }
    }

    DeviceStatus status() {
        return status;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                // The "state" extra is the new airplane-mode value; fall back to reading the setting.
                status = status.withAirplane(intent.hasExtra("state")
                        ? intent.getBooleanExtra("state", false) : DeviceStateReader.isAirplaneModeOn(context));
                break;
            case Intent.ACTION_BATTERY_LOW:
                status = status.withBatteryLow(true);
                break;
            case Intent.ACTION_BATTERY_OKAY:
                status = status.withBatteryLow(false);
                break;
            default:
                return;
        }
        listener.onDeviceStatus(status);
    }
}

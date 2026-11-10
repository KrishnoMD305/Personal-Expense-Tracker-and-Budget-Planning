package com.example.expensetracker;

/**
 * Stage 7: what the device is doing that matters to cloud backup - airplane mode and a low battery.
 * Immutable and free of Android classes, so the rules below are unit-tested on the JVM.
 */
final class DeviceStatus {
    /** The banner the dashboard shows. */
    enum Banner { NONE, OFFLINE, BATTERY_LOW, OFFLINE_AND_BATTERY_LOW }

    /** Battery percentage at or below which an unplugged device counts as "low" when no broadcast told us. */
    static final int LOW_BATTERY_PERCENT = 15;

    final boolean airplaneMode;
    final boolean batteryLow;

    DeviceStatus(boolean airplaneMode, boolean batteryLow) {
        this.airplaneMode = airplaneMode;
        this.batteryLow = batteryLow;
    }

    DeviceStatus withAirplane(boolean on) {
        return new DeviceStatus(on, batteryLow);
    }

    DeviceStatus withBatteryLow(boolean low) {
        return new DeviceStatus(airplaneMode, low);
    }

    Banner banner() {
        if (airplaneMode && batteryLow) return Banner.OFFLINE_AND_BATTERY_LOW;
        if (airplaneMode) return Banner.OFFLINE;
        if (batteryLow) return Banner.BATTERY_LOW;
        return Banner.NONE;
    }

    /**
     * The daily automatic backup waits when offline (it could not upload anyway) or when the battery is low
     * (a background upload is the first thing to postpone).
     */
    boolean deferAutoBackup() {
        return airplaneMode || batteryLow;
    }

    /** A backup the user starts by hand still runs on a low battery (their decision) but not in airplane mode. */
    boolean blocksManualBackup() {
        return airplaneMode;
    }

    /** True when a battery reading says "low": at or below the threshold and not charging. */
    static boolean isLowReading(int level, int scale, boolean charging) {
        if (scale <= 0 || level < 0 || charging) return false;
        return level * 100 / scale <= LOW_BATTERY_PERCENT;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DeviceStatus)) return false;
        DeviceStatus d = (DeviceStatus) o;
        return d.airplaneMode == airplaneMode && d.batteryLow == batteryLow;
    }

    @Override
    public int hashCode() {
        return (airplaneMode ? 2 : 0) + (batteryLow ? 1 : 0);
    }
}

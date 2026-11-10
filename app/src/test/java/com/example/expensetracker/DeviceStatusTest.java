package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Stage 7: the rules behind the dashboard banner and the CloudSyncWorker deferral. */
public class DeviceStatusTest {
    @Test
    public void bannerFollowsAirplaneAndBattery() {
        assertEquals(DeviceStatus.Banner.NONE, new DeviceStatus(false, false).banner());
        assertEquals(DeviceStatus.Banner.OFFLINE, new DeviceStatus(true, false).banner());
        assertEquals(DeviceStatus.Banner.BATTERY_LOW, new DeviceStatus(false, true).banner());
        assertEquals(DeviceStatus.Banner.OFFLINE_AND_BATTERY_LOW, new DeviceStatus(true, true).banner());
    }

    @Test
    public void autoBackupIsDeferredWhenOfflineOrBatteryLow() {
        assertFalse(new DeviceStatus(false, false).deferAutoBackup());
        assertTrue(new DeviceStatus(true, false).deferAutoBackup());
        assertTrue(new DeviceStatus(false, true).deferAutoBackup());
    }

    @Test
    public void manualBackupOnlyBlockedByAirplaneMode() {
        assertFalse(new DeviceStatus(false, true).blocksManualBackup());
        assertTrue(new DeviceStatus(true, false).blocksManualBackup());
    }

    @Test
    public void broadcastsUpdateOneFlagAtATime() {
        DeviceStatus s = new DeviceStatus(false, false).withAirplane(true).withBatteryLow(true);
        assertEquals(new DeviceStatus(true, true), s);
        assertEquals(new DeviceStatus(true, false), s.withBatteryLow(false)); // ACTION_BATTERY_OKAY
        assertEquals(new DeviceStatus(false, true), s.withAirplane(false));
    }

    @Test
    public void batteryReadingIsLowOnlyWhenUnpluggedAtOrBelowThreshold() {
        assertTrue(DeviceStatus.isLowReading(15, 100, false));
        assertTrue(DeviceStatus.isLowReading(7, 50, false));      // 14 %
        assertFalse(DeviceStatus.isLowReading(16, 100, false));
        assertFalse(DeviceStatus.isLowReading(5, 100, true));     // charging
        assertFalse(DeviceStatus.isLowReading(-1, 100, false));   // unknown level
        assertFalse(DeviceStatus.isLowReading(10, 0, false));     // bad scale
    }
}

package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class MonthUtilsTest {
    private TimeZone original;
    private Locale originalLocale;

    @Before
    public void setUp() {
        original = TimeZone.getDefault();
        originalLocale = Locale.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(original);
        Locale.setDefault(originalLocale);
    }

    private static long at(int y, int m1, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m1 - 1, d, h, min, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void keyOfUsesLocalCalendarMonth() {
        assertEquals("2026-10", MonthUtils.keyOf(at(2026, 10, 17, 8, 50)));
        assertEquals("2026-10", MonthUtils.keyOf(at(2026, 10, 1, 0, 0)));
        assertEquals("2026-10", MonthUtils.keyOf(at(2026, 10, 31, 23, 59)));
        assertEquals("2026-11", MonthUtils.keyOf(at(2026, 11, 1, 0, 0)));
    }

    @Test
    public void rangeIsHalfOpenAndCoversWholeMonth() {
        long start = MonthUtils.startMillis("2026-10");
        long end = MonthUtils.endMillis("2026-10");
        assertEquals(at(2026, 10, 1, 0, 0), start);
        assertEquals(at(2026, 11, 1, 0, 0), end);
        assertTrue(at(2026, 10, 31, 23, 59) < end);
        assertEquals("2026-10", MonthUtils.keyOf(start));
        assertEquals("2026-11", MonthUtils.keyOf(end));
    }

    @Test
    public void shiftCrossesYearBoundaries() {
        assertEquals("2027-01", MonthUtils.shift("2026-12", 1));
        assertEquals("2025-12", MonthUtils.shift("2026-01", -1));
        assertEquals("2026-10", MonthUtils.shift("2026-10", 0));
        assertEquals("2028-02", MonthUtils.shift("2026-02", 24));
    }

    @Test
    public void februaryLeapYearLengths() {
        long feb2028 = MonthUtils.endMillis("2028-02") - MonthUtils.startMillis("2028-02");
        long feb2027 = MonthUtils.endMillis("2027-02") - MonthUtils.startMillis("2027-02");
        assertEquals(29L * 24 * 3600 * 1000, feb2028);
        assertEquals(28L * 24 * 3600 * 1000, feb2027);
    }

    @Test
    public void validation() {
        assertTrue(MonthUtils.isValidKey("2026-10"));
        assertFalse(MonthUtils.isValidKey(null));
        assertFalse(MonthUtils.isValidKey(""));
        assertFalse(MonthUtils.isValidKey("2026-13"));
        assertFalse(MonthUtils.isValidKey("2026-00"));
        assertFalse(MonthUtils.isValidKey("2026/10"));
        assertFalse(MonthUtils.isValidKey("26-10"));
        assertFalse(MonthUtils.isValidKey("abcd-10"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidKeyThrows() {
        MonthUtils.startMillis("nonsense");
    }

    @Test
    public void label() {
        assertEquals("October 2026", MonthUtils.label("2026-10"));
    }

    @Test
    public void keysAreAsciiEvenInDigitLocales() {
        Locale.setDefault(new Locale("bn", "BD"));
        assertEquals("2026-10", MonthUtils.keyOf(at(2026, 10, 5, 12, 0)));
        assertEquals("2026-11", MonthUtils.shift("2026-10", 1));
    }
}

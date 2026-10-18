package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class ReportPeriodTest {
    private TimeZone originalZone;
    private Locale originalLocale;

    @Before
    public void setUp() {
        originalZone = TimeZone.getDefault();
        originalLocale = Locale.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalZone);
        Locale.setDefault(originalLocale);
    }

    static long at(int y, int m1, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m1 - 1, d, h, min, 0);
        return c.getTimeInMillis();
    }

    static long day(int y, int m1, int d) {
        return at(y, m1, d, 0, 0);
    }

    @Test
    public void monthPeriodCoversExactlyThatMonth() {
        ReportPeriod p = ReportPeriod.month("2026-10");
        assertEquals(ReportPeriod.Type.MONTH, p.type);
        assertEquals(day(2026, 10, 1), p.startMillis);
        assertEquals(day(2026, 11, 1), p.endMillis);
        assertEquals(31, p.dayCount());
        assertEquals("2026-10", p.firstMonthKey());
        assertEquals("2026-10", p.lastMonthKey());
        assertTrue(p.contains(day(2026, 10, 1)));
        assertTrue(p.contains(at(2026, 10, 31, 23, 59)));
        assertFalse(p.contains(day(2026, 11, 1)));
        assertFalse(p.contains(at(2026, 9, 30, 23, 59)));
    }

    @Test
    public void dayCountHandlesLeapYears() {
        assertEquals(29, ReportPeriod.month("2028-02").dayCount());
        assertEquals(28, ReportPeriod.month("2027-02").dayCount());
        assertEquals(30, ReportPeriod.month("2026-09").dayCount());
    }

    @Test
    public void customRangeIncludesTheLastDay() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 10, 10), at(2026, 10, 12, 15, 30));
        assertEquals(ReportPeriod.Type.CUSTOM, p.type);
        assertEquals(day(2026, 10, 10), p.startMillis);
        assertEquals(day(2026, 10, 13), p.endMillis); // exclusive: start of the day after the last day
        assertEquals(3, p.dayCount());
        assertTrue(p.contains(day(2026, 10, 12)));
        assertTrue(p.contains(at(2026, 10, 12, 23, 59)));
        assertFalse(p.contains(day(2026, 10, 13)));
        assertNull(p.monthKey);
    }

    @Test
    public void singleDayRangeIsOneDay() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 10, 5), day(2026, 10, 5));
        assertEquals(1, p.dayCount());
        assertEquals(day(2026, 10, 6), p.endMillis);
    }

    @Test(expected = IllegalArgumentException.class)
    public void reversedRangeIsRejected() {
        ReportPeriod.custom(day(2026, 10, 12), day(2026, 10, 11));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rangeBefore1970IsRejected() {
        ReportPeriod.custom(day(1969, 6, 1), day(1969, 6, 2));
    }

    @Test
    public void previousOfMonthIsPreviousCalendarMonth() {
        ReportPeriod p = ReportPeriod.month("2026-01").previous();
        assertEquals(ReportPeriod.Type.MONTH, p.type);
        assertEquals("2025-12", p.monthKey);
        assertEquals(day(2025, 12, 1), p.startMillis);
        assertEquals(day(2026, 1, 1), p.endMillis);
    }

    @Test
    public void previousOfCustomRangeIsEqualLengthBlockBefore() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 10, 10), day(2026, 10, 12)).previous();
        assertEquals(day(2026, 10, 7), p.startMillis);
        assertEquals(day(2026, 10, 10), p.endMillis);
        assertEquals(3, p.dayCount());
    }

    @Test
    public void previousOfCustomRangeCrossesMonthAndYear() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 1, 1), day(2026, 1, 31)).previous();
        assertEquals(day(2025, 12, 1), p.startMillis);
        assertEquals(day(2026, 1, 1), p.endMillis);
        assertEquals(31, p.dayCount());
    }

    @Test
    public void wholeCalendarMonthDetection() {
        assertTrue(ReportPeriod.month("2026-10").isWholeCalendarMonths());
        assertTrue(ReportPeriod.custom(day(2026, 10, 1), day(2026, 10, 31)).isWholeCalendarMonths());
        assertTrue(ReportPeriod.custom(day(2026, 10, 1), day(2026, 12, 31)).isWholeCalendarMonths());
        assertTrue(ReportPeriod.custom(day(2028, 2, 1), day(2028, 2, 29)).isWholeCalendarMonths());
        assertFalse(ReportPeriod.custom(day(2026, 10, 2), day(2026, 10, 31)).isWholeCalendarMonths());
        assertFalse(ReportPeriod.custom(day(2026, 10, 1), day(2026, 10, 30)).isWholeCalendarMonths());
        assertFalse(ReportPeriod.custom(day(2028, 2, 1), day(2028, 2, 28)).isWholeCalendarMonths());
    }

    @Test
    public void monthKeysOfCustomRange() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 8, 20), day(2026, 11, 3));
        assertEquals("2026-08", p.firstMonthKey());
        assertEquals("2026-11", p.lastMonthKey());
    }

    @Test
    public void equalityIsByTypeAndBounds() {
        assertEquals(ReportPeriod.month("2026-10"), ReportPeriod.month("2026-10"));
        assertFalse(ReportPeriod.month("2026-10").equals(ReportPeriod.month("2026-09")));
        // same bounds but different type are not equal
        assertFalse(ReportPeriod.month("2026-10")
                .equals(ReportPeriod.custom(day(2026, 10, 1), day(2026, 10, 31))));
        assertEquals(ReportPeriod.month("2026-10").hashCode(), ReportPeriod.month("2026-10").hashCode());
    }

    @Test
    public void dayCountIsExactAcrossADaylightSavingChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        // US clocks went forward on 8 March 2026 (a 23-hour day) and back on 1 Nov 2026 (25 hours).
        assertEquals(31, ReportPeriod.month("2026-03").dayCount());
        assertEquals(30, ReportPeriod.month("2026-11").dayCount());
        assertEquals(1, ReportPeriod.custom(day(2026, 3, 8), day(2026, 3, 8)).dayCount());
        assertEquals(1, ReportPeriod.custom(day(2026, 11, 1), day(2026, 11, 1)).dayCount());
        assertEquals(10, ReportPeriod.custom(day(2026, 3, 1), day(2026, 3, 10)).dayCount());
    }

    // ---- Stage 3 additions to MonthUtils

    @Test
    public void monthUtilsShortLabelAndMonthsBetween() {
        assertEquals("Oct 26", MonthUtils.shortLabel("2026-10"));
        assertEquals(0, MonthUtils.monthsBetween("2026-10", "2026-10"));
        assertEquals(5, MonthUtils.monthsBetween("2026-10", "2027-03"));
        assertEquals(-12, MonthUtils.monthsBetween("2026-10", "2025-10"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void monthsBetweenRejectsInvalidKeys() {
        MonthUtils.monthsBetween("2026-10", "oops");
    }

    @Test
    public void monthUtilsStartOfDayAndAddDays() {
        assertEquals(day(2026, 10, 17), MonthUtils.startOfDay(at(2026, 10, 17, 18, 45)));
        assertEquals(day(2026, 10, 17), MonthUtils.startOfDay(day(2026, 10, 17)));
        assertEquals(day(2026, 11, 2), MonthUtils.addDays(day(2026, 10, 30), 3));
        assertEquals(day(2025, 12, 31), MonthUtils.addDays(day(2026, 1, 1), -1));
    }

    @Test
    public void addDaysKeepsLocalMidnightAcrossDaylightSaving() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        long before = day(2026, 3, 7);
        long after = MonthUtils.addDays(before, 2);
        assertEquals(day(2026, 3, 9), after);
        assertEquals(after, MonthUtils.startOfDay(after));
    }
}

package com.example.expensetracker;

import static com.example.expensetracker.TestDays.d;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class PlanDatesTest {
    private TimeZone saved;

    @Before
    public void setUp() {
        saved = TimeZone.getDefault();
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(saved);
    }

    @Test
    public void daysBetweenCountsCalendarDaysAcrossDaylightSaving() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        assertEquals(3, PlanDates.daysBetween(d(2026, 3, 7), d(2026, 3, 10))); // a 23-hour day inside
        assertEquals(3, PlanDates.daysBetween(d(2026, 10, 31), d(2026, 11, 3))); // a 25-hour day inside
        assertEquals(-2, PlanDates.daysBetween(d(2026, 3, 10), d(2026, 3, 8)));
        assertEquals(0, PlanDates.daysBetween(d(2026, 3, 9), d(2026, 3, 9)));
    }

    @Test
    public void nextHourOfDayIsStrictlyInTheFuture() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        long eightAm = d(2026, 10, 17) + 8 * 3_600_000L;
        long nine = PlanDates.nextHourOfDay(eightAm, 9);
        assertEquals(d(2026, 10, 17) + 9 * 3_600_000L, nine);
        long exactlyNine = d(2026, 10, 17) + 9 * 3_600_000L;
        assertEquals(d(2026, 10, 18) + 9 * 3_600_000L, PlanDates.nextHourOfDay(exactlyNine, 9));
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(PlanDates.nextHourOfDay(d(2026, 10, 17) + 20 * 3_600_000L, 9));
        assertEquals(18, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, c.get(Calendar.HOUR_OF_DAY));
    }
}

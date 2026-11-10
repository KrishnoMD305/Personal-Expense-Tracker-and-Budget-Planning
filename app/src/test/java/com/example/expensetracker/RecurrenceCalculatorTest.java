package com.example.expensetracker;

import static com.example.expensetracker.TestDays.d;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class RecurrenceCalculatorTest {
    private TimeZone saved;

    @Before
    public void setUp() {
        saved = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(saved);
    }

    @Test
    public void dailyOccurrencesAreInclusiveOnBothEnds() {
        List<Long> o = RecurrenceCalculator.occurrencesBetween(d(2026, 10, 1), 0,
                RecurrenceCalculator.DAILY, d(2026, 10, 3), d(2026, 10, 5), 100);
        assertEquals(3, o.size());
        assertEquals(d(2026, 10, 3), (long) o.get(0));
        assertEquals(d(2026, 10, 5), (long) o.get(2));
    }

    @Test
    public void weeklyStepsSevenDays() {
        List<Long> o = RecurrenceCalculator.occurrencesBetween(d(2026, 10, 1), 0,
                RecurrenceCalculator.WEEKLY, d(2026, 10, 1), d(2026, 10, 31), 100);
        assertEquals(5, o.size()); // 1, 8, 15, 22, 29
        assertEquals(d(2026, 10, 8), (long) o.get(1));
        assertEquals(d(2026, 10, 29), (long) o.get(4));
    }

    @Test
    public void monthlyFromThe31stClampsButNeverDrifts() {
        long start = d(2026, 1, 31);
        assertEquals(d(2026, 1, 31), RecurrenceCalculator.occurrence(start, RecurrenceCalculator.MONTHLY, 0));
        assertEquals(d(2026, 2, 28), RecurrenceCalculator.occurrence(start, RecurrenceCalculator.MONTHLY, 1));
        assertEquals(d(2026, 3, 31), RecurrenceCalculator.occurrence(start, RecurrenceCalculator.MONTHLY, 2));
        assertEquals(d(2026, 4, 30), RecurrenceCalculator.occurrence(start, RecurrenceCalculator.MONTHLY, 3));
        assertEquals(d(2026, 5, 31), RecurrenceCalculator.occurrence(start, RecurrenceCalculator.MONTHLY, 4));
    }

    @Test
    public void monthlyLeapYearFebruary() {
        assertEquals(d(2028, 2, 29), RecurrenceCalculator.occurrence(d(2028, 1, 31), RecurrenceCalculator.MONTHLY, 1));
        assertEquals(d(2027, 2, 28), RecurrenceCalculator.occurrence(d(2027, 1, 30), RecurrenceCalculator.MONTHLY, 1));
    }

    @Test
    public void monthlyAcrossYearEnd() {
        List<Long> o = RecurrenceCalculator.occurrencesBetween(d(2026, 11, 15), 0,
                RecurrenceCalculator.MONTHLY, d(2026, 11, 1), d(2027, 2, 28), 100);
        assertEquals(4, o.size());
        assertEquals(d(2027, 1, 15), (long) o.get(2));
    }

    @Test
    public void endDateIsInclusiveAndStopsTheSchedule() {
        List<Long> o = RecurrenceCalculator.occurrencesBetween(d(2026, 10, 1), d(2026, 10, 3),
                RecurrenceCalculator.DAILY, d(2026, 10, 1), d(2026, 12, 31), 100);
        assertEquals(3, o.size());
        assertEquals(-1, RecurrenceCalculator.nextOccurrence(d(2026, 10, 1), d(2026, 10, 3),
                RecurrenceCalculator.DAILY, d(2026, 10, 4)));
    }

    @Test
    public void fromBeforeStartStartsAtTheStartDate() {
        List<Long> o = RecurrenceCalculator.occurrencesBetween(d(2026, 10, 10), 0,
                RecurrenceCalculator.DAILY, d(2026, 9, 1), d(2026, 10, 11), 100);
        assertEquals(2, o.size());
        assertEquals(d(2026, 10, 10), (long) o.get(0));
    }

    @Test
    public void nothingBeforeTheStartDate() {
        assertTrue(RecurrenceCalculator.occurrencesBetween(d(2026, 10, 10), 0,
                RecurrenceCalculator.WEEKLY, d(2026, 9, 1), d(2026, 10, 9), 100).isEmpty());
    }

    @Test
    public void limitIsRespected() {
        assertEquals(5, RecurrenceCalculator.occurrencesBetween(d(2026, 1, 1), 0,
                RecurrenceCalculator.DAILY, d(2026, 1, 1), d(2026, 12, 31), 5).size());
    }

    @Test
    public void longRunningRuleFindsTheRightDayQuickly() {
        List<Long> o = RecurrenceCalculator.occurrencesBetween(d(2016, 3, 31), 0,
                RecurrenceCalculator.MONTHLY, d(2026, 10, 17), d(2026, 11, 30), 10);
        assertEquals(2, o.size()); // Oct 31 and Nov 30 (clamped from the 31st)
        assertEquals(d(2026, 10, 31), (long) o.get(0));
        assertEquals(d(2026, 11, 30), (long) o.get(1));

        List<Long> daily = RecurrenceCalculator.occurrencesBetween(d(2016, 3, 31), 0,
                RecurrenceCalculator.DAILY, d(2026, 10, 17), d(2026, 10, 17), 10);
        assertEquals(1, daily.size());
        assertEquals(d(2026, 10, 17), (long) daily.get(0));
    }

    @Test
    public void nextOccurrenceIncludesFromItself() {
        assertEquals(d(2026, 10, 8), RecurrenceCalculator.nextOccurrence(d(2026, 10, 1), 0,
                RecurrenceCalculator.WEEKLY, d(2026, 10, 8)));
        assertEquals(d(2026, 10, 15), RecurrenceCalculator.nextOccurrence(d(2026, 10, 1), 0,
                RecurrenceCalculator.WEEKLY, d(2026, 10, 9)));
    }

    @Test
    public void occurrencesStayAtMidnightAcrossDaylightSavingChanges() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        long start = d(2026, 3, 7); // clocks go forward on 8 March 2026
        for (int k = 0; k < 5; k++) {
            long occ = RecurrenceCalculator.occurrence(start, RecurrenceCalculator.DAILY, k);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(occ);
            assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
            assertEquals(7 + k, c.get(Calendar.DAY_OF_MONTH));
        }
        List<Long> weekly = RecurrenceCalculator.occurrencesBetween(start, 0, RecurrenceCalculator.WEEKLY,
                d(2026, 3, 1), d(2026, 4, 30), 100);
        assertEquals(d(2026, 3, 14), (long) weekly.get(1));
        assertEquals(d(2026, 3, 21), (long) weekly.get(2));
    }

    @Test
    public void invalidInputIsRejected() {
        try {
            RecurrenceCalculator.occurrence(d(2026, 1, 1), "YEARLY", 0);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            RecurrenceCalculator.occurrence(d(2026, 1, 1), RecurrenceCalculator.DAILY, -1);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}

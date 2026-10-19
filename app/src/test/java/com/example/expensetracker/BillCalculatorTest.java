package com.example.expensetracker;

import static com.example.expensetracker.TestDays.bill;
import static com.example.expensetracker.TestDays.d;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.TimeZone;

public class BillCalculatorTest {
    private long TODAY;
    private TimeZone saved;

    @Before
    public void setUp() {
        saved = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        TODAY = d(2026, 10, 17);
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(saved);
    }

    @Test
    public void classifiesBills() {
        assertEquals(BillState.OVERDUE, BillCalculator.state(bill(d(2026, 10, 16), false, 1), TODAY));
        assertEquals(BillState.DUE_TODAY, BillCalculator.state(bill(TODAY, false, 1), TODAY));
        assertEquals(BillState.UPCOMING, BillCalculator.state(bill(d(2026, 10, 18), false, 1), TODAY));
        assertEquals(BillState.PAID, BillCalculator.state(bill(d(2026, 1, 1), true, 1), TODAY));
    }

    @Test
    public void daysUntilDueIsNegativeWhenOverdue() {
        assertEquals(-3, BillCalculator.daysUntilDue(bill(d(2026, 10, 14), false, 1), TODAY));
        assertEquals(0, BillCalculator.daysUntilDue(bill(TODAY, false, 1), TODAY));
        assertEquals(5, BillCalculator.daysUntilDue(bill(d(2026, 10, 22), false, 1), TODAY));
    }

    @Test
    public void dueTimeOfDayDoesNotChangeTheDay() {
        assertEquals(BillState.DUE_TODAY, BillCalculator.state(bill(TODAY + 13 * 3_600_000L, false, 1), TODAY));
    }

    @Test
    public void reminderStartsOnTheLeadDay() {
        Bill b = bill(d(2026, 10, 20), false, 1);
        b.reminderDaysBefore = 3;
        assertFalse(BillReminderPolicy.shouldNotify(b, d(2026, 10, 16)));
        assertTrue(BillReminderPolicy.shouldNotify(b, d(2026, 10, 17)));
        assertTrue(BillReminderPolicy.shouldNotify(b, d(2026, 10, 20)));
    }

    @Test
    public void zeroLeadDaysRemindsOnTheDueDate() {
        Bill b = bill(TODAY, false, 1);
        b.reminderDaysBefore = 0;
        assertTrue(BillReminderPolicy.shouldNotify(b, TODAY));
        assertFalse(BillReminderPolicy.shouldNotify(b, d(2026, 10, 16)));
    }

    @Test
    public void overdueBillsKeepRemindingOncePerDay() {
        Bill b = bill(d(2026, 10, 10), false, 1);
        b.reminderDaysBefore = 2;
        b.lastNotifiedDay = d(2026, 10, 16);
        assertTrue(BillReminderPolicy.shouldNotify(b, TODAY));
        b.lastNotifiedDay = TODAY;
        assertFalse(BillReminderPolicy.shouldNotify(b, TODAY));
    }

    @Test
    public void paidBillsNeverNotify() {
        Bill b = bill(d(2026, 10, 10), true, 1);
        assertFalse(BillReminderPolicy.shouldNotify(b, TODAY));
    }
}
package com.example.expensetracker;

import static com.example.expensetracker.TestDays.bill;
import static com.example.expensetracker.TestDays.d;
import static com.example.expensetracker.TestDays.goal;
import static com.example.expensetracker.TestDays.rule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

public class DashboardCalculatorTest {
    private long TODAY; // set in setUp, after the time zone is fixed
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

    // ---------------------------------------------------------------- goals

    @Test
    public void emptyGoals() {
        DashboardCalculator.GoalsSummary s = DashboardCalculator.goals(Collections.emptyList(), TODAY);
        assertFalse(s.hasGoals());
        assertEquals(0, s.progress);
        assertNull(DashboardCalculator.goals(null, TODAY).nextDeadlineGoal);
    }

    @Test
    public void goalTotalsIgnoreArchivedGoals() {
        DashboardCalculator.GoalsSummary s = DashboardCalculator.goals(Arrays.asList(
                goal("A", 100_000, 25_000, 0, false),
                goal("B", 300_000, 100_000, 0, false),
                goal("Old", 999_999, 999_999, 0, true)), TODAY);
        assertEquals(2, s.activeCount);
        assertEquals(400_000, s.totalTargetPaisa);
        assertEquals(125_000, s.totalSavedPaisa);
        assertEquals(31, s.progress);          // 31.25% rounded down
        assertEquals("31.2", s.percentText);
        assertEquals(0, s.completedCount);
    }

    @Test
    public void anOverfundedGoalCannotHideAnEmptyOne() {
        DashboardCalculator.GoalsSummary s = DashboardCalculator.goals(Arrays.asList(
                goal("Done", 100_000, 200_000, 0, false),
                goal("Empty", 100_000, 0, 0, false)), TODAY);
        assertEquals(200_000, s.totalSavedPaisa); // real total saved
        assertEquals(50, s.progress);             // but progress counts at most each target
        assertEquals(1, s.completedCount);
    }

    @Test
    public void nextDeadlineIsTheEarliestUnfinishedGoalAndOverdueComesFirst() {
        DashboardCalculator.GoalsSummary s = DashboardCalculator.goals(Arrays.asList(
                goal("Later", 100_000, 0, d(2026, 12, 1), false),
                goal("Sooner", 100_000, 0, d(2026, 10, 25), false),
                goal("Finished", 100_000, 100_000, d(2026, 10, 18), false),
                goal("NoDate", 100_000, 0, 0, false)), TODAY);
        assertEquals("Sooner", s.nextDeadlineGoal.name);
        assertEquals(8, s.nextDeadlineDays);

        DashboardCalculator.GoalsSummary late = DashboardCalculator.goals(Arrays.asList(
                goal("Later", 100_000, 0, d(2026, 12, 1), false),
                goal("Missed", 100_000, 0, d(2026, 10, 10), false)), TODAY);
        assertEquals("Missed", late.nextDeadlineGoal.name);
        assertEquals(-7, late.nextDeadlineDays);
        assertEquals(1, late.overdueCount);
    }

    // ------------------------------------------------------------ recurring

    @Test
    public void forecastCoversTomorrowThroughThirtyDaysAheadAndSkipsToday() {
        // daily rule: Oct 18 .. Nov 16 = 30 occurrences; today's (Oct 17) is not counted
        RecurringRule daily = rule(Transaction.TYPE_EXPENSE, 10_000, RecurrenceCalculator.DAILY,
                d(2026, 10, 1), 0, true);
        DashboardCalculator.RecurringSummary s = DashboardCalculator.recurring(
                Collections.singletonList(daily), TODAY);
        assertEquals(30, s.upcomingCount);
        assertEquals(300_000, s.upcomingExpensePaisa);
        assertEquals(0, s.upcomingIncomePaisa);
        assertEquals(d(2026, 10, 18), s.nextMillis);
    }

    @Test
    public void forecastSplitsIncomeAndExpenseAndHonoursEndDatesAndPausedRules() {
        RecurringRule salary = rule(Transaction.TYPE_INCOME, 5_000_000, RecurrenceCalculator.MONTHLY,
                d(2026, 1, 1), 0, true);                              // Nov 1
        RecurringRule rent = rule(Transaction.TYPE_EXPENSE, 1_500_000, RecurrenceCalculator.MONTHLY,
                d(2026, 1, 5), d(2026, 10, 31), true);                // ended: nothing ahead
        RecurringRule gym = rule(Transaction.TYPE_EXPENSE, 200_000, RecurrenceCalculator.WEEKLY,
                d(2026, 10, 3), 0, true);                             // Oct 24, 31, Nov 7, 14
        RecurringRule paused = rule(Transaction.TYPE_EXPENSE, 999_999, RecurrenceCalculator.DAILY,
                d(2026, 10, 1), 0, false);
        DashboardCalculator.RecurringSummary s = DashboardCalculator.recurring(
                Arrays.asList(salary, rent, gym, paused), TODAY);
        assertEquals(3, s.activeCount);
        assertEquals(1, s.pausedCount);
        assertEquals(5_000_000, s.upcomingIncomePaisa);
        assertEquals(800_000, s.upcomingExpensePaisa);
        assertEquals(5, s.upcomingCount);
        assertEquals(d(2026, 10, 24), s.nextMillis);
        assertTrue(s.hasRules());
    }

    @Test
    public void ruleThatStartsInTheFutureAppearsInTheForecast() {
        RecurringRule later = rule(Transaction.TYPE_INCOME, 100_000, RecurrenceCalculator.WEEKLY,
                d(2026, 11, 10), 0, true);
        DashboardCalculator.RecurringSummary s = DashboardCalculator.recurring(
                Collections.singletonList(later), TODAY);
        assertEquals(1, s.upcomingCount); // Nov 10 only (Nov 17 is beyond Nov 16)
        assertEquals(d(2026, 11, 10), s.nextMillis);
    }

    @Test
    public void noRules() {
        assertFalse(DashboardCalculator.recurring(null, TODAY).hasRules());
        assertEquals(-1, DashboardCalculator.recurring(Collections.emptyList(), TODAY).nextMillis);
    }

    // ---------------------------------------------------------------- bills

    @Test
    public void billTotalsOverdueAndDueSoon() {
        DashboardCalculator.BillsSummary s = DashboardCalculator.bills(Arrays.asList(
                bill(d(2026, 10, 10), false, 100_000),  // overdue
                bill(d(2026, 10, 16), false, 50_000),   // overdue
                bill(TODAY, false, 20_000),             // due today -> due soon
                bill(d(2026, 10, 23), false, 30_000),   // today + 6 -> due soon
                bill(d(2026, 10, 24), false, 70_000),   // today + 7 -> NOT due soon
                bill(d(2026, 10, 12), true, 999_999)),  // paid -> ignored
                TODAY);
        assertEquals(5, s.unpaidCount);
        assertEquals(270_000, s.unpaidTotalPaisa);
        assertEquals(2, s.overdueCount);
        assertEquals(150_000, s.overdueTotalPaisa);
        assertEquals(2, s.dueSoonCount);
        assertEquals(50_000, s.dueSoonTotalPaisa);
        assertEquals(20_000, s.nextBill.amountPaisa);   // today's bill is the next one
        assertEquals(0, s.nextBillDays);
    }

    @Test
    public void nextBillIgnoresOverdueAndPaid() {
        DashboardCalculator.BillsSummary s = DashboardCalculator.bills(Arrays.asList(
                bill(d(2026, 10, 1), false, 1),
                bill(d(2026, 10, 18), true, 2),
                bill(d(2026, 11, 5), false, 3)), TODAY);
        assertEquals(3, s.nextBill.amountPaisa);
        assertEquals(19, s.nextBillDays);
        assertEquals(0, s.dueSoonCount);
    }

    @Test
    public void allPaidOrEmpty() {
        assertFalse(DashboardCalculator.bills(Collections.singletonList(
                bill(d(2026, 10, 1), true, 5)), TODAY).hasBills());
        assertFalse(DashboardCalculator.bills(null, TODAY).hasBills());
    }
}

package com.example.expensetracker;

import static com.example.expensetracker.TestDays.d;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

public class GoalCalculatorTest {
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

    @Test
    public void halfwayThere() {
        GoalStatus s = GoalCalculator.status(100_000, 50_000, 0, TODAY);
        assertEquals(50, s.progress);
        assertEquals("50.0", s.percentText);
        assertEquals(50_000, s.remainingPaisa);
        assertEquals(GoalStatus.State.ACTIVE, s.state);
        assertFalse(s.hasDeadline);
        assertEquals(0, s.suggestedMonthlyPaisa);
    }

    @Test
    public void percentIsRoundedDownSoNinetyNinePointNineNeverShowsAs100() {
        GoalStatus s = GoalCalculator.status(10_000, 9_999, 0, TODAY);
        assertEquals("99.9", s.percentText);
        assertEquals(99, s.progress);
        assertFalse(s.isCompleted());
    }

    @Test
    public void reachingTheTargetCompletesTheGoal() {
        GoalStatus s = GoalCalculator.status(10_000, 10_000, d(2026, 1, 1), TODAY);
        assertTrue(s.isCompleted()); // even though the deadline has passed
        assertEquals(100, s.progress);
        assertEquals(0, s.remainingPaisa);
        assertEquals(0, s.excessPaisa);
    }

    @Test
    public void overfundingIsCappedForProgressButReportedAsExcess() {
        GoalStatus s = GoalCalculator.status(10_000, 12_550, 0, TODAY);
        assertEquals("100.0", s.percentText);
        assertEquals(100, s.progress);
        assertEquals(2_550, s.excessPaisa);
        assertEquals(12_550, s.savedPaisa);
    }

    @Test
    public void passedDeadlineMeansOverdueUnlessCompleted() {
        GoalStatus s = GoalCalculator.status(10_000, 1_000, d(2026, 10, 16), TODAY);
        assertEquals(GoalStatus.State.OVERDUE, s.state);
        assertEquals(-1, s.daysLeft);
        assertEquals(0, s.suggestedMonthlyPaisa);
    }

    @Test
    public void deadlineTodayIsStillActive() {
        GoalStatus s = GoalCalculator.status(10_000, 1_000, TODAY, TODAY);
        assertEquals(GoalStatus.State.ACTIVE, s.state);
        assertEquals(0, s.daysLeft);
        assertEquals(9_000, s.suggestedMonthlyPaisa); // everything remaining, in one "month"
    }

    @Test
    public void suggestedMonthlyAmountRoundsUpSoThePlanNeverFallsShort() {
        // 61 days -> ceil(61/30) = 3 months; 100000 / 3 = 33333.33 -> 33334
        GoalStatus s = GoalCalculator.status(100_000, 0, MonthUtils.addDays(TODAY, 61), TODAY);
        assertEquals(61, s.daysLeft);
        assertEquals(33_334, s.suggestedMonthlyPaisa);
        // 30 days -> 1 month
        assertEquals(100_000, GoalCalculator.status(100_000, 0, MonthUtils.addDays(TODAY, 30), TODAY)
                .suggestedMonthlyPaisa);
        // 31 days -> 2 months
        assertEquals(50_000, GoalCalculator.status(100_000, 0, MonthUtils.addDays(TODAY, 31), TODAY)
                .suggestedMonthlyPaisa);
    }

    @Test
    public void largestAmountsDoNotOverflow() {
        long max = MoneyUtils.MAX_PAISA;
        GoalStatus s = GoalCalculator.status(max, max - 1, 0, TODAY);
        assertEquals("99.9", s.percentText);
        assertEquals(99, s.progress);
        assertEquals(1, s.remainingPaisa);
    }

    @Test
    public void zeroAndNegativeInputsAreSafe() {
        GoalStatus s = GoalCalculator.status(0, 0, 0, TODAY);
        assertEquals(0, s.progress);
        assertFalse(s.isCompleted());
        GoalStatus n = GoalCalculator.status(10_000, -5, 0, TODAY);
        assertEquals(0, n.savedPaisa);
    }
}

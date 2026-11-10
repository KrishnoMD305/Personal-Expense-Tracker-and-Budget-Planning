package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BudgetAlertsTest {
    private static final List<String> CATS = Arrays.asList("Food", "Transport", "Rent");

    private static MonthOverview overview(long overall, long foodBudget, long foodSpent, long otherSpent) {
        java.util.List<Budget> b = new java.util.ArrayList<>();
        if (overall > 0) b.add(BudgetCalculatorTest.budget("2026-10", Budget.OVERALL, overall));
        if (foodBudget > 0) b.add(BudgetCalculatorTest.budget("2026-10", "Food", foodBudget));
        java.util.List<CategoryTotal> s = new java.util.ArrayList<>();
        if (foodSpent > 0) s.add(BudgetCalculatorTest.total("Food", foodSpent));
        if (otherSpent > 0) s.add(BudgetCalculatorTest.total("Rent", otherSpent));
        return MonthOverview.build("2026-10", b, s);
    }

    @Test
    public void noAlertsBelowEightyPercent() {
        MonthOverview o = overview(100_000, 30_000, 20_000, 10_000);
        assertTrue(BudgetAlerts.forMonth(o, CATS).isEmpty());
        assertTrue(BudgetAlerts.forExpense(o, "Food").isEmpty());
    }

    @Test
    public void noAlertsWhenNoBudgetsAreSet() {
        MonthOverview o = overview(0, 0, 999_999, 999_999);
        assertTrue(BudgetAlerts.forMonth(o, CATS).isEmpty());
    }

    @Test
    public void warningAtEightyPercent() {
        MonthOverview o = overview(0, 10_000, 8_000, 0);
        List<BudgetAlerts.Alert> a = BudgetAlerts.forExpense(o, "Food");
        assertEquals(1, a.size());
        assertEquals(BudgetStatus.Level.WARNING, a.get(0).level);
        assertTrue(a.get(0).message, a.get(0).message.contains("80% used"));
        assertTrue(a.get(0).message.contains(MoneyUtils.format(2_000)));
    }

    @Test
    public void limitReachedAtHundredPercent() {
        MonthOverview o = overview(0, 10_000, 10_000, 0);
        BudgetAlerts.Alert a = BudgetAlerts.forExpense(o, "Food").get(0);
        assertEquals(BudgetStatus.Level.EXCEEDED, a.level);
        assertTrue(a.message, a.message.contains("100% of budget used"));
    }

    @Test
    public void overspendingAlertNamesTheAmount() {
        MonthOverview o = overview(0, 10_000, 12_500, 0);
        BudgetAlerts.Alert a = BudgetAlerts.forExpense(o, "Food").get(0);
        assertEquals(BudgetStatus.Level.EXCEEDED, a.level);
        assertTrue(a.message, a.message.contains("OVERSPENT by " + MoneyUtils.format(2_500)));
    }

    @Test
    public void overallAndCategoryAlertsAreBothReported() {
        // overall 20,000 with 18,000 spent in total (90%); Food 10,000 with 11,000 spent
        MonthOverview o = overview(20_000, 10_000, 11_000, 7_000);
        List<BudgetAlerts.Alert> a = BudgetAlerts.forExpense(o, "Food");
        assertEquals(2, a.size());
        assertEquals("Food", a.get(0).scope);
        assertEquals(BudgetAlerts.OVERALL_LABEL, a.get(1).scope);
        assertEquals(BudgetStatus.Level.WARNING, a.get(1).level);
        assertEquals(2, BudgetAlerts.forMonth(o, CATS).size());
    }

    @Test
    public void expenseInUnbudgetedCategoryStillTriggersOverallAlert() {
        MonthOverview o = overview(10_000, 0, 0, 9_000); // Rent has no budget
        List<BudgetAlerts.Alert> a = BudgetAlerts.forExpense(o, "Rent");
        assertEquals(1, a.size());
        assertEquals(BudgetAlerts.OVERALL_LABEL, a.get(0).scope);
        assertEquals(Collections.singletonList(BudgetAlerts.OVERALL_LABEL),
                Collections.singletonList(BudgetAlerts.forMonth(o, CATS).get(0).scope));
    }
}

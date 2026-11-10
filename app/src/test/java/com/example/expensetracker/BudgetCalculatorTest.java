package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BudgetCalculatorTest {
    private static long bdt(long taka) {
        return taka * 100;
    }

    @Test
    public void noBudgetMeansNoLevel() {
        BudgetStatus s = BudgetCalculator.status(0, bdt(50));
        assertFalse(s.hasBudget());
        assertEquals(BudgetStatus.Level.NONE, s.level);
        assertFalse(s.isOverspent());
        assertEquals(bdt(50), s.spentPaisa);
    }

    @Test
    public void belowEightyPercentIsNormal() {
        BudgetStatus s = BudgetCalculator.status(bdt(1000), bdt(799));
        assertEquals(BudgetStatus.Level.NORMAL, s.level);
        assertEquals(bdt(201), s.remainingPaisa);
        assertEquals("79.9", s.percentText);
        assertEquals(79, s.progress);
    }

    @Test
    public void justBelowEightyPercentDoesNotWarn() {
        // 79.99% must not be rounded up into a warning
        BudgetStatus s = BudgetCalculator.status(10_000, 7_999);
        assertEquals(BudgetStatus.Level.NORMAL, s.level);
        assertEquals("79.9", s.percentText);
    }

    @Test
    public void exactlyEightyPercentWarns() {
        BudgetStatus s = BudgetCalculator.status(bdt(1000), bdt(800));
        assertEquals(BudgetStatus.Level.WARNING, s.level);
        assertEquals("80", s.percentText);
        assertEquals(bdt(200), s.remainingPaisa);
        assertFalse(s.isOverspent());
    }

    @Test
    public void justBelowHundredIsStillWarning() {
        BudgetStatus s = BudgetCalculator.status(10_000, 9_999);
        assertEquals(BudgetStatus.Level.WARNING, s.level);
        assertEquals("99.9", s.percentText);
        assertEquals(99, s.progress);
    }

    @Test
    public void exactlyHundredPercentIsExceededButNotOverspent() {
        BudgetStatus s = BudgetCalculator.status(bdt(1000), bdt(1000));
        assertEquals(BudgetStatus.Level.EXCEEDED, s.level);
        assertEquals("100", s.percentText);
        assertEquals(0, s.remainingPaisa);
        assertFalse(s.isOverspent());
        assertEquals(100, s.progress);
    }

    @Test
    public void overspendingReportsNegativeRemainingAndCapsProgress() {
        BudgetStatus s = BudgetCalculator.status(bdt(1000), bdt(1250));
        assertEquals(BudgetStatus.Level.EXCEEDED, s.level);
        assertTrue(s.isOverspent());
        assertEquals(-bdt(250), s.remainingPaisa);
        assertEquals("125", s.percentText);
        assertEquals(100, s.progress);
    }

    @Test
    public void percentKeepsOneDecimal() {
        BudgetStatus s = BudgetCalculator.status(bdt(300), bdt(100)); // 33.333...
        assertEquals("33.3", s.percentText);
        assertEquals(33, s.progress);
    }

    @Test
    public void zeroSpentIsZeroPercent() {
        BudgetStatus s = BudgetCalculator.status(bdt(500), 0);
        assertEquals(BudgetStatus.Level.NORMAL, s.level);
        assertEquals("0", s.percentText);
        assertEquals(bdt(500), s.remainingPaisa);
    }

    @Test
    public void largestSupportedAmountsDoNotOverflow() {
        BudgetStatus s = BudgetCalculator.status(MoneyUtils.MAX_PAISA, MoneyUtils.MAX_PAISA * 3);
        assertEquals(BudgetStatus.Level.EXCEEDED, s.level);
        assertEquals("300", s.percentText);
    }

    // ---- MonthOverview-based

    @Test
    public void overviewAggregatesAcrossCategories() {
        java.util.List<Budget> budgets = new java.util.ArrayList<>();
        budgets.add(budget("2026-10", Budget.OVERALL, bdt(10_000)));
        budgets.add(budget("2026-10", "Food", bdt(3_000)));
        budgets.add(budget("2026-10", "Transport", bdt(1_000)));
        java.util.List<CategoryTotal> spent = new java.util.ArrayList<>();
        spent.add(total("Food", bdt(2_500)));
        spent.add(total("Transport", bdt(1_200)));
        spent.add(total("Rent", bdt(4_000))); // no category budget
        spent.add(total(null, bdt(10)));       // legacy row without category

        MonthOverview o = MonthOverview.build("2026-10", budgets, spent);
        assertEquals(bdt(10_000), o.overallBudget());
        assertEquals(bdt(7_710), o.totalSpent());
        assertEquals(bdt(4_000), o.categoryBudgetsTotal());

        BudgetStatus overall = BudgetCalculator.overall(o);
        assertEquals(BudgetStatus.Level.NORMAL, overall.level);
        assertEquals(bdt(2_290), overall.remainingPaisa);
        assertEquals("77.1", overall.percentText);

        BudgetStatus food = BudgetCalculator.forCategory(o, "Food");
        assertEquals(BudgetStatus.Level.WARNING, food.level);
        assertEquals("83.3", food.percentText);

        BudgetStatus transport = BudgetCalculator.forCategory(o, "Transport");
        assertTrue(transport.isOverspent());
        assertEquals(-bdt(200), transport.remainingPaisa);

        assertFalse(BudgetCalculator.forCategory(o, "Rent").hasBudget());
        assertEquals(bdt(4_000), BudgetCalculator.forCategory(o, "Rent").spentPaisa);
    }

    @Test
    public void editingOrDeletingATransactionChangesTheResult() {
        // The app recomputes from raw rows; simulate before/after of an edit and a delete.
        java.util.List<Budget> budgets = java.util.Collections.singletonList(budget("2026-10", "Food", bdt(1_000)));
        MonthOverview before = MonthOverview.build("2026-10", budgets,
                java.util.Collections.singletonList(total("Food", bdt(900))));
        assertEquals(BudgetStatus.Level.WARNING, BudgetCalculator.forCategory(before, "Food").level);

        MonthOverview afterEdit = MonthOverview.build("2026-10", budgets,
                java.util.Collections.singletonList(total("Food", bdt(500))));
        assertEquals(BudgetStatus.Level.NORMAL, BudgetCalculator.forCategory(afterEdit, "Food").level);
        assertEquals(bdt(500), BudgetCalculator.forCategory(afterEdit, "Food").remainingPaisa);

        MonthOverview afterDelete = MonthOverview.build("2026-10", budgets, new java.util.ArrayList<>());
        assertEquals(0, BudgetCalculator.forCategory(afterDelete, "Food").spentPaisa);
        assertEquals(bdt(1_000), BudgetCalculator.forCategory(afterDelete, "Food").remainingPaisa);
    }

    static Budget budget(String month, String category, long paisa) {
        Budget b = new Budget();
        b.monthKey = month;
        b.category = category;
        b.amountPaisa = paisa;
        return b;
    }

    static CategoryTotal total(String category, long paisa) {
        CategoryTotal t = new CategoryTotal();
        t.category = category;
        t.total = paisa;
        return t;
    }
}

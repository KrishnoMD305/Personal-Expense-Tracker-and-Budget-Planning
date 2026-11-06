package com.example.expensetracker;

import java.util.Collections;
import java.util.List;

/**
 * Everything the Reports screen shows for one {@link ReportPeriod}, computed from real database
 * rows by {@link ReportCalculator}. All money is in whole paisa; all ratios are in tenths of a
 * percent (185 = 18.5%) so no floating point is involved in anything that is displayed as a number.
 */
public final class ReportData {

    /** Totals for one period. */
    public static final class Summary {
        public final long incomePaisa;
        public final long expensePaisa;
        public final int incomeCount;
        public final int expenseCount;
        /** income - expense; negative when more was spent than earned. */
        public final long netPaisa;
        /** (income - expense) / income in tenths of a percent; null when there was no income. */
        public final Long savingsRateTenths;

        Summary(long incomePaisa, long expensePaisa, int incomeCount, int expenseCount,
                long netPaisa, Long savingsRateTenths) {
            this.incomePaisa = incomePaisa;
            this.expensePaisa = expensePaisa;
            this.incomeCount = incomeCount;
            this.expenseCount = expenseCount;
            this.netPaisa = netPaisa;
            this.savingsRateTenths = savingsRateTenths;
        }

        public int transactionCount() {
            return incomeCount + expenseCount;
        }

        public boolean isEmpty() {
            return transactionCount() == 0;
        }
    }

    /** One expense category's share of the period's spending. */
    public static final class CategorySlice {
        public final String category;
        public final long amountPaisa;
        public final int count;
        /** Share of total expenses in tenths of a percent (rounded half-up). */
        public final long shareTenths;
        /** What this category cost in the previous period. */
        public final long previousPaisa;
        /** Change vs previous period in tenths of a percent; null when there was no previous spend. */
        public final Long changeTenths;

        CategorySlice(String category, long amountPaisa, int count, long shareTenths,
                      long previousPaisa, Long changeTenths) {
            this.category = category;
            this.amountPaisa = amountPaisa;
            this.count = count;
            this.shareTenths = shareTenths;
            this.previousPaisa = previousPaisa;
            this.changeTenths = changeTenths;
        }
    }

    /** Income and expenses for one calendar month (or the part of it inside a custom range). */
    public static final class MonthBar {
        public final String monthKey;
        public final long incomePaisa;
        public final long expensePaisa;
        public final long netPaisa;
        public final Long savingsRateTenths;

        MonthBar(String monthKey, long incomePaisa, long expensePaisa, long netPaisa,
                 Long savingsRateTenths) {
            this.monthKey = monthKey;
            this.incomePaisa = incomePaisa;
            this.expensePaisa = expensePaisa;
            this.netPaisa = netPaisa;
            this.savingsRateTenths = savingsRateTenths;
        }
    }

    public enum Granularity { DAY, WEEK, MONTH }

    /** Expenses inside one trend bucket (a day, a 7-day block, or a month). */
    public static final class TrendPoint {
        public final long startMillis;
        public final String label;
        public final long expensePaisa;

        TrendPoint(long startMillis, String label, long expensePaisa) {
            this.startMillis = startMillis;
            this.label = label;
            this.expensePaisa = expensePaisa;
        }
    }

    /** How one category's spending moved between the previous and the current period. */
    public static final class CategoryChange {
        public final String category;
        public final long currentPaisa;
        public final long previousPaisa;
        /** current - previous. */
        public final long deltaPaisa;
        /** Change in tenths of a percent; null when previousPaisa is 0. */
        public final Long changeTenths;

        CategoryChange(String category, long currentPaisa, long previousPaisa, long deltaPaisa,
                       Long changeTenths) {
            this.category = category;
            this.currentPaisa = currentPaisa;
            this.previousPaisa = previousPaisa;
            this.deltaPaisa = deltaPaisa;
            this.changeTenths = changeTenths;
        }
    }

    /** One budget (monthly or per-category) compared with spending over the period. */
    public static final class BudgetRow {
        public final String label;
        public final boolean overall;
        public final BudgetStatus status;

        BudgetRow(String label, boolean overall, BudgetStatus status) {
            this.label = label;
            this.overall = overall;
            this.status = status;
        }
    }

    /**
     * Budget-vs-actual for the period. Budgets are monthly, so this is only available for whole
     * calendar months (a single month, or a custom range that starts on the 1st and ends on a month's
     * last day). For several months, each budget is summed over the months in which it was set and is
     * compared with spending in exactly those months.
     */
    public static final class BudgetReport {
        /** False when the period is not made of whole calendar months. */
        public final boolean periodSupported;
        public final List<BudgetRow> rows;
        /** Months in the period that have at least one budget. */
        public final int monthsWithBudgets;

        BudgetReport(boolean periodSupported, List<BudgetRow> rows, int monthsWithBudgets) {
            this.periodSupported = periodSupported;
            this.rows = Collections.unmodifiableList(rows);
            this.monthsWithBudgets = monthsWithBudgets;
        }

        public boolean hasBudgets() {
            return !rows.isEmpty();
        }
    }

    public final ReportPeriod period;
    public final ReportPeriod previousPeriod;
    /** Human-readable name of the previous period, e.g. "September 2026" or "previous 30 days". */
    public final String previousLabel;

    public final Summary current;
    public final Summary previous;

    /** Expense categories, largest first. Amounts add up to {@code current.expensePaisa}. */
    public final List<CategorySlice> categories;

    public final List<MonthBar> bars;
    /** True when a custom range spans more than 12 months and only the latest 12 are charted. */
    public final boolean barsTruncated;
    public final int totalBarMonths;

    public final Granularity trendGranularity;
    public final List<TrendPoint> trend;

    public final List<Transaction> largestExpenses;
    public final List<Transaction> largestIncome;
    /** Biggest rise / fall vs the previous period; null when there is nothing to compare. */
    public final CategoryChange biggestIncrease;
    public final CategoryChange biggestDecrease;

    /** Days of the period that have started (today included) - the divisor for daily averages. */
    public final int elapsedDays;
    /** Expenses up to and including today / elapsedDays; 0 when no day has elapsed. */
    public final long avgDailyExpensePaisa;
    /** Projected whole-month spending when viewing the current month part-way through; else -1. */
    public final long projectedMonthExpensePaisa;
    /** Start of the day with the highest spending, or -1 when there were no expenses. */
    public final long highestSpendDayMillis;
    public final long highestSpendDayPaisa;

    /** Short plain-language observations derived from the numbers above. */
    public final List<String> insights;

    public final BudgetReport budget;

    ReportData(ReportPeriod period, ReportPeriod previousPeriod, String previousLabel,
               Summary current, Summary previous, List<CategorySlice> categories,
               List<MonthBar> bars, boolean barsTruncated, int totalBarMonths,
               Granularity trendGranularity, List<TrendPoint> trend,
               List<Transaction> largestExpenses, List<Transaction> largestIncome,
               CategoryChange biggestIncrease, CategoryChange biggestDecrease,
               int elapsedDays, long avgDailyExpensePaisa, long projectedMonthExpensePaisa,
               long highestSpendDayMillis, long highestSpendDayPaisa,
               List<String> insights, BudgetReport budget) {
        this.period = period;
        this.previousPeriod = previousPeriod;
        this.previousLabel = previousLabel;
        this.current = current;
        this.previous = previous;
        this.categories = Collections.unmodifiableList(categories);
        this.bars = Collections.unmodifiableList(bars);
        this.barsTruncated = barsTruncated;
        this.totalBarMonths = totalBarMonths;
        this.trendGranularity = trendGranularity;
        this.trend = Collections.unmodifiableList(trend);
        this.largestExpenses = Collections.unmodifiableList(largestExpenses);
        this.largestIncome = Collections.unmodifiableList(largestIncome);
        this.biggestIncrease = biggestIncrease;
        this.biggestDecrease = biggestDecrease;
        this.elapsedDays = elapsedDays;
        this.avgDailyExpensePaisa = avgDailyExpensePaisa;
        this.projectedMonthExpensePaisa = projectedMonthExpensePaisa;
        this.highestSpendDayMillis = highestSpendDayMillis;
        this.highestSpendDayPaisa = highestSpendDayPaisa;
        this.insights = Collections.unmodifiableList(insights);
        this.budget = budget;
    }

    public boolean hasData() {
        return !current.isEmpty();
    }
}

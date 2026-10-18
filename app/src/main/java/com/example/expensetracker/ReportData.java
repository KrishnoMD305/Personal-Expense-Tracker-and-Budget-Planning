package com.example.expensetracker;

import java.util.Collections;
import java.util.List;

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

    public static final class CategorySlice {
        public final String category;
        public final long amountPaisa;
        public final int count;
        public final long shareTenths;
        public final long previousPaisa;
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

    public static final class CategoryChange {
        public final String category;
        public final long currentPaisa;
        public final long previousPaisa;
        /** current - previous. */
        public final long deltaPaisa;
        public final Long changeTenths;

        CategoryChange(String category, long currentPaisa, long previousPaisa, long deltaPaisa, Long changeTenths) {
            this.category = category;
            this.currentPaisa = currentPaisa;
            this.previousPaisa = previousPaisa;
            this.deltaPaisa = deltaPaisa;
            this.changeTenths = changeTenths;
        }
    }

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

    public static final class BudgetReport {
        public final boolean periodSupported;
        public final List<BudgetRow> rows;
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
    public final String previousLabel;
    public final Summary current;
    public final Summary previous;
    public final List<CategorySlice> categories;

    public final List<MonthBar> bars;
    public final boolean barsTruncated;
    public final int totalBarMonths;

    public final Granularity trendGranularity;
    public final List<TrendPoint> trend;

    public final List<Transaction> largestExpenses;
    public final List<Transaction> largestIncome;
    public final CategoryChange biggestIncrease;
    public final CategoryChange biggestDecrease;
    public final int elapsedDays;
    public final long avgDailyExpensePaisa;
    public final long projectedMonthExpensePaisa;
    public final long highestSpendDayMillis;
    public final long highestSpendDayPaisa;
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

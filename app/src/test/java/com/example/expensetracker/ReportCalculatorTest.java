package com.example.expensetracker;

import static com.example.expensetracker.ReportPeriodTest.at;
import static com.example.expensetracker.ReportPeriodTest.day;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ReportCalculatorTest {
    private TimeZone originalZone;
    private Locale originalLocale;
    private long nextId;

    @Before
    public void setUp() {
        originalZone = TimeZone.getDefault();
        originalLocale = Locale.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        Locale.setDefault(Locale.US);
        nextId = 1;
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalZone);
        Locale.setDefault(originalLocale);
    }

    // ------------------------------------------------------------------ helpers

    /** Whole taka to paisa. */
    private static long tk(long taka) {
        return taka * 100;
    }

    private Transaction tx(String type, long taka, String category, long date) {
        return txPaisa(type, tk(taka), category, date);
    }

    private Transaction txPaisa(String type, long paisa, String category, long date) {
        Transaction t = new Transaction();
        t.id = nextId++;
        t.type = type;
        t.amountPaisa = paisa;
        t.category = category;
        t.note = "";
        t.dateMillis = date;
        t.createdAt = date;
        return t;
    }

    private Transaction exp(long taka, String category, long date) {
        return tx(Transaction.TYPE_EXPENSE, taka, category, date);
    }

    private Transaction inc(long taka, String category, long date) {
        return tx(Transaction.TYPE_INCOME, taka, category, date);
    }

    private static Budget budget(String month, String category, long taka) {
        Budget b = new Budget();
        b.monthKey = month;
        b.category = category;
        b.amountPaisa = tk(taka);
        return b;
    }

    /** "Now" far after every test month, so periods are complete unless a test says otherwise. */
    private static final long LATER = day(2030, 1, 15);

    private static ReportData report(ReportPeriod p, List<Transaction> txs, List<Budget> budgets, long now) {
        return ReportCalculator.build(p, txs, budgets, now);
    }

    private static ReportData octReport(List<Transaction> txs) {
        return report(ReportPeriod.month("2026-10"), txs, new ArrayList<Budget>(), LATER);
    }

    private static List<Transaction> list(Transaction... t) {
        return new ArrayList<>(Arrays.asList(t));
    }

    // ------------------------------------------------------------------ summary

    @Test
    public void summaryTotalsNetCashFlowAndSavingsRate() {
        ReportData d = octReport(list(
                inc(50000, "Salary", day(2026, 10, 1)),
                inc(10000, "Freelance", day(2026, 10, 10)),
                exp(5000, "Food", day(2026, 10, 3)),
                exp(15000, "Rent", day(2026, 10, 5))));
        assertEquals(tk(60000), d.current.incomePaisa);
        assertEquals(tk(20000), d.current.expensePaisa);
        assertEquals(tk(40000), d.current.netPaisa);
        assertEquals(2, d.current.incomeCount);
        assertEquals(2, d.current.expenseCount);
        assertEquals(4, d.current.transactionCount());
        // 40,000 / 60,000 = 66.666...% -> 66.7%
        assertEquals(Long.valueOf(667), d.current.savingsRateTenths);
        assertTrue(d.hasData());
        assertTrue(d.insights.contains("You saved 66.7% of your income."));
    }

    @Test
    public void savingsRateIsNegativeWhenSpendingExceedsIncome() {
        ReportData d = octReport(list(
                inc(10000, "Salary", day(2026, 10, 1)),
                exp(12500, "Rent", day(2026, 10, 2))));
        assertEquals(tk(-2500), d.current.netPaisa);
        assertEquals(Long.valueOf(-250), d.current.savingsRateTenths);
        assertTrue(d.insights.contains("Expenses exceeded income by \u09F3 2,500.00."));
        assertFalse(d.insights.toString().contains("You saved"));
    }

    @Test
    public void savingsRateIsNotAvailableWithoutIncome() {
        ReportData d = octReport(list(exp(500, "Food", day(2026, 10, 2))));
        assertNull(d.current.savingsRateTenths);
        assertEquals("N/A", ReportFormat.percent(d.current.savingsRateTenths));
        assertEquals(tk(-500), d.current.netPaisa);
    }

    @Test
    public void periodBoundariesAreStartInclusiveEndExclusive() {
        ReportData d = octReport(list(
                exp(100, "Food", at(2026, 9, 30, 23, 59)),   // previous month
                exp(200, "Food", day(2026, 10, 1)),          // first instant of October: counted
                exp(300, "Food", at(2026, 10, 31, 23, 59)),  // last minute of October: counted
                exp(400, "Food", day(2026, 11, 1))));        // next month: not counted
        assertEquals(tk(500), d.current.expensePaisa);
        assertEquals(2, d.current.expenseCount);
        assertEquals(tk(100), d.previous.expensePaisa);
    }

    @Test
    public void unsupportedOrMissingTransactionTypesAreIgnored() {
        Transaction weird = txPaisa("TRANSFER", 99999, "Food", day(2026, 10, 2));
        Transaction nullType = txPaisa(null, 88888, "Food", day(2026, 10, 2));
        ReportData d = octReport(list(weird, nullType, exp(10, "Food", day(2026, 10, 2))));
        assertEquals(tk(10), d.current.expensePaisa);
        assertEquals(1, d.current.transactionCount());
    }

    @Test
    public void emptyDatabaseProducesAnEmptyButCompleteReport() {
        ReportData d = octReport(new ArrayList<Transaction>());
        assertFalse(d.hasData());
        assertEquals(0, d.current.netPaisa);
        assertTrue(d.categories.isEmpty());
        assertEquals(6, d.bars.size());
        assertEquals(31, d.trend.size());
        assertTrue(d.largestExpenses.isEmpty());
        assertTrue(d.largestIncome.isEmpty());
        assertNull(d.biggestIncrease);
        assertNull(d.biggestDecrease);
        assertTrue(d.insights.isEmpty());
        assertTrue(d.budget.periodSupported);
        assertFalse(d.budget.hasBudgets());
        assertEquals(-1, d.highestSpendDayMillis);
    }

    // ------------------------------------------------------------------ month-to-month comparison

    @Test
    public void comparesWithPreviousMonth() {
        ReportData d = octReport(list(
                exp(3000, "Food", day(2026, 9, 10)),
                exp(4500, "Food", day(2026, 10, 10))));
        assertEquals("September 2026", d.previousLabel);
        assertEquals(tk(3000), d.previous.expensePaisa);
        assertEquals(tk(4500), d.current.expensePaisa);
        assertTrue(d.insights.contains("Spending is up 50% vs September 2026 (\u09F3 1,500.00 more)."));
    }

    @Test
    public void reportsADecreaseWithRoundedPercent() {
        ReportData d = octReport(list(
                exp(3000, "Food", day(2026, 9, 10)),
                exp(2000, "Food", day(2026, 10, 10))));
        // -1000 / 3000 = -33.33% -> 33.3%
        assertTrue(d.insights.contains("Spending is down 33.3% vs September 2026 (\u09F3 1,000.00 less)."));
    }

    @Test
    public void noComparisonPercentWhenPreviousMonthHadNoSpending() {
        ReportData d = octReport(list(exp(2000, "Food", day(2026, 10, 10))));
        assertTrue(d.insights.contains("No spending was recorded in September 2026 to compare with."));
        assertNull(d.biggestIncrease);
        assertNull(d.biggestDecrease);
    }

    @Test
    public void previousMonthOfJanuaryIsDecember() {
        ReportData d = report(ReportPeriod.month("2026-01"), list(
                exp(700, "Food", day(2025, 12, 31)),
                exp(100, "Food", day(2026, 1, 1))), new ArrayList<Budget>(), LATER);
        assertEquals("December 2025", d.previousLabel);
        assertEquals(tk(700), d.previous.expensePaisa);
        assertEquals(tk(100), d.current.expensePaisa);
    }

    @Test
    public void unchangedSpendingIsDescribedAsTheSame() {
        ReportData d = octReport(list(
                exp(100, "Food", day(2026, 9, 10)),
                exp(100, "Food", day(2026, 10, 10))));
        assertTrue(d.insights.contains("Spending is the same as in September 2026."));
    }

    @Test
    public void customRangeIncludesLastDayAndComparesWithEqualLengthBlockBefore() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 10, 10), day(2026, 10, 12));
        ReportData d = report(p, list(
                exp(1, "Food", day(2026, 10, 6)),    // before previous block: ignored
                exp(20, "Food", day(2026, 10, 9)),   // previous block (7th-9th)
                exp(300, "Food", day(2026, 10, 10)),
                exp(400, "Food", day(2026, 10, 12)), // last day, included
                exp(5000, "Food", day(2026, 10, 13))), new ArrayList<Budget>(), LATER);
        assertEquals(tk(700), d.current.expensePaisa);
        assertEquals(tk(20), d.previous.expensePaisa);
        assertEquals("the previous 3 days", d.previousLabel);
    }

    @Test
    public void categoriesAreSortedSharedAndComparedWithPreviousPeriod() {
        ReportData d = octReport(list(
                exp(100, "Food", day(2026, 10, 1)),
                exp(200, "Food", day(2026, 10, 2)),
                exp(300, "Food", day(2026, 10, 3)),
                exp(600, "Transport", day(2026, 10, 4)),
                exp(300, "Rent", day(2026, 10, 5)),
                exp(50, null, day(2026, 10, 6)),
                exp(50, "   ", day(2026, 10, 7)),
                exp(400, "Food", day(2026, 9, 7)),
                inc(9999, "Salary", day(2026, 10, 7)))); // income never appears among expense categories
        assertEquals(4, d.categories.size());
        ReportData.CategorySlice food = d.categories.get(0);
        ReportData.CategorySlice transport = d.categories.get(1);
        ReportData.CategorySlice rent = d.categories.get(2);
        ReportData.CategorySlice other = d.categories.get(3);

        assertEquals("Food", food.category);          // 600, ties with Transport -> name order
        assertEquals("Transport", transport.category);
        assertEquals("Rent", rent.category);
        assertEquals(ReportCalculator.UNCATEGORIZED, other.category);

        assertEquals(tk(600), food.amountPaisa);
        assertEquals(3, food.count);
        assertEquals(tk(100), other.amountPaisa);
        assertEquals(2, other.count);

        // total 1,600: 37.5%, 37.5%, 18.75 -> 18.8 (half-up), 6.25 -> 6.3 (half-up)
        assertEquals(375, food.shareTenths);
        assertEquals(375, transport.shareTenths);
        assertEquals(188, rent.shareTenths);
        assertEquals(63, other.shareTenths);

        long sum = 0;
        for (ReportData.CategorySlice s : d.categories) sum += s.amountPaisa;
        assertEquals(d.current.expensePaisa, sum);

        assertEquals(tk(400), food.previousPaisa);
        assertEquals(Long.valueOf(500), food.changeTenths); // (600-400)/400 = +50%
        assertNull(transport.changeTenths);                  // nothing last month: no percentage
    }

    @Test
    public void categoryMoversAgainstPreviousMonth() {
        ReportData d = octReport(list(
                exp(1000, "Food", day(2026, 9, 5)),
                exp(500, "Transport", day(2026, 9, 6)),
                exp(200, "Health", day(2026, 9, 7)),
                exp(1500, "Food", day(2026, 10, 5)),
                exp(200, "Transport", day(2026, 10, 6)),
                exp(400, "Shopping", day(2026, 10, 7))));
        assertNotNull(d.biggestIncrease);
        assertEquals("Food", d.biggestIncrease.category); // +500 beats Shopping's +400
        assertEquals(tk(500), d.biggestIncrease.deltaPaisa);
        assertEquals(Long.valueOf(500), d.biggestIncrease.changeTenths);
        assertNotNull(d.biggestDecrease);
        assertEquals("Transport", d.biggestDecrease.category); // -300 beats Health's -200
        assertEquals(tk(-300), d.biggestDecrease.deltaPaisa);
        assertEquals(Long.valueOf(-600), d.biggestDecrease.changeTenths);
        assertTrue(d.insights.contains(
                "Biggest increase: Food, up \u09F3 500.00 (+50%) vs September 2026."));
        assertTrue(d.insights.contains(
                "Biggest decrease: Transport, down \u09F3 300.00 (-60%) vs September 2026."));
    }

    // ------------------------------------------------------------------ monthly bars

    @Test
    public void monthModeChartsSixMonthsEndingWithTheSelectedOneAcrossAYearBoundary() {
        ReportData d = report(ReportPeriod.month("2027-02"), list(
                        inc(1000, "Salary", day(2026, 9, 1)),
                        exp(200, "Food", day(2026, 12, 15)),
                        inc(500, "Salary", day(2027, 1, 5)),
                        exp(100, "Food", day(2027, 1, 20)),
                        exp(50, "Food", day(2027, 2, 3)),
                        exp(999, "Food", day(2026, 8, 31)),   // before the 6-month window
                        exp(888, "Food", day(2027, 3, 1))),   // after the selected month
                new ArrayList<Budget>(), LATER);
        assertEquals(6, d.bars.size());
        assertFalse(d.barsTruncated);
        String[] keys = {"2026-09", "2026-10", "2026-11", "2026-12", "2027-01", "2027-02"};
        for (int i = 0; i < keys.length; i++) assertEquals(keys[i], d.bars.get(i).monthKey);

        assertEquals(tk(1000), d.bars.get(0).incomePaisa);
        assertEquals(0, d.bars.get(0).expensePaisa);
        assertEquals(Long.valueOf(1000), d.bars.get(0).savingsRateTenths); // saved 100%
        assertEquals(0, d.bars.get(1).incomePaisa);
        assertNull(d.bars.get(1).savingsRateTenths);                        // no income: not available
        assertEquals(tk(200), d.bars.get(3).expensePaisa);
        assertEquals(tk(500), d.bars.get(4).incomePaisa);
        assertEquals(tk(100), d.bars.get(4).expensePaisa);
        assertEquals(tk(400), d.bars.get(4).netPaisa);
        assertEquals(Long.valueOf(800), d.bars.get(4).savingsRateTenths);   // 400/500
        assertEquals(tk(50), d.bars.get(5).expensePaisa);
        // the selected month's bar equals the summary
        assertEquals(d.current.expensePaisa, d.bars.get(5).expensePaisa);
    }

    @Test
    public void customRangeBarsAreClippedToTheRangeAndAddUpToTheSummary() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 10, 15), day(2026, 12, 10));
        ReportData d = report(p, list(
                        exp(7, "Food", day(2026, 10, 10)),    // before the range
                        exp(100, "Food", day(2026, 10, 15)),
                        exp(50, "Food", day(2026, 10, 31)),
                        inc(300, "Salary", day(2026, 11, 5)),
                        exp(20, "Food", day(2026, 12, 10)),
                        exp(999, "Food", day(2026, 12, 11))), // after the range
                new ArrayList<Budget>(), LATER);
        assertEquals(3, d.bars.size());
        assertFalse(d.barsTruncated);
        assertEquals(3, d.totalBarMonths);
        assertEquals("2026-10", d.bars.get(0).monthKey);
        assertEquals(tk(150), d.bars.get(0).expensePaisa);
        assertEquals(tk(300), d.bars.get(1).incomePaisa);
        assertEquals(tk(20), d.bars.get(2).expensePaisa);
        long income = 0, expense = 0;
        for (ReportData.MonthBar b : d.bars) {
            income += b.incomePaisa;
            expense += b.expensePaisa;
        }
        assertEquals(d.current.incomePaisa, income);
        assertEquals(d.current.expensePaisa, expense);
    }

    @Test
    public void longCustomRangesChartOnlyTheLatestTwelveMonths() {
        ReportPeriod p = ReportPeriod.custom(day(2025, 1, 1), day(2026, 10, 31)); // 22 months
        ReportData d = report(p, list(
                exp(11, "Food", day(2025, 10, 20)),   // in range but before the charted window
                exp(22, "Food", day(2025, 11, 2)),
                exp(33, "Food", day(2026, 10, 31))), new ArrayList<Budget>(), LATER);
        assertTrue(d.barsTruncated);
        assertEquals(22, d.totalBarMonths);
        assertEquals(12, d.bars.size());
        assertEquals("2025-11", d.bars.get(0).monthKey);
        assertEquals("2026-10", d.bars.get(11).monthKey);
        assertEquals(tk(22), d.bars.get(0).expensePaisa);
        assertEquals(tk(33), d.bars.get(11).expensePaisa);
        assertEquals(tk(66), d.current.expensePaisa); // the summary still covers the whole range
    }

    // ------------------------------------------------------------------ spending trend

    @Test
    public void dailyTrendForAMonth() {
        ReportData d = octReport(list(
                exp(100, "Food", day(2026, 10, 1)),
                exp(50, "Food", day(2026, 10, 1)),
                exp(200, "Food", day(2026, 10, 17)),
                exp(70, "Food", at(2026, 10, 31, 23, 0)),
                inc(9999, "Salary", day(2026, 10, 17)))); // income is not spending
        assertEquals(ReportData.Granularity.DAY, d.trendGranularity);
        assertEquals(31, d.trend.size());
        assertEquals("1 Oct", d.trend.get(0).label);
        assertEquals("31 Oct", d.trend.get(30).label);
        assertEquals(tk(150), d.trend.get(0).expensePaisa);
        assertEquals(0, d.trend.get(1).expensePaisa);
        assertEquals(tk(200), d.trend.get(16).expensePaisa);
        assertEquals(tk(70), d.trend.get(30).expensePaisa);
        long sum = 0;
        for (ReportData.TrendPoint t : d.trend) sum += t.expensePaisa;
        assertEquals(d.current.expensePaisa, sum);
    }

    @Test
    public void trendStopsAtTodayWhenTheMonthIsStillInProgress() {
        long now = at(2026, 10, 17, 12, 0);
        ReportData d = report(ReportPeriod.month("2026-10"), list(
                exp(100, "Food", day(2026, 10, 5))), new ArrayList<Budget>(), now);
        assertEquals(17, d.trend.size());
        assertEquals("17 Oct", d.trend.get(16).label);
    }

    @Test
    public void weeklyTrendForAQuarter() {
        ReportPeriod p = ReportPeriod.custom(day(2026, 1, 1), day(2026, 3, 31)); // 90 days
        ReportData d = report(p, list(
                        exp(1, "Food", day(2026, 1, 7)),    // day 7  -> week 1
                        exp(2, "Food", day(2026, 1, 8)),    // day 8  -> week 2
                        exp(4, "Food", day(2026, 3, 31))),  // day 90 -> week 13 (a 6-day tail)
                new ArrayList<Budget>(), LATER);
        assertEquals(ReportData.Granularity.WEEK, d.trendGranularity);
        assertEquals(13, d.trend.size());
        assertEquals(tk(1), d.trend.get(0).expensePaisa);
        assertEquals(tk(2), d.trend.get(1).expensePaisa);
        assertEquals(tk(4), d.trend.get(12).expensePaisa);
        assertEquals("1 Jan", d.trend.get(0).label);
        assertEquals("8 Jan", d.trend.get(1).label);
    }

    @Test
    public void monthlyTrendForVeryLongRangesStartsWithAPartialMonth() {
        ReportPeriod p = ReportPeriod.custom(day(2025, 1, 20), day(2026, 3, 10)); // 415 days
        ReportData d = report(p, list(
                exp(5, "Food", day(2025, 1, 25)),
                exp(9, "Food", day(2026, 3, 10))), new ArrayList<Budget>(), LATER);
        assertEquals(ReportData.Granularity.MONTH, d.trendGranularity);
        assertEquals(15, d.trend.size()); // Jan 2025 .. Mar 2026
        assertEquals("Jan 25", d.trend.get(0).label);
        assertEquals(tk(5), d.trend.get(0).expensePaisa);
        assertEquals("Mar 26", d.trend.get(14).label);
        assertEquals(tk(9), d.trend.get(14).expensePaisa);
    }

    @Test
    public void granularityThresholds() {
        assertEquals(ReportData.Granularity.DAY, ReportCalculator.granularityFor(45));
        assertEquals(ReportData.Granularity.WEEK, ReportCalculator.granularityFor(46));
        assertEquals(ReportData.Granularity.WEEK, ReportCalculator.granularityFor(400));
        assertEquals(ReportData.Granularity.MONTH, ReportCalculator.granularityFor(401));
    }

    // ------------------------------------------------------------------ largest transactions

    @Test
    public void largestExpensesAreRankedWithDeterministicTieBreaks() {
        Transaction a = exp(500, "Food", day(2026, 10, 5));
        Transaction big = exp(900, "Rent", day(2026, 10, 2));
        Transaction b = exp(500, "Shopping", day(2026, 10, 5));  // same amount and date as a, higher id
        Transaction c = exp(500, "Health", day(2026, 10, 7));    // same amount, most recent
        Transaction e1 = exp(300, "Food", day(2026, 10, 8));
        Transaction e2 = exp(200, "Food", day(2026, 10, 9));
        Transaction e3 = exp(100, "Food", day(2026, 10, 10));
        Transaction salary = inc(8000, "Salary", day(2026, 10, 1));
        Transaction gift = inc(100, "Gift", day(2026, 10, 3));
        ReportData d = octReport(list(a, big, b, c, e1, e2, e3, salary, gift));

        assertEquals(5, d.largestExpenses.size());
        assertEquals(Arrays.asList(big, c, b, a, e1), d.largestExpenses);
        assertFalse(d.largestExpenses.contains(salary)); // income never appears among expenses
        assertEquals(Arrays.asList(salary, gift), d.largestIncome);
    }

    // ------------------------------------------------------------------ averages, projection, busiest day

    @Test
    public void averageDailySpendingForACompletedMonth() {
        ReportData d = octReport(list(exp(3100, "Food", day(2026, 10, 5))));
        assertEquals(31, d.elapsedDays);
        assertEquals(tk(100), d.avgDailyExpensePaisa);
        assertEquals(-1, d.projectedMonthExpensePaisa);
        assertTrue(d.insights.contains("Average spending: \u09F3 100.00 per day (31 days)."));
    }

    @Test
    public void currentMonthUsesElapsedDaysAndProjectsMonthEnd() {
        long now = at(2026, 10, 17, 12, 0);
        ReportData d = report(ReportPeriod.month("2026-10"), list(
                        exp(1700, "Food", day(2026, 10, 5)),
                        exp(1000, "Rent", day(2026, 10, 25))), // dated in the future: in totals, not in the pace
                new ArrayList<Budget>(), now);
        assertEquals(17, d.elapsedDays);
        assertEquals(tk(2700), d.current.expensePaisa);
        assertEquals(tk(100), d.avgDailyExpensePaisa);          // 1,700 / 17 days
        assertEquals(tk(3100), d.projectedMonthExpensePaisa);   // 100 per day x 31 days
        assertTrue(d.insights.contains("Average spending: \u09F3 100.00 per day (17 of 31 days so far)."));
        assertTrue(d.insights.contains(
                "At this pace you'll spend about \u09F3 3,100.00 by the end of October 2026."));
    }

    @Test
    public void projectionNeverFallsBelowWhatIsAlreadyRecorded() {
        long now = at(2026, 10, 17, 12, 0);
        ReportData d = report(ReportPeriod.month("2026-10"), list(
                exp(100, "Food", day(2026, 10, 5)),
                exp(5000, "Rent", day(2026, 10, 30))), new ArrayList<Budget>(), now);
        assertEquals(tk(5100), d.projectedMonthExpensePaisa);
    }

    @Test
    public void noProjectionForCustomRangesOrFutureMonths() {
        long now = at(2026, 10, 17, 12, 0);
        ReportData custom = report(ReportPeriod.custom(day(2026, 10, 1), day(2026, 10, 31)),
                list(exp(100, "Food", day(2026, 10, 5))), new ArrayList<Budget>(), now);
        assertEquals(-1, custom.projectedMonthExpensePaisa);
        ReportData future = report(ReportPeriod.month("2026-12"), list(exp(100, "Food", day(2026, 12, 5))),
                new ArrayList<Budget>(), now);
        assertEquals(0, future.elapsedDays);
        assertEquals(0, future.avgDailyExpensePaisa);
        assertEquals(-1, future.projectedMonthExpensePaisa);
        assertTrue(future.trend.isEmpty());
    }

    @Test
    public void highestSpendingDayAddsUpAllExpensesOnThatDay() {
        ReportData d = octReport(list(
                exp(100, "Food", day(2026, 10, 5)),
                exp(200, "Rent", at(2026, 10, 5, 18, 0)),
                exp(250, "Food", day(2026, 10, 9)),
                exp(300, "Food", day(2026, 10, 12))));  // ties with the 5th: the earlier day wins
        assertEquals(day(2026, 10, 5), d.highestSpendDayMillis);
        assertEquals(tk(300), d.highestSpendDayPaisa);
        assertTrue(d.insights.contains("Highest spending day: 05 Oct 2026 (\u09F3 300.00)."));
    }

    // ------------------------------------------------------------------ budget comparison

    @Test
    public void singleMonthBudgetsComparedWithRealSpending() {
        List<Budget> budgets = new ArrayList<>(Arrays.asList(
                budget("2026-10", Budget.OVERALL, 20000),
                budget("2026-10", "Food", 3000),
                budget("2026-10", "Transport", 1000),
                budget("2026-10", "Health", 10000),
                budget("2026-09", "Food", 1)));   // another month: must be ignored
        ReportData d = report(ReportPeriod.month("2026-10"), list(
                        exp(2400, "Food", day(2026, 10, 2)),        // exactly 80%
                        exp(1050, "Transport", day(2026, 10, 3)),   // 105%
                        exp(7999, "Health", day(2026, 10, 4)),      // 79.99% must NOT look like 80%
                        exp(5000, "Rent", day(2026, 10, 5)),        // no category budget
                        inc(20000, "Salary", day(2026, 10, 1))),    // income never counts against a budget
                budgets, LATER);

        assertTrue(d.budget.periodSupported);
        assertEquals(1, d.budget.monthsWithBudgets);
        assertEquals(4, d.budget.rows.size());

        ReportData.BudgetRow overall = d.budget.rows.get(0);
        assertTrue(overall.overall);
        assertEquals(tk(20000), overall.status.budgetPaisa);
        assertEquals(tk(16449), overall.status.spentPaisa); // 2400 + 1050 + 7999 + 5000 = 16,449
        assertEquals(BudgetStatus.Level.WARNING, overall.status.level); // 82.2% used
        assertEquals("82.2", overall.status.percentText);

        // categories follow, most-used first: Transport 105%, Food 80%, Health 79.9%
        ReportData.BudgetRow transport = d.budget.rows.get(1);
        assertEquals("Transport", transport.label);
        assertTrue(transport.status.isOverspent());
        assertEquals(tk(-50), transport.status.remainingPaisa);

        ReportData.BudgetRow food = d.budget.rows.get(2);
        assertEquals("Food", food.label);
        assertEquals(BudgetStatus.Level.WARNING, food.status.level);
        assertEquals("80", food.status.percentText);

        ReportData.BudgetRow health = d.budget.rows.get(3);
        assertEquals("Health", health.label);
        assertEquals(BudgetStatus.Level.NORMAL, health.status.level);
        assertEquals("79.9", health.status.percentText);
    }

    @Test
    public void multiMonthBudgetsAreSummedOnlyOverMonthsWhereTheyExist() {
        List<Budget> budgets = new ArrayList<>(Arrays.asList(
                budget("2026-09", "Food", 1000),
                budget("2026-10", "Food", 2000),
                budget("2026-10", Budget.OVERALL, 5000),
                budget("2026-09", "Transport", 500)));
        ReportPeriod p = ReportPeriod.custom(day(2026, 9, 1), day(2026, 10, 31));
        ReportData d = report(p, list(
                        exp(800, "Food", day(2026, 9, 5)),
                        exp(700, "Rent", day(2026, 9, 6)),
                        exp(1500, "Food", day(2026, 10, 5)),
                        exp(1000, "Rent", day(2026, 10, 6)),
                        exp(100, "Transport", day(2026, 9, 7)),
                        exp(900, "Transport", day(2026, 10, 8))), // October has no Transport budget
                budgets, LATER);

        assertTrue(d.budget.periodSupported);
        assertEquals(2, d.budget.monthsWithBudgets);
        ReportData.BudgetRow overall = d.budget.rows.get(0);
        assertTrue(overall.overall);
        assertEquals(tk(5000), overall.status.budgetPaisa);
        // September had no overall budget, so only October's spending (1500 + 1000 + 900) is compared
        assertEquals(tk(3400), overall.status.spentPaisa);

        ReportData.BudgetRow food = null, transport = null;
        for (ReportData.BudgetRow r : d.budget.rows) {
            if ("Food".equals(r.label)) food = r;
            if ("Transport".equals(r.label)) transport = r;
        }
        assertNotNull(food);
        assertEquals(tk(3000), food.status.budgetPaisa);
        assertEquals(tk(2300), food.status.spentPaisa);
        assertEquals("76.6", food.status.percentText); // 76.666 rounded down, never up
        assertNotNull(transport);
        assertEquals(tk(500), transport.status.budgetPaisa);
        assertEquals(tk(100), transport.status.spentPaisa); // October's 900 had no budget to count against
    }

    @Test
    public void budgetComparisonIsUnavailableForPartialMonths() {
        List<Budget> budgets = list2(budget("2026-10", Budget.OVERALL, 5000));
        ReportData d = report(ReportPeriod.custom(day(2026, 10, 5), day(2026, 10, 20)),
                list(exp(100, "Food", day(2026, 10, 6))), budgets, LATER);
        assertFalse(d.budget.periodSupported);
        assertFalse(d.budget.hasBudgets());
    }

    @Test
    public void noBudgetsMeansAnEmptySupportedBudgetReport() {
        ReportData d = octReport(list(exp(100, "Food", day(2026, 10, 6))));
        assertTrue(d.budget.periodSupported);
        assertFalse(d.budget.hasBudgets());
        assertEquals(0, d.budget.monthsWithBudgets);
    }

    @Test
    public void zeroOrNegativeBudgetRowsAreIgnored() {
        Budget zero = budget("2026-10", "Food", 0);
        ReportData d = report(ReportPeriod.month("2026-10"), list(exp(100, "Food", day(2026, 10, 6))),
                list2(zero), LATER);
        assertFalse(d.budget.hasBudgets());
    }

    private static List<Budget> list2(Budget... b) {
        return new ArrayList<>(Arrays.asList(b));
    }

    // ------------------------------------------------------------------ numeric safety

    @Test
    public void savingsRateDoesNotOverflowWithVeryLargeTotals() {
        List<Transaction> txs = new ArrayList<>();
        // 100,000 incomes and 50,000 expenses of the maximum allowed amount: sums reach ~1e16 paisa,
        // and (net x 1000) would overflow a long if the ratio were computed naively.
        for (int i = 0; i < 100_000; i++) {
            txs.add(txPaisa(Transaction.TYPE_INCOME, MoneyUtils.MAX_PAISA, "Salary", day(2026, 10, 2)));
        }
        for (int i = 0; i < 50_000; i++) {
            txs.add(txPaisa(Transaction.TYPE_EXPENSE, MoneyUtils.MAX_PAISA, "Rent", day(2026, 10, 3)));
        }
        ReportData d = octReport(txs);
        assertEquals(100_000L * MoneyUtils.MAX_PAISA, d.current.incomePaisa);
        assertEquals(50_000L * MoneyUtils.MAX_PAISA, d.current.expensePaisa);
        assertEquals(Long.valueOf(500), d.current.savingsRateTenths); // exactly 50.0%
    }

    @Test
    public void ratioAndChangeRoundingIsHalfUpAwayFromZero() {
        assertEquals(125, ReportCalculator.ratioTenths(1, 8));    // 12.5% exactly
        assertEquals(63, ReportCalculator.ratioTenths(1, 16));    // 6.25% -> 6.3%
        assertEquals(-63, ReportCalculator.ratioTenths(-1, 16));  // symmetric for negatives
        assertEquals(333, ReportCalculator.ratioTenths(1, 3));
        assertEquals(667, ReportCalculator.ratioTenths(2, 3));
        assertEquals(-500, ReportCalculator.ratioTenths(5, -10));
        assertEquals(Long.valueOf(500), ReportCalculator.changeTenths(150, 100));
        assertEquals(Long.valueOf(-500), ReportCalculator.changeTenths(50, 100));
        assertNull(ReportCalculator.changeTenths(10, 0));
        // from a negative base the sign still follows the direction of the change
        assertEquals(Long.valueOf(500), ReportCalculator.changeTenths(-50, -100));
        assertEquals(Long.valueOf(-500), ReportCalculator.changeTenths(-150, -100));
    }

    // ------------------------------------------------------------------ daylight saving

    @Test
    public void everyDayLandsInItsOwnTrendBucketInADaylightSavingZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        List<Transaction> txs = new ArrayList<>();
        for (int d = 1; d <= 31; d++) txs.add(exp(d, "Food", day(2026, 3, d))); // DST began on 8 March
        ReportData r = report(ReportPeriod.month("2026-03"), txs, new ArrayList<Budget>(), day(2026, 4, 5));
        assertEquals(31, r.trend.size());
        for (int i = 0; i < 31; i++) {
            assertEquals("day " + (i + 1), tk(i + 1), r.trend.get(i).expensePaisa);
        }
        assertEquals(31, r.elapsedDays);
        assertEquals(tk(496), r.current.expensePaisa); // 1 + 2 + ... + 31

        // the November change back ("fall back", a 25-hour day)
        List<Transaction> nov = new ArrayList<>();
        for (int d = 1; d <= 3; d++) nov.add(exp(d * 10, "Food", day(2026, 11, d)));
        ReportData n = report(ReportPeriod.custom(day(2026, 11, 1), day(2026, 11, 3)), nov,
                new ArrayList<Budget>(), day(2026, 12, 1));
        assertEquals(3, n.trend.size());
        assertEquals(tk(10), n.trend.get(0).expensePaisa);
        assertEquals(tk(20), n.trend.get(1).expensePaisa);
        assertEquals(tk(30), n.trend.get(2).expensePaisa);
    }

    @Test
    public void rowsOutsideEveryWindowAreIgnoredSoPassingExtraDataIsHarmless() {
        ReportData d = octReport(list(
                exp(100, "Food", day(2020, 1, 1)),
                exp(100, "Food", day(2026, 10, 10)),
                exp(100, "Food", day(2035, 1, 1))));
        assertEquals(tk(100), d.current.expensePaisa);
        assertEquals(0, d.previous.expensePaisa);
    }

    // ------------------------------------------------------------------ data window used by the repository

    @Test
    public void dataWindowCoversPreviousPeriodAndBarChartMonths() {
        assertEquals(day(2026, 9, 1), ReportCalculator.dataWindowStart(ReportPeriod.month("2027-02")));
        assertEquals(day(2026, 11, 1), ReportCalculator.dataWindowStart(ReportPeriod.month("2027-04")));
        assertEquals(day(2026, 10, 7), ReportCalculator.dataWindowStart(
                ReportPeriod.custom(day(2026, 10, 10), day(2026, 10, 12))));
    }

    @Test
    public void loadingOnlyTheDataWindowGivesTheSameReportAsLoadingEverything() {
        List<Transaction> everything = new ArrayList<>();
        String[] cats = {"Food", "Transport", "Rent", "Health"};
        for (int i = 0; i < 400; i++) {
            long date = MonthUtils.addDays(day(2025, 9, 1), i * 2);
            everything.add(i % 5 == 0 ? inc(1000 + i, "Salary", date) : exp(10 + (i % 37) * 3, cats[i % 4], date));
        }
        ReportPeriod[] periods = {
                ReportPeriod.month("2026-03"),
                ReportPeriod.month("2026-01"),
                ReportPeriod.custom(day(2026, 2, 14), day(2026, 3, 29)),
                ReportPeriod.custom(day(2025, 10, 1), day(2026, 6, 30))};
        for (ReportPeriod p : periods) {
            List<Transaction> windowed = new ArrayList<>();
            long from = ReportCalculator.dataWindowStart(p);
            for (Transaction t : everything) {
                if (t.dateMillis >= from && t.dateMillis < p.endMillis) windowed.add(t);
            }
            ReportData a = report(p, everything, new ArrayList<Budget>(), LATER);
            ReportData b = report(p, windowed, new ArrayList<Budget>(), LATER);
            assertEquals(p.toString(), a.current.incomePaisa, b.current.incomePaisa);
            assertEquals(p.toString(), a.current.expensePaisa, b.current.expensePaisa);
            assertEquals(p.toString(), a.previous.incomePaisa, b.previous.incomePaisa);
            assertEquals(p.toString(), a.previous.expensePaisa, b.previous.expensePaisa);
            assertEquals(a.bars.size(), b.bars.size());
            for (int i = 0; i < a.bars.size(); i++) {
                assertEquals(a.bars.get(i).incomePaisa, b.bars.get(i).incomePaisa);
                assertEquals(a.bars.get(i).expensePaisa, b.bars.get(i).expensePaisa);
            }
            assertEquals(a.categories.size(), b.categories.size());
            assertEquals(a.trend.size(), b.trend.size());
            assertEquals(a.insights, b.insights);
        }
    }
}

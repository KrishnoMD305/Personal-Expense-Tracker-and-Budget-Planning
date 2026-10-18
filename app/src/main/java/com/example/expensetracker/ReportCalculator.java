package com.example.expensetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class ReportCalculator {
    public static final String UNCATEGORIZED = "Uncategorized";
    public static final int MONTH_MODE_BAR_MONTHS = 6;
    public static final int MAX_BAR_MONTHS = 12;
    public static final int TOP_EXPENSES = 5;
    public static final int TOP_INCOMES = 3;
    static final int DAILY_TREND_MAX_DAYS = 45;
    static final int WEEKLY_TREND_MAX_DAYS = 400;
    private static final double DAY_MS = 24d * 60 * 60 * 1000;
    private ReportCalculator() {}

    public static ReportData build(ReportPeriod period, List<Transaction> all, List<Budget> budgets, long nowMillis) {
        ReportPeriod prev = period.previous();

        List<Transaction> cur = new ArrayList<>();
        List<Transaction> prv = new ArrayList<>();
        List<Transaction> valid = new ArrayList<>();
        for (Transaction t : all) {
            if (t == null || !isCounted(t)) continue;
            valid.add(t);
            if (period.contains(t.dateMillis)) cur.add(t);
            else if (prev.contains(t.dateMillis)) prv.add(t);
        }

        ReportData.Summary curSummary = summarize(cur);
        ReportData.Summary prevSummary = summarize(prv);
        String previousLabel = period.type == ReportPeriod.Type.MONTH
                ? MonthUtils.label(prev.monthKey)
                : "the previous " + period.dayCount() + (period.dayCount() == 1 ? " day" : " days");

        List<ReportData.CategorySlice> categories = buildCategories(cur, prv, curSummary.expensePaisa);

        ReportPeriod.Type type = period.type;
        long barStart;
        long barEnd = period.endMillis;
        String lastKey = period.lastMonthKey();
        String firstKey;
        boolean truncated = false;
        int totalMonths;
        if (type == ReportPeriod.Type.MONTH) {
            firstKey = clampedShift(period.monthKey, -(MONTH_MODE_BAR_MONTHS - 1));
            barStart = MonthUtils.startMillis(firstKey);
            totalMonths = MonthUtils.monthsBetween(firstKey, lastKey) + 1;
        } else {
            firstKey = period.firstMonthKey();
            barStart = period.startMillis;
            totalMonths = MonthUtils.monthsBetween(firstKey, lastKey) + 1;
            if (totalMonths > MAX_BAR_MONTHS) {
                truncated = true;
                firstKey = MonthUtils.shift(lastKey, -(MAX_BAR_MONTHS - 1));
                barStart = MonthUtils.startMillis(firstKey);
            }
        }
        List<ReportData.MonthBar> bars = buildBars(valid, firstKey, lastKey, barStart, barEnd);

        ReportData.Granularity granularity = granularityFor(period.dayCount());
        List<ReportData.TrendPoint> trend = buildTrend(period, cur, granularity, nowMillis);

        List<Transaction> expensesDesc = new ArrayList<>();
        List<Transaction> incomesDesc = new ArrayList<>();
        for (Transaction t : cur) {
            if (t.isIncome()) incomesDesc.add(t);
            else expensesDesc.add(t);
        }
        Collections.sort(expensesDesc, LARGEST_FIRST);
        Collections.sort(incomesDesc, LARGEST_FIRST);
        List<Transaction> largestExpenses = new ArrayList<>(
                expensesDesc.subList(0, Math.min(TOP_EXPENSES, expensesDesc.size())));
        List<Transaction> largestIncome = new ArrayList<>(
                incomesDesc.subList(0, Math.min(TOP_INCOMES, incomesDesc.size())));

        ReportData.CategoryChange biggestIncrease = null;
        ReportData.CategoryChange biggestDecrease = null;
        if (prevSummary.expensePaisa > 0) {
            Map<String, Long> curByCat = new HashMap<>();
            Map<String, Long> prevByCat = new HashMap<>();
            for (Transaction t : cur) if (!t.isIncome()) addTo(curByCat, normalize(t.category), t.amountPaisa);
            for (Transaction t : prv) if (!t.isIncome()) addTo(prevByCat, normalize(t.category), t.amountPaisa);
            Set<String> names = new HashSet<>(curByCat.keySet());
            names.addAll(prevByCat.keySet());
            List<String> sortedNames = new ArrayList<>(names);
            Collections.sort(sortedNames);
            for (String name : sortedNames) {
                long c = valueOf(curByCat, name);
                long p = valueOf(prevByCat, name);
                long delta = c - p;
                ReportData.CategoryChange change = new ReportData.CategoryChange(
                        name, c, p, delta, changeTenths(c, p));
                if (delta > 0 && (biggestIncrease == null || delta > biggestIncrease.deltaPaisa)) {
                    biggestIncrease = change;
                }
                if (delta < 0 && (biggestDecrease == null || delta < biggestDecrease.deltaPaisa)) {
                    biggestDecrease = change;
                }
            }
        }

        int dayCount = period.dayCount();
        int elapsed = elapsedDays(period, nowMillis);
        long tomorrowStart = MonthUtils.startOfDay(MonthUtils.addDays(MonthUtils.startOfDay(nowMillis), 1));
        long cutoff = Math.min(period.endMillis, tomorrowStart);
        long expenseToDate = 0;
        Map<Long, Long> expenseByDay = new HashMap<>();
        for (Transaction t : cur) {
            if (t.isIncome()) continue;
            if (t.dateMillis < cutoff) expenseToDate += t.amountPaisa;
            addTo(expenseByDay, MonthUtils.startOfDay(t.dateMillis), t.amountPaisa);
        }
        long avgDaily = elapsed > 0 ? divideRounded(expenseToDate, elapsed) : 0;
        long projected = -1;
        if (type == ReportPeriod.Type.MONTH && elapsed > 0 && elapsed < dayCount && expenseToDate > 0) {
            projected = Math.max(curSummary.expensePaisa, divideRounded(expenseToDate * dayCount, elapsed));
        }
        long topDayMillis = -1;
        long topDayPaisa = 0;
        List<Long> dayKeys = new ArrayList<>(expenseByDay.keySet());
        Collections.sort(dayKeys);
        for (Long day : dayKeys) {
            long amount = expenseByDay.get(day);
            if (amount > topDayPaisa) {
                topDayPaisa = amount;
                topDayMillis = day;
            }
        }

        ReportData.BudgetReport budget = buildBudgets(period, budgets, cur);

        List<String> insights = buildInsights(period, previousLabel, curSummary, prevSummary,
                biggestIncrease, biggestDecrease, elapsed, dayCount, avgDaily, expenseToDate,
                projected, topDayMillis, topDayPaisa);

        return new ReportData(period, prev, previousLabel, curSummary, prevSummary, categories, bars,
                truncated, totalMonths, granularity, trend, largestExpenses, largestIncome,
                biggestIncrease, biggestDecrease, elapsed, avgDaily, projected, topDayMillis,
                topDayPaisa, insights, budget);
    }

    public static long dataWindowStart(ReportPeriod period) {
        long prevStart = period.previous().startMillis;
        if (period.type == ReportPeriod.Type.MONTH) {
            String firstBarKey = clampedShift(period.monthKey, -(MONTH_MODE_BAR_MONTHS - 1));
            return Math.min(prevStart, MonthUtils.startMillis(firstBarKey));
        }
        return prevStart;
    }

    static boolean isCounted(Transaction t) {
        return t.isIncome() || Transaction.TYPE_EXPENSE.equals(t.type);
    }

    static ReportData.Summary summarize(List<Transaction> txs) {
        long income = 0, expense = 0;
        int incomeCount = 0, expenseCount = 0;
        for (Transaction t : txs) {
            if (t.isIncome()) {
                income += t.amountPaisa;
                incomeCount++;
            } else if (Transaction.TYPE_EXPENSE.equals(t.type)) {
                expense += t.amountPaisa;
                expenseCount++;
            }
        }
        return summary(income, expense, incomeCount, expenseCount);
    }

    static ReportData.Summary summary(long income, long expense, int incomeCount, int expenseCount) {
        long net = income - expense;
        return new ReportData.Summary(income, expense, incomeCount, expenseCount, net,
                savingsRateTenths(income, expense));
    }

    public static Long savingsRateTenths(long incomePaisa, long expensePaisa) {
        if (incomePaisa <= 0) return null;
        return ratioTenths(incomePaisa - expensePaisa, incomePaisa);
    }
    public static long ratioTenths(long part, long whole) {
        if (whole == 0) throw new ArithmeticException("whole is zero");
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(Math.abs(whole)), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(whole < 0 ? -1 : 1)).longValue();
    }

    public static Long changeTenths(long current, long previous) {
        if (previous == 0) return null;
        return BigDecimal.valueOf(current - previous).multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(Math.abs(previous)), 0, RoundingMode.HALF_UP).longValue();
    }

    static long divideRounded(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).longValue();
    }

    static String normalize(String category) {
        return category == null || category.trim().isEmpty() ? UNCATEGORIZED : category;
    }

    private static List<ReportData.CategorySlice> buildCategories(List<Transaction> cur, List<Transaction> prv, long totalExpense) {
        Map<String, Long> amount = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Transaction t : cur) {
            if (t.isIncome()) continue;
            String c = normalize(t.category);
            addTo(amount, c, t.amountPaisa);
            Integer n = count.get(c);
            count.put(c, n == null ? 1 : n + 1);
        }
        Map<String, Long> previous = new HashMap<>();
        for (Transaction t : prv) if (!t.isIncome()) addTo(previous, normalize(t.category), t.amountPaisa);

        List<ReportData.CategorySlice> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : amount.entrySet()) {
            long a = e.getValue();
            long p = valueOf(previous, e.getKey());
            long share = totalExpense > 0 ? ratioTenths(a, totalExpense) : 0;
            out.add(new ReportData.CategorySlice(e.getKey(), a, count.get(e.getKey()), share, p,
                    changeTenths(a, p)));
        }
        Collections.sort(out, new Comparator<ReportData.CategorySlice>() {
            @Override
            public int compare(ReportData.CategorySlice x, ReportData.CategorySlice y) {
                int byAmount = Long.compare(y.amountPaisa, x.amountPaisa);
                return byAmount != 0 ? byAmount : x.category.compareTo(y.category);
            }
        });
        return out;
    }

    private static String clampedShift(String key, int delta) {
        String k = MonthUtils.shift(key, delta);
        return MonthUtils.isValidKey(k) ? k : "1970-01";
    }

    private static List<ReportData.MonthBar> buildBars(List<Transaction> valid, String firstKey, String lastKey, long from, long to) {
        int months = MonthUtils.monthsBetween(firstKey, lastKey) + 1;
        long[] income = new long[months];
        long[] expense = new long[months];
        for (Transaction t : valid) {
            if (t.dateMillis < from || t.dateMillis >= to) continue;
            int idx = MonthUtils.monthsBetween(firstKey, MonthUtils.keyOf(t.dateMillis));
            if (idx < 0 || idx >= months) continue;
            if (t.isIncome()) income[idx] += t.amountPaisa;
            else expense[idx] += t.amountPaisa;
        }
        List<ReportData.MonthBar> bars = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            bars.add(new ReportData.MonthBar(MonthUtils.shift(firstKey, i), income[i], expense[i],
                    income[i] - expense[i], savingsRateTenths(income[i], expense[i])));
        }
        return bars;
    }

    static ReportData.Granularity granularityFor(int dayCount) {
        if (dayCount <= DAILY_TREND_MAX_DAYS) return ReportData.Granularity.DAY;
        if (dayCount <= WEEKLY_TREND_MAX_DAYS) return ReportData.Granularity.WEEK;
        return ReportData.Granularity.MONTH;
    }

    static long[] bucketBounds(ReportPeriod p, ReportData.Granularity g) {
        List<Long> b = new ArrayList<>();
        b.add(p.startMillis);
        if (g == ReportData.Granularity.MONTH) {
            String key = p.firstMonthKey();
            while (true) {
                long next = MonthUtils.endMillis(key);
                if (next >= p.endMillis) break;
                b.add(next);
                key = MonthUtils.shift(key, 1);
            }
        } else {
            int step = g == ReportData.Granularity.DAY ? 1 : 7;
            long cur = p.startMillis;
            while (true) {
                cur = MonthUtils.startOfDay(MonthUtils.addDays(cur, step));
                if (cur >= p.endMillis) break;
                b.add(cur);
            }
        }
        b.add(p.endMillis);
        long[] out = new long[b.size()];
        for (int i = 0; i < out.length; i++) out[i] = b.get(i);
        return out;
    }

    private static List<ReportData.TrendPoint> buildTrend(ReportPeriod p, List<Transaction> cur,
                                                          ReportData.Granularity g, long now) {
        long[] bounds = bucketBounds(p, g);
        int n = bounds.length - 1;
        long[] sums = new long[n];
        for (Transaction t : cur) {
            if (t.isIncome()) continue;
            int idx = Arrays.binarySearch(bounds, t.dateMillis);
            if (idx < 0) idx = -idx - 2;
            if (idx >= n) idx = n - 1;
            if (idx < 0) continue;
            sums[idx] += t.amountPaisa;
        }
        SimpleDateFormat dayFormat = new SimpleDateFormat("d MMM", Locale.getDefault());
        dayFormat.setTimeZone(TimeZone.getDefault());
        List<ReportData.TrendPoint> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (bounds[i] > now) break;
            String label = g == ReportData.Granularity.MONTH
                    ? MonthUtils.shortLabel(MonthUtils.keyOf(bounds[i]))
                    : dayFormat.format(new java.util.Date(bounds[i]));
            out.add(new ReportData.TrendPoint(bounds[i], label, sums[i]));
        }
        return out;
    }

    static int elapsedDays(ReportPeriod p, long now) {
        if (now < p.startMillis) return 0;
        if (now >= p.endMillis) return p.dayCount();
        long today = MonthUtils.startOfDay(now);
        long days = Math.round((today - p.startMillis) / DAY_MS) + 1;
        return (int) Math.min(p.dayCount(), days);
    }

    private static ReportData.BudgetReport buildBudgets(ReportPeriod period, List<Budget> budgets,
                                                        List<Transaction> cur) {
        if (!period.isWholeCalendarMonths()) {
            return new ReportData.BudgetReport(false, new ArrayList<ReportData.BudgetRow>(), 0);
        }
        String first = period.firstMonthKey();
        String last = period.lastMonthKey();

        Map<String, Long> overallByMonth = new HashMap<>();
        Map<String, Map<String, Long>> categoryByMonth = new HashMap<>();
        Set<String> monthsWithBudget = new HashSet<>();
        for (Budget b : budgets) {
            if (b == null || b.monthKey == null || b.amountPaisa <= 0) continue;
            if (b.monthKey.compareTo(first) < 0 || b.monthKey.compareTo(last) > 0) continue;
            monthsWithBudget.add(b.monthKey);
            if (b.isOverall()) {
                overallByMonth.put(b.monthKey, b.amountPaisa);
            } else {
                Map<String, Long> m = categoryByMonth.get(b.monthKey);
                if (m == null) {
                    m = new HashMap<>();
                    categoryByMonth.put(b.monthKey, m);
                }
                m.put(b.category, b.amountPaisa);
            }
        }

        Map<String, Long> totalByMonth = new HashMap<>();
        Map<String, Map<String, Long>> spentByMonth = new HashMap<>();
        for (Transaction t : cur) {
            if (t.isIncome()) continue;
            String month = MonthUtils.keyOf(t.dateMillis);
            addTo(totalByMonth, month, t.amountPaisa);
            Map<String, Long> m = spentByMonth.get(month);
            if (m == null) {
                m = new HashMap<>();
                spentByMonth.put(month, m);
            }
            addTo(m, t.category == null ? "" : t.category, t.amountPaisa);
        }

        List<ReportData.BudgetRow> rows = new ArrayList<>();
        if (!overallByMonth.isEmpty()) {
            long budgetSum = 0, spentSum = 0;
            for (Map.Entry<String, Long> e : overallByMonth.entrySet()) {
                budgetSum += e.getValue();
                spentSum += valueOf(totalByMonth, e.getKey());
            }
            rows.add(new ReportData.BudgetRow(BudgetAlerts.OVERALL_LABEL, true,
                    BudgetCalculator.status(budgetSum, spentSum)));
        }

        Map<String, long[]> perCategory = new HashMap<>(); // category -> {budget, spent}
        for (Map.Entry<String, Map<String, Long>> monthEntry : categoryByMonth.entrySet()) {
            Map<String, Long> spent = spentByMonth.get(monthEntry.getKey());
            for (Map.Entry<String, Long> c : monthEntry.getValue().entrySet()) {
                long[] acc = perCategory.get(c.getKey());
                if (acc == null) {
                    acc = new long[2];
                    perCategory.put(c.getKey(), acc);
                }
                acc[0] += c.getValue();
                acc[1] += spent == null ? 0 : valueOf(spent, c.getKey());
            }
        }
        List<ReportData.BudgetRow> categoryRows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : perCategory.entrySet()) {
            categoryRows.add(new ReportData.BudgetRow(e.getKey(), false,
                    BudgetCalculator.status(e.getValue()[0], e.getValue()[1])));
        }
        Collections.sort(categoryRows, new Comparator<ReportData.BudgetRow>() {
            @Override
            public int compare(ReportData.BudgetRow a, ReportData.BudgetRow b) {
                BigDecimal ra = BigDecimal.valueOf(a.status.spentPaisa)
                        .multiply(BigDecimal.valueOf(b.status.budgetPaisa));
                BigDecimal rb = BigDecimal.valueOf(b.status.spentPaisa)
                        .multiply(BigDecimal.valueOf(a.status.budgetPaisa));
                int c = rb.compareTo(ra);
                return c != 0 ? c : a.label.compareTo(b.label);
            }
        });
        rows.addAll(categoryRows);
        return new ReportData.BudgetReport(true, rows, monthsWithBudget.size());
    }

    private static List<String> buildInsights(ReportPeriod period, String previousLabel,
                                              ReportData.Summary cur, ReportData.Summary prev,
                                              ReportData.CategoryChange up, ReportData.CategoryChange down,
                                              int elapsed, int dayCount, long avgDaily,
                                              long expenseToDate, long projected,
                                              long topDayMillis, long topDayPaisa) {
        List<String> out = new ArrayList<>();

        if (cur.expensePaisa > 0 || prev.expensePaisa > 0) {
            if (prev.expensePaisa == 0) {
                out.add("No spending was recorded in " + previousLabel + " to compare with.");
            } else if (cur.expensePaisa == 0) {
                out.add("No spending recorded in this period, compared with "
                        + MoneyUtils.format(prev.expensePaisa) + " in " + previousLabel + ".");
            } else {
                long delta = cur.expensePaisa - prev.expensePaisa;
                if (delta == 0) {
                    out.add("Spending is the same as in " + previousLabel + ".");
                } else {
                    out.add("Spending is " + (delta > 0 ? "up " : "down ")
                            + ReportFormat.percentMagnitude(changeTenths(cur.expensePaisa, prev.expensePaisa))
                            + " vs " + previousLabel + " (" + MoneyUtils.format(Math.abs(delta))
                            + (delta > 0 ? " more)." : " less)."));
                }
            }
        }

        if (cur.netPaisa < 0) {
            out.add("Expenses exceeded income by " + MoneyUtils.format(-cur.netPaisa) + ".");
        } else if (cur.savingsRateTenths != null && cur.netPaisa > 0) {
            out.add("You saved " + ReportFormat.percent(cur.savingsRateTenths) + " of your income.");
        }

        if (elapsed > 0 && expenseToDate > 0) {
            String span = elapsed < dayCount
                    ? elapsed + " of " + dayCount + " days so far"
                    : dayCount + (dayCount == 1 ? " day" : " days");
            out.add("Average spending: " + MoneyUtils.format(avgDaily) + " per day (" + span + ").");
        }
        if (projected >= 0) {
            out.add("At this pace you'll spend about " + MoneyUtils.format(projected)
                    + " by the end of " + MonthUtils.label(period.monthKey) + ".");
        }
        if (topDayMillis >= 0) {
            SimpleDateFormat f = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            f.setTimeZone(TimeZone.getDefault());
            out.add("Highest spending day: " + f.format(new java.util.Date(topDayMillis))
                    + " (" + MoneyUtils.format(topDayPaisa) + ").");
        }
        if (up != null) {
            out.add("Biggest increase: " + up.category + ", up " + MoneyUtils.format(up.deltaPaisa)
                    + (up.changeTenths == null ? "" : " (" + ReportFormat.signedPercent(up.changeTenths) + ")")
                    + " vs " + previousLabel + ".");
        }
        if (down != null) {
            out.add("Biggest decrease: " + down.category + ", down " + MoneyUtils.format(-down.deltaPaisa)
                    + (down.changeTenths == null ? "" : " (" + ReportFormat.signedPercent(down.changeTenths) + ")")
                    + " vs " + previousLabel + ".");
        }
        return out;
    }

    private static final Comparator<Transaction> LARGEST_FIRST = new Comparator<Transaction>() {
        @Override
        public int compare(Transaction a, Transaction b) {
            int c = Long.compare(b.amountPaisa, a.amountPaisa);
            if (c != 0) return c;
            c = Long.compare(b.dateMillis, a.dateMillis);
            return c != 0 ? c : Long.compare(b.id, a.id);
        }
    };

    private static void addTo(Map<String, Long> map, String key, long amount) {
        Long prev = map.get(key);
        map.put(key, (prev == null ? 0 : prev) + amount);
    }

    private static void addTo(Map<Long, Long> map, Long key, long amount) {
        Long prev = map.get(key);
        map.put(key, (prev == null ? 0 : prev) + amount);
    }

    private static long valueOf(Map<String, Long> map, String key) {
        Long v = map.get(key);
        return v == null ? 0 : v;
    }
}
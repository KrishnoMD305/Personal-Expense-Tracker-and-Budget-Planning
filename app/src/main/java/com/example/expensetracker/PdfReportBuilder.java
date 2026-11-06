package com.example.expensetracker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Stage 5: turns a {@link ReportData} (plus a snapshot of goals, bills and recurring rules) into the
 * {@link PdfReportModel.Item} list of the PDF financial report. Pure Java; all figures come from the same
 * calculators as the on-screen Reports, so the PDF always agrees with the app.
 */
final class PdfReportBuilder {
    /** Safety limit so a huge history cannot create thousands of pages. */
    static final int MAX_TRANSACTION_ROWS = 1500;
    static final int MAX_LIST_ROWS = 40;
    /** Characters per line used when wrapping prose (conservative for 9.5 pt text on A4). */
    static final int WRAP_CHARS = 100;

    static final class Input {
        ReportData report;
        /** Transactions of the period (any order). */
        List<Transaction> transactions = new ArrayList<>();
        List<GoalWithSaved> goals = new ArrayList<>();
        List<Bill> bills = new ArrayList<>();
        List<RecurringRule> rules = new ArrayList<>();
        long nowMillis;
    }

    private PdfReportBuilder() {}

    static String periodLabel(ReportPeriod p) {
        SimpleDateFormat f = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        if (p.type == ReportPeriod.Type.MONTH) return MonthUtils.label(p.monthKey);
        return f.format(new Date(p.startMillis)) + " - " + f.format(new Date(p.endMillis - 1));
    }

    /** Replaces the taka sign (which some device fonts lack) so every figure in the PDF reads "BDT 1,250.50". */
    static String clean(String s) {
        if (s == null) return "";
        return s.replace(MoneyUtils.SYMBOL + " ", "BDT ").replace(MoneyUtils.SYMBOL, "BDT ");
    }

    static List<PdfReportModel.Item> build(Input in) {
        ReportData r = in.report;
        long today = MonthUtils.startOfDay(in.nowMillis);
        SimpleDateFormat day = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
        SimpleDateFormat stamp = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH);
        List<PdfReportModel.Item> items = new ArrayList<>();

        items.add(PdfReportModel.title("Financial Report"));
        items.add(PdfReportModel.subtitle("Period: " + periodLabel(r.period)));
        items.add(PdfReportModel.subtitle("Generated on " + stamp.format(new Date(in.nowMillis))));
        items.add(PdfReportModel.space(6));

        // ---- summary
        items.add(PdfReportModel.heading("Summary"));
        PdfReportModel.Item kv = PdfReportModel.tableHeader(new String[]{"Measure", "Value"}, new float[]{3f, 2f},
                new boolean[]{false, true});
        items.add(kv);
        ReportData.Summary s = r.current;
        items.add(row(kv, "Total income", MoneyUtils.formatBdt(s.incomePaisa) + "  (" + s.incomeCount + " transactions)"));
        items.add(row(kv, "Total expenses", MoneyUtils.formatBdt(s.expensePaisa) + "  (" + s.expenseCount + " transactions)"));
        items.add(PdfReportModel.tableRow(kv, new String[]{"Net cash flow", MoneyUtils.formatBdt(s.netPaisa)}, -1f, true));
        items.add(row(kv, "Savings rate", ReportFormat.percent(s.savingsRateTenths)));
        items.add(row(kv, "Days in period", String.valueOf(r.period.dayCount())));
        if (r.avgDailyExpensePaisa > 0) {
            items.add(row(kv, "Average daily spending", MoneyUtils.formatBdt(r.avgDailyExpensePaisa)));
        }
        if (r.projectedMonthExpensePaisa >= 0) {
            items.add(row(kv, "Projected spending for the whole month", MoneyUtils.formatBdt(r.projectedMonthExpensePaisa)));
        }
        if (r.highestSpendDayMillis >= 0) {
            items.add(row(kv, "Highest-spending day", day.format(new Date(r.highestSpendDayMillis))
                    + "  (" + MoneyUtils.formatBdt(r.highestSpendDayPaisa) + ")"));
        }

        if (!r.hasData()) {
            items.add(PdfReportModel.space(6));
            items.add(PdfReportModel.text("There are no transactions in this period."));
        } else {
            addComparison(items, r);
            addCategories(items, r);
            addMonths(items, r);
        }
        addBudgets(items, r);
        if (r.hasData()) {
            addInsights(items, r);
            addLargest(items, r, day);
        }
        addGoals(items, in, today, day);
        addBills(items, in, today, day);
        addRecurring(items, in, day);
        if (r.hasData()) addTransactions(items, in, day);
        return items;
    }

    // ------------------------------------------------------------------ sections

    private static PdfReportModel.Item row(PdfReportModel.Item header, String... cells) {
        return PdfReportModel.tableRow(header, cells, -1f, false);
    }

    private static void addComparison(List<PdfReportModel.Item> items, ReportData r) {
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Compared with " + r.previousLabel));
        PdfReportModel.Item h = PdfReportModel.tableHeader(new String[]{"Measure", "This period", "Previous", "Change"},
                new float[]{2f, 2.2f, 2.2f, 1.4f}, new boolean[]{false, true, true, true});
        items.add(h);
        ReportData.Summary c = r.current, p = r.previous;
        items.add(row(h, "Income", MoneyUtils.formatBdt(c.incomePaisa), MoneyUtils.formatBdt(p.incomePaisa),
                ReportFormat.signedPercent(ReportCalculator.changeTenths(c.incomePaisa, p.incomePaisa))));
        items.add(row(h, "Expenses", MoneyUtils.formatBdt(c.expensePaisa), MoneyUtils.formatBdt(p.expensePaisa),
                ReportFormat.signedPercent(ReportCalculator.changeTenths(c.expensePaisa, p.expensePaisa))));
        items.add(row(h, "Net cash flow", MoneyUtils.formatBdt(c.netPaisa), MoneyUtils.formatBdt(p.netPaisa),
                ReportFormat.DASH));
        items.add(row(h, "Savings rate", ReportFormat.percent(c.savingsRateTenths),
                ReportFormat.percent(p.savingsRateTenths),
                ReportFormat.pointsChange(c.savingsRateTenths, p.savingsRateTenths)));
    }

    private static void addCategories(List<PdfReportModel.Item> items, ReportData r) {
        if (r.categories.isEmpty()) return;
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Expenses by category"));
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Category", "Amount", "Share", "Txns", "vs previous"},
                new float[]{2.6f, 2.2f, 1.1f, 0.9f, 1.4f}, new boolean[]{false, true, true, true, true});
        items.add(h);
        for (ReportData.CategorySlice c : r.categories) {
            float bar = Math.max(0f, Math.min(1f, c.shareTenths / 1000f));
            items.add(PdfReportModel.tableRow(h, new String[]{c.category, MoneyUtils.formatBdt(c.amountPaisa),
                    ReportFormat.percent(c.shareTenths), String.valueOf(c.count),
                    ReportFormat.signedPercent(c.changeTenths)}, bar, false));
        }
        items.add(PdfReportModel.tableRow(h, new String[]{"Total", MoneyUtils.formatBdt(r.current.expensePaisa),
                "100%", String.valueOf(r.current.expenseCount), ""}, -1f, true));
    }

    private static void addMonths(List<PdfReportModel.Item> items, ReportData r) {
        if (r.bars.isEmpty()) return;
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Income vs expenses by month"));
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Month", "Income", "Expenses", "Net", "Savings rate"},
                new float[]{1.8f, 2.2f, 2.2f, 2.2f, 1.4f}, new boolean[]{false, true, true, true, true});
        items.add(h);
        for (ReportData.MonthBar b : r.bars) {
            items.add(row(h, MonthUtils.label(b.monthKey), MoneyUtils.formatBdt(b.incomePaisa),
                    MoneyUtils.formatBdt(b.expensePaisa), MoneyUtils.formatBdt(b.netPaisa),
                    ReportFormat.percent(b.savingsRateTenths)));
        }
        if (r.barsTruncated) {
            items.add(PdfReportModel.text("Showing the latest " + r.bars.size() + " of " + r.totalBarMonths
                    + " months in this range."));
        }
    }

    private static String statusText(BudgetStatus.Level level) {
        switch (level) {
            case EXCEEDED:
                return "Over budget";
            case WARNING:
                return "Near limit";
            case NORMAL:
                return "On track";
            default:
                return "-";
        }
    }

    private static void addBudgets(List<PdfReportModel.Item> items, ReportData r) {
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Budget vs actual"));
        ReportData.BudgetReport b = r.budget;
        if (b == null || !b.periodSupported) {
            items.add(PdfReportModel.text("Budgets are monthly, so this comparison needs whole calendar months."));
            return;
        }
        if (!b.hasBudgets()) {
            items.add(PdfReportModel.text("No budgets were set for this period."));
            return;
        }
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Budget", "Budgeted", "Spent", "Remaining", "Used", "Status"},
                new float[]{2.4f, 2f, 2f, 2f, 1f, 1.5f}, new boolean[]{false, true, true, true, true, false});
        items.add(h);
        for (ReportData.BudgetRow row : b.rows) {
            BudgetStatus st = row.status;
            items.add(PdfReportModel.tableRow(h, new String[]{row.overall ? "Overall monthly budget" : row.label,
                    MoneyUtils.formatBdt(st.budgetPaisa), MoneyUtils.formatBdt(st.spentPaisa),
                    MoneyUtils.formatBdt(st.remainingPaisa), st.percentText + "%", statusText(st.level)}, -1f,
                    row.overall));
        }
        if (b.monthsWithBudgets > 1) {
            items.addAll(PdfReportModel.wrapped("Each budget is compared with spending in the months where it was set ("
                    + b.monthsWithBudgets + " months have budgets).", WRAP_CHARS, false));
        }
    }

    private static void addInsights(List<PdfReportModel.Item> items, ReportData r) {
        if (r.insights.isEmpty()) return;
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Insights"));
        for (String insight : r.insights) {
            items.addAll(PdfReportModel.wrapped(clean(insight), WRAP_CHARS, true));
        }
    }

    private static void addLargest(List<PdfReportModel.Item> items, ReportData r, SimpleDateFormat day) {
        addTransactionTable(items, "Largest expenses", r.largestExpenses, day, false);
        addTransactionTable(items, "Largest income", r.largestIncome, day, false);
    }

    private static void addTransactionTable(List<PdfReportModel.Item> items, String title, List<Transaction> list,
                                            SimpleDateFormat day, boolean signed) {
        if (list.isEmpty()) return;
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading(title));
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Date", "Type", "Category", "Note", "Amount"},
                new float[]{1.7f, 1.2f, 2.2f, 3f, 2.2f}, new boolean[]{false, false, false, false, true});
        items.add(h);
        for (Transaction t : list) {
            items.add(row(h, day.format(new Date(t.dateMillis)), t.isIncome() ? "Income" : "Expense",
                    t.category == null ? "" : t.category, t.note == null ? "" : t.note,
                    (signed ? (t.isIncome() ? "+ " : "- ") : "") + MoneyUtils.formatBdt(t.amountPaisa)));
        }
    }

    private static void addGoals(List<PdfReportModel.Item> items, Input in, long today, SimpleDateFormat day) {
        List<GoalWithSaved> active = new ArrayList<>();
        int archived = 0;
        for (GoalWithSaved g : in.goals) {
            if (g.goal.archived) archived++;
            else active.add(g);
        }
        if (active.isEmpty() && archived == 0) return;
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Savings goals (as of " + day.format(new Date(today)) + ")"));
        if (active.isEmpty()) {
            items.add(PdfReportModel.text("All goals are archived (" + archived + ")."));
            return;
        }
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Goal", "Target", "Saved", "Progress", "Deadline"},
                new float[]{2.6f, 2.1f, 2.1f, 1.2f, 1.9f}, new boolean[]{false, true, true, true, true});
        items.add(h);
        int shown = 0;
        for (GoalWithSaved g : active) {
            if (shown++ >= MAX_LIST_ROWS) break;
            GoalStatus st = GoalCalculator.status(g, today);
            String deadline = !st.hasDeadline ? "None" : day.format(new Date(g.goal.deadlineMillis));
            float bar = Math.max(0f, Math.min(1f, st.progress / 100f));
            items.add(PdfReportModel.tableRow(h, new String[]{g.goal.name, MoneyUtils.formatBdt(g.goal.targetPaisa),
                    MoneyUtils.formatBdt(g.savedPaisa), st.percentText + "%", deadline}, bar, false));
        }
        if (archived > 0) items.add(PdfReportModel.text(archived + " archived goal(s) not listed."));
    }

    private static void addBills(List<PdfReportModel.Item> items, Input in, long today, SimpleDateFormat day) {
        List<Bill> unpaid = new ArrayList<>();
        for (Bill b : in.bills) {
            if (!b.paid) unpaid.add(b);
        }
        if (unpaid.isEmpty()) return;
        java.util.Collections.sort(unpaid, new java.util.Comparator<Bill>() {
            @Override
            public int compare(Bill a, Bill b) {
                return Long.compare(a.dueDateMillis, b.dueDateMillis);
            }
        });
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Unpaid bills (as of " + day.format(new Date(today)) + ")"));
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Bill", "Due date", "Amount", "Status"},
                new float[]{3f, 1.9f, 2.2f, 2.4f}, new boolean[]{false, false, true, false});
        items.add(h);
        int shown = 0;
        for (Bill b : unpaid) {
            if (shown++ >= MAX_LIST_ROWS) break;
            int days = BillCalculator.daysUntilDue(b, today);
            String status;
            switch (BillCalculator.state(b, today)) {
                case OVERDUE:
                    status = "Overdue by " + (-days) + (days == -1 ? " day" : " days");
                    break;
                case DUE_TODAY:
                    status = "Due today";
                    break;
                default:
                    status = "Due in " + days + (days == 1 ? " day" : " days");
            }
            items.add(row(h, b.name, day.format(new Date(b.dueDateMillis)), MoneyUtils.formatBdt(b.amountPaisa), status));
        }
        if (unpaid.size() > MAX_LIST_ROWS) {
            items.add(PdfReportModel.text((unpaid.size() - MAX_LIST_ROWS) + " more not listed."));
        }
    }

    private static void addRecurring(List<PdfReportModel.Item> items, Input in, SimpleDateFormat day) {
        List<RecurringRule> active = new ArrayList<>();
        int paused = 0;
        for (RecurringRule rule : in.rules) {
            if (rule.active) active.add(rule);
            else paused++;
        }
        if (active.isEmpty() && paused == 0) return;
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("Recurring income and expenses"));
        if (active.isEmpty()) {
            items.add(PdfReportModel.text("All recurring rules are paused (" + paused + ")."));
            return;
        }
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Category", "Type", "Amount", "Repeats", "Ends"},
                new float[]{2.6f, 1.3f, 2.2f, 1.4f, 1.9f}, new boolean[]{false, false, true, false, true});
        items.add(h);
        int shown = 0;
        for (RecurringRule rule : active) {
            if (shown++ >= MAX_LIST_ROWS) break;
            String every = RecurrenceCalculator.DAILY.equals(rule.frequency) ? "Daily"
                    : RecurrenceCalculator.WEEKLY.equals(rule.frequency) ? "Weekly" : "Monthly";
            items.add(row(h, rule.category, rule.isIncome() ? "Income" : "Expense",
                    MoneyUtils.formatBdt(rule.amountPaisa), every,
                    rule.endDateMillis > 0 ? day.format(new Date(rule.endDateMillis)) : "No end"));
        }
        if (paused > 0) items.add(PdfReportModel.text(paused + " paused rule(s) not listed."));
    }

    private static void addTransactions(List<PdfReportModel.Item> items, Input in, SimpleDateFormat day) {
        List<Transaction> list = new ArrayList<>();
        for (Transaction t : in.transactions) {
            if (ReportCalculator.isCounted(t) && in.report.period.contains(t.dateMillis)) list.add(t);
        }
        if (list.isEmpty()) return;
        java.util.Collections.sort(list, new java.util.Comparator<Transaction>() {
            @Override
            public int compare(Transaction a, Transaction b) {
                if (a.dateMillis != b.dateMillis) return a.dateMillis < b.dateMillis ? 1 : -1;
                return Long.compare(b.id, a.id);
            }
        });
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.heading("All transactions in this period (" + list.size() + ")"));
        PdfReportModel.Item h = PdfReportModel.tableHeader(
                new String[]{"Date", "Type", "Category", "Note", "Amount"},
                new float[]{1.7f, 1.2f, 2.2f, 3f, 2.2f}, new boolean[]{false, false, false, false, true});
        items.add(h);
        int shown = 0;
        for (Transaction t : list) {
            if (shown++ >= MAX_TRANSACTION_ROWS) break;
            items.add(row(h, day.format(new Date(t.dateMillis)), t.isIncome() ? "Income" : "Expense",
                    t.category == null ? "" : t.category, t.note == null ? "" : t.note,
                    (t.isIncome() ? "+ " : "- ") + MoneyUtils.formatBdt(t.amountPaisa)));
        }
        if (list.size() > MAX_TRANSACTION_ROWS) {
            items.add(PdfReportModel.text("Showing the newest " + MAX_TRANSACTION_ROWS + " of " + list.size()
                    + " transactions. Use CSV export for the complete list."));
        }
    }
}

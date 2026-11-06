package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Builds a full report model from realistic data and checks content, figures and pagination. */
public class PdfReportBuilderTest {
    private TimeZone saved;

    @Before
    public void setUp() {
        saved = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(saved);
    }

    private static long day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    private static Transaction tx(long id, String type, long paisa, String cat, String note, long date) {
        Transaction t = new Transaction();
        t.id = id;
        t.type = type;
        t.amountPaisa = paisa;
        t.category = cat;
        t.note = note;
        t.dateMillis = date;
        return t;
    }

    private static PdfReportBuilder.Input input(int extraRows) {
        long now = day(2026, 10, 20) + 12 * 3600_000L;
        List<Transaction> all = new ArrayList<>();
        all.add(tx(1, Transaction.TYPE_INCOME, 5_000_000, "Salary", "October salary", day(2026, 10, 1)));
        all.add(tx(2, Transaction.TYPE_EXPENSE, 125_050, "Food", "Lunch", day(2026, 10, 5)));
        all.add(tx(3, Transaction.TYPE_EXPENSE, 800_000, "Rent", "", day(2026, 10, 3)));
        all.add(tx(4, Transaction.TYPE_EXPENSE, 90_000, "Transport", null, day(2026, 9, 12)));
        for (int i = 0; i < extraRows; i++) {
            all.add(tx(100 + i, Transaction.TYPE_EXPENSE, 1000 + i, "Misc", "row " + i, day(2026, 10, 1 + (i % 19))));
        }
        List<Budget> budgets = new ArrayList<>();
        Budget b = new Budget();
        b.monthKey = "2026-10";
        b.category = Budget.OVERALL;
        b.amountPaisa = 1_000_000;
        budgets.add(b);
        ReportPeriod period = ReportPeriod.month("2026-10");

        PdfReportBuilder.Input in = new PdfReportBuilder.Input();
        in.report = ReportCalculator.build(period, all, budgets, now);
        in.transactions = all;
        in.nowMillis = now;

        SavingsGoal g = new SavingsGoal();
        g.id = 1;
        g.name = "Laptop";
        g.targetPaisa = 8_000_000;
        g.deadlineMillis = day(2026, 12, 31);
        GoalWithSaved gw = new GoalWithSaved();
        gw.goal = g;
        gw.savedPaisa = 2_000_000;
        in.goals.add(gw);

        Bill bill = new Bill();
        bill.id = 1;
        bill.name = "Electricity";
        bill.amountPaisa = 120_000;
        bill.dueDateMillis = day(2026, 10, 18); // overdue
        in.bills.add(bill);

        RecurringRule rule = new RecurringRule();
        rule.id = 1;
        rule.category = "Rent";
        rule.amountPaisa = 800_000;
        rule.frequency = RecurrenceCalculator.MONTHLY;
        rule.startDateMillis = day(2026, 1, 3);
        in.rules.add(rule);
        return in;
    }

    private static List<String> allText(List<PdfReportModel.Item> items) {
        List<String> out = new ArrayList<>();
        for (PdfReportModel.Item i : items) {
            for (String c : i.cells) out.add(c == null ? "" : c);
        }
        return out;
    }

    @Test
    public void reportContainsExpectedSectionsAndFigures() {
        List<PdfReportModel.Item> items = PdfReportBuilder.build(input(0));
        List<String> text = allText(items);
        for (String section : new String[]{"Financial Report", "Summary", "Expenses by category", "Budget vs actual",
                "Largest expenses", "Savings goals", "Unpaid bills", "Recurring income and expenses",
                "All transactions in this period"}) {
            boolean found = false;
            for (String t : text) found |= t.startsWith(section);
            assertTrue("missing section: " + section, found);
        }
        assertTrue(text.contains("BDT 50,000.00"));   // income
        assertTrue(text.contains("BDT 9,250.50"));    // expenses 800,000 + 125,050 paisa = 925,050 -> 9,250.50
        assertTrue(text.contains("Period: October 2026"));
        assertTrue(text.contains("Overdue by 2 days"));
    }

    @Test
    public void noTakaSignAnywhereInThePdfText() {
        for (String t : allText(PdfReportBuilder.build(input(0)))) {
            assertFalse("taka sign leaked: " + t, t.contains(MoneyUtils.SYMBOL));
        }
    }

    @Test
    public void emptyPeriodStillProducesAValidReport() {
        PdfReportBuilder.Input in = input(0);
        in.transactions = new ArrayList<>();
        in.report = ReportCalculator.build(ReportPeriod.month("2025-01"), new ArrayList<Transaction>(),
                new ArrayList<Budget>(), in.nowMillis);
        List<PdfReportModel.Item> items = PdfReportBuilder.build(in);
        assertTrue(allText(items).contains("There are no transactions in this period."));
        assertTrue(PdfReportModel.paginate(items, PdfReportModel.CONTENT_HEIGHT).size() >= 1);
    }

    @Test
    public void largeHistoryIsCappedAndPaginated() {
        PdfReportBuilder.Input in = input(PdfReportBuilder.MAX_TRANSACTION_ROWS + 300);
        List<PdfReportModel.Item> items = PdfReportBuilder.build(in);
        int rows = 0;
        boolean capNote = false;
        for (PdfReportModel.Item i : items) {
            if (i.kind == PdfReportModel.Kind.TEXT && i.text().startsWith("Showing the newest")) capNote = true;
        }
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(items, PdfReportModel.CONTENT_HEIGHT);
        for (List<PdfReportModel.Item> page : pages) {
            float used = 0;
            for (PdfReportModel.Item i : page) used += i.height;
            assertTrue(used <= PdfReportModel.CONTENT_HEIGHT + 0.01f);
            rows += page.size();
        }
        assertTrue(capNote);
        assertTrue(pages.size() > 20);
        assertTrue(rows > 0);
    }

    @Test
    public void periodLabels() {
        assertEquals("October 2026", PdfReportBuilder.periodLabel(ReportPeriod.month("2026-10")));
        assertEquals("01 Oct 2026 - 20 Oct 2026",
                PdfReportBuilder.periodLabel(ReportPeriod.custom(day(2026, 10, 1), day(2026, 10, 20))));
    }
}

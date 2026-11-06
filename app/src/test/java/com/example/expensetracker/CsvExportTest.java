package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class CsvExportTest {
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

    private static long day(int y, int m, int d) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    @Test
    public void transactionsCsvHasBomHeaderExactAmountsAndNewestFirst() throws Exception {
        List<Transaction> list = new ArrayList<>();
        list.add(tx(1, Transaction.TYPE_EXPENSE, 125050, "Food", "Lunch, with \"friends\"", day(2026, 10, 5)));
        list.add(tx(2, Transaction.TYPE_INCOME, 5000000, "Salary", null, day(2026, 10, 7)));
        StringBuilder sb = new StringBuilder();
        CsvExport.writeTransactions(sb, list, Long.MIN_VALUE, Long.MAX_VALUE);
        String[] lines = sb.toString().split("\r\n");
        assertTrue(sb.toString().startsWith(CsvExport.BOM));
        assertEquals(CsvExport.BOM + "Date,Type,Category,Amount (BDT),Note", lines[0]);
        assertEquals("2026-10-07,Income,Salary,50000.00,", lines[1]);
        assertEquals("2026-10-05,Expense,Food,1250.50,\"Lunch, with \"\"friends\"\"\"", lines[2]);
        assertEquals(3, lines.length);
    }

    @Test
    public void dateWindowIsHalfOpen() throws Exception {
        List<Transaction> list = new ArrayList<>();
        list.add(tx(1, Transaction.TYPE_EXPENSE, 100, "A", "", day(2026, 10, 1)));
        list.add(tx(2, Transaction.TYPE_EXPENSE, 200, "B", "", day(2026, 10, 31)));
        list.add(tx(3, Transaction.TYPE_EXPENSE, 300, "C", "", day(2026, 11, 1)));
        StringBuilder sb = new StringBuilder();
        CsvExport.writeTransactions(sb, list, day(2026, 10, 1), day(2026, 11, 1));
        String csv = sb.toString();
        assertTrue(csv.contains(",A,"));
        assertTrue(csv.contains(",B,"));
        assertEquals(false, csv.contains(",C,"));
    }

    @Test
    public void dangerousNoteIsNeutralised() throws Exception {
        List<Transaction> list = new ArrayList<>();
        list.add(tx(1, Transaction.TYPE_EXPENSE, 100, "A", "=HYPERLINK(\"http://x\")", day(2026, 10, 1)));
        StringBuilder sb = new StringBuilder();
        CsvExport.writeTransactions(sb, list, Long.MIN_VALUE, Long.MAX_VALUE);
        assertTrue(sb.toString().contains("\"'=HYPERLINK(\"\"http://x\"\")\""));
    }

    @Test
    public void emptyDataStillWritesHeader() throws Exception {
        StringBuilder sb = new StringBuilder();
        CsvExport.writeBudgets(sb, new ArrayList<Budget>());
        assertEquals(CsvExport.BOM + "Month,Category,Budget (BDT)\r\n", sb.toString());
    }

    @Test
    public void budgetsLabelOverallRow() throws Exception {
        Budget b = new Budget();
        b.monthKey = "2026-10";
        b.category = Budget.OVERALL;
        b.amountPaisa = 3000000;
        List<Budget> list = new ArrayList<>();
        list.add(b);
        StringBuilder sb = new StringBuilder();
        CsvExport.writeBudgets(sb, list);
        assertTrue(sb.toString().contains("2026-10,Overall monthly budget,30000.00"));
    }

    @Test
    public void goalsShowSavedAndRemaining() throws Exception {
        SavingsGoal g = new SavingsGoal();
        g.id = 1;
        g.name = "Laptop";
        g.targetPaisa = 8000000;
        GoalContribution c1 = new GoalContribution();
        c1.goalId = 1;
        c1.amountPaisa = 1500000;
        GoalContribution c2 = new GoalContribution();
        c2.goalId = 1;
        c2.amountPaisa = 500000;
        List<SavingsGoal> goals = new ArrayList<>();
        goals.add(g);
        List<GoalContribution> cs = new ArrayList<>();
        cs.add(c1);
        cs.add(c2);
        StringBuilder sb = new StringBuilder();
        CsvExport.writeGoals(sb, goals, cs);
        assertTrue(sb.toString().contains("Laptop,80000.00,20000.00,60000.00,,No,"));
    }

    @Test
    public void billsShowStatus() throws Exception {
        Bill b = new Bill();
        b.id = 1;
        b.name = "Electricity";
        b.category = "Utilities";
        b.amountPaisa = 120000;
        b.dueDateMillis = day(2026, 10, 25);
        b.reminderDaysBefore = 3;
        StringBuilder sb = new StringBuilder();
        List<Bill> list = new ArrayList<>();
        list.add(b);
        CsvExport.writeBills(sb, list);
        assertTrue(sb.toString().contains("Electricity,Utilities,1200.00,2026-10-25,3,Unpaid,"));
    }
}

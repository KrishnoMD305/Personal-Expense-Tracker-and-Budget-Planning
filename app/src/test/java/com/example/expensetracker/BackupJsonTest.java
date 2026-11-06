package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class BackupJsonTest {
    static BackupData sample() {
        BackupData d = new BackupData();
        d.theme = "dark";
        d.createdAtMillis = 1760000000000L;
        d.dbVersion = AppDatabase.VERSION;
        d.appVersion = "2026.0.0";

        Transaction t1 = new Transaction();
        t1.id = 7; t1.type = Transaction.TYPE_EXPENSE; t1.amountPaisa = 125050; t1.category = "Food";
        t1.note = "Lunch \"quoted\" \u09AC\u09BE\u099C\u09BE\u09B0\nline2"; t1.dateMillis = 1760000000000L; t1.createdAt = 1;
        Transaction t2 = new Transaction();
        t2.id = 9; t2.type = Transaction.TYPE_INCOME; t2.amountPaisa = 5000000; t2.category = null;
        t2.note = null; t2.dateMillis = 1760100000000L; t2.createdAt = 2;
        d.transactions.add(t1);
        d.transactions.add(t2);

        Budget b = new Budget();
        b.id = 3; b.monthKey = "2026-10"; b.category = Budget.OVERALL; b.amountPaisa = 3000000; b.createdAt = 5;
        d.budgets.add(b);

        SavingsGoal g = new SavingsGoal();
        g.id = 4; g.name = "Laptop"; g.targetPaisa = 8000000; g.deadlineMillis = 0; g.createdAt = 6; g.archived = true;
        d.goals.add(g);
        GoalContribution c = new GoalContribution();
        c.id = 11; c.goalId = 4; c.amountPaisa = 100000; c.dateMillis = 1760000000000L; c.note = ""; c.createdAt = 7;
        d.contributions.add(c);

        RecurringRule r = new RecurringRule();
        r.id = 2; r.type = Transaction.TYPE_INCOME; r.amountPaisa = 100; r.category = "Salary"; r.note = "";
        r.frequency = RecurrenceCalculator.MONTHLY; r.startDateMillis = 1; r.endDateMillis = 0; r.active = false;
        r.nextCheckMillis = 3; r.createdAt = 4;
        d.rules.add(r);
        RecurringPosting p = new RecurringPosting();
        p.id = 5; p.ruleId = 2; p.occurrenceMillis = 99; p.transactionId = 9; p.createdAt = 8;
        d.postings.add(p);

        Bill bill = new Bill();
        bill.id = 6; bill.name = "Rent"; bill.amountPaisa = 1500000; bill.category = "Housing";
        bill.dueDateMillis = 1760200000000L; bill.reminderDaysBefore = 3; bill.paid = true; bill.paidAt = 1760300000000L;
        bill.paymentTransactionId = 7; bill.lastNotifiedDay = 12; bill.createdAt = 13;
        d.bills.add(bill);
        return d;
    }

    private static void expectFailure(String json, String messagePart) {
        try {
            BackupJson.parse(json);
            fail("expected BackupException containing: " + messagePart);
        } catch (BackupException e) {
            assertTrue("message was: " + e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    @Test
    public void roundTripKeepsEveryFieldAndId() throws Exception {
        BackupData in = sample();
        BackupData out = BackupJson.parse(BackupJson.toJson(in));
        assertEquals("dark", out.theme);
        assertEquals(1760000000000L, out.createdAtMillis);
        assertEquals(2, out.transactions.size());
        Transaction t = out.transactions.get(0);
        assertEquals(7, t.id);
        assertEquals(Transaction.TYPE_EXPENSE, t.type);
        assertEquals(125050, t.amountPaisa);
        assertEquals("Lunch \"quoted\" \u09AC\u09BE\u099C\u09BE\u09B0\nline2", t.note);
        Transaction t2 = out.transactions.get(1);
        assertNull(t2.category);
        assertNull(t2.note);
        assertEquals(3, out.budgets.get(0).id);
        assertTrue(out.goals.get(0).archived);
        assertEquals(11, out.contributions.get(0).id);
        assertEquals(4, out.contributions.get(0).goalId);
        assertEquals(false, out.rules.get(0).active);
        assertEquals(99, out.postings.get(0).occurrenceMillis);
        assertEquals(7, out.bills.get(0).paymentTransactionId);
        assertTrue(out.bills.get(0).paid);
        assertEquals(in.totalRows(), out.totalRows());
    }

    @Test
    public void emptyDatabaseRoundTrips() throws Exception {
        BackupData d = new BackupData();
        d.dbVersion = AppDatabase.VERSION;
        assertEquals(0, BackupJson.parse(BackupJson.toJson(d)).totalRows());
    }

    @Test
    public void bomIsTolerated() throws Exception {
        BackupData out = BackupJson.parse("\uFEFF" + BackupJson.toJson(sample()));
        assertEquals(2, out.transactions.size());
    }

    @Test
    public void rejectsEmptyAndGarbage() {
        expectFailure("", "empty");
        expectFailure("   ", "empty");
        expectFailure("not json at all", "not a valid backup");
        expectFailure("[1,2,3]", "not a valid backup");
    }

    @Test
    public void rejectsForeignJson() {
        expectFailure("{\"hello\":\"world\"}", "not created by this app");
    }

    @Test
    public void rejectsNewerFormatAndNewerDatabase() throws Exception {
        JSONObject o = new JSONObject(BackupJson.toJson(sample()));
        o.put("backupFormat", 99);
        expectFailure(o.toString(), "newer file format");
        o = new JSONObject(BackupJson.toJson(sample()));
        o.put("dbVersion", AppDatabase.VERSION + 1);
        expectFailure(o.toString(), "newer version");
    }

    @Test
    public void rejectsMissingSection() throws Exception {
        JSONObject o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").remove("bills");
        expectFailure(o.toString(), "\"bills\" section is missing");
    }

    @Test
    public void rejectsTruncatedFileByCounts() throws Exception {
        JSONObject o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("transactions").remove(1);
        expectFailure(o.toString(), "damaged");
    }

    @Test
    public void rejectsBadValues() throws Exception {
        JSONObject o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("amountPaisa", -5);
        expectFailure(o.toString(), "amount out of range");

        o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("type", "TRANSFER");
        expectFailure(o.toString(), "unknown type");

        o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("budgets").getJSONObject(0).put("monthKey", "2026-13");
        expectFailure(o.toString(), "invalid month");

        o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("recurringRules").getJSONObject(0).put("frequency", "YEARLY");
        expectFailure(o.toString(), "unknown frequency");
    }

    @Test
    public void rejectsDuplicateIdsAndBrokenReferences() throws Exception {
        JSONObject o = new JSONObject(BackupJson.toJson(sample()));
        JSONArray tx = o.getJSONObject("data").getJSONArray("transactions");
        tx.getJSONObject(1).put("id", 7);
        expectFailure(o.toString(), "duplicate id");

        o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("goalContributions").getJSONObject(0).put("goalId", 999);
        expectFailure(o.toString(), "missing savings goal");

        o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("recurringPostings").getJSONObject(0).put("ruleId", 999);
        expectFailure(o.toString(), "missing recurring rule");
    }

    @Test
    public void rejectsWrongFieldType() throws Exception {
        JSONObject o = new JSONObject(BackupJson.toJson(sample()));
        o.getJSONObject("data").getJSONArray("bills").getJSONObject(0).put("paid", "yes");
        expectFailure(o.toString(), "not a valid backup");
    }

    @Test
    public void unknownThemeIsKeptAsTextAndNormalisedLater() throws Exception {
        BackupData d = sample();
        d.theme = "purple";
        assertEquals("purple", BackupJson.parse(BackupJson.toJson(d)).theme);
        assertEquals(ThemeManager.DEFAULT, ThemeManager.normalize("purple"));
    }
}

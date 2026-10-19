package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import android.content.Context;
import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.Calendar;
import java.util.List;

public class Stage4DatabaseTest {
    private AppDatabase db;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    @After
    public void closeDb() {
        db.close();
    }

    private static long day(int y, int m1, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m1 - 1, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    private long insertRule(String type, long paisa, String freq, long start, long end) {
        RecurringRule r = new RecurringRule();
        r.type = type;
        r.amountPaisa = paisa;
        r.category = type.equals(Transaction.TYPE_INCOME) ? "Salary" : "Rent";
        r.frequency = freq;
        r.startDateMillis = start;
        r.endDateMillis = end;
        r.active = true;
        r.nextCheckMillis = start;
        r.createdAt = 1;
        return db.recurringRuleDao().insert(r);
    }

    private int transactionCount() {
        return db.transactionDao().getBetween(0, Long.MAX_VALUE).size();
    }

    @Test
    public void processDue_createsMissedOccurrencesOnce() {
        insertRule(Transaction.TYPE_INCOME, 5_000_000, RecurrenceCalculator.MONTHLY, day(2026, 8, 1), 0);
        long now = day(2026, 10, 17) + 36_000_000L;
        assertEquals(3, RecurringRepository.processDue(db, now));
        assertEquals(0, RecurringRepository.processDue(db, now));
        assertEquals(3, transactionCount());
    }

    @Test
    public void processDue_neverDuplicatesEvenWhenTheMarkerIsReset() {
        long id = insertRule(Transaction.TYPE_EXPENSE, 100, RecurrenceCalculator.DAILY, day(2026, 10, 15), 0);
        long now = day(2026, 10, 17) + 1000;
        assertEquals(3, RecurringRepository.processDue(db, now));
        db.recurringRuleDao().setNextCheck(id, day(2026, 1, 1));
        assertEquals(0, RecurringRepository.processDue(db, now));
        assertEquals(3, transactionCount());
    }

    @Test
    public void processDue_doesNotRecreateADeletedTransaction() {
        insertRule(Transaction.TYPE_EXPENSE, 100, RecurrenceCalculator.DAILY, day(2026, 10, 16), 0);
        long now = day(2026, 10, 17) + 1000;
        RecurringRepository.processDue(db, now);
        List<Transaction> all = db.transactionDao().getBetween(0, Long.MAX_VALUE);
        db.transactionDao().deleteById(all.get(0).id);
        assertEquals(1, transactionCount());
        RecurringRepository.processDue(db, now);
        assertEquals(1, transactionCount());
    }

    @Test
    public void deletingARuleKeepsItsTransactionsButRemovesItsPostings() {
        long id = insertRule(Transaction.TYPE_EXPENSE, 100, RecurrenceCalculator.DAILY, day(2026, 10, 16), 0);
        RecurringRepository.processDue(db, day(2026, 10, 17) + 1000);
        db.recurringRuleDao().deleteById(id);
        assertEquals(2, transactionCount());
        assertEquals(0, db.recurringPostingDao().count(id, day(2026, 10, 16)));
    }

    @Test
    public void goalSavedTotalAndCascadeDelete() throws Exception {
        SavingsGoal g = new SavingsGoal();
        g.name = "Laptop";
        g.targetPaisa = 10_000_000;
        g.createdAt = 1;
        long gid = db.savingsGoalDao().insert(g);
        for (long amount : new long[]{250_000, 100_000}) {
            GoalContribution c = new GoalContribution();
            c.goalId = gid;
            c.amountPaisa = amount;
            c.dateMillis = 1;
            c.createdAt = 1;
            db.goalContributionDao().insert(c);
        }
        assertEquals(350_000L, db.goalContributionDao().totalForGoal(gid));
        db.savingsGoalDao().deleteById(gid);
        assertEquals(0L, db.goalContributionDao().totalForGoal(gid));
        assertNull(db.savingsGoalDao().getById(gid));
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException.class)
    public void contributionToAMissingGoalIsRejected() {
        GoalContribution c = new GoalContribution();
        c.goalId = 12345;
        c.amountPaisa = 1;
        db.goalContributionDao().insert(c);
    }

    @Test
    public void markPaidCreatesOneExpenseAndMarkUnpaidRemovesIt() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase real = AppDatabase.getInstance(ctx);
        int before = real.transactionDao().getBetween(0, Long.MAX_VALUE).size();
        Bill b = new Bill();
        b.name = "Stage4 test bill";
        b.amountPaisa = 450_000;
        b.category = "Utilities";
        b.dueDateMillis = day(2026, 10, 20);
        long id = real.billDao().insert(b);
        try {
            BillRepository.markPaid(ctx, id, true, null);
            waitForWrites();
            assertEquals(true, real.billDao().getById(id).paid);
            assertEquals(before + 1, real.transactionDao().getBetween(0, Long.MAX_VALUE).size());

            // a second "mark paid" must not create a second expense
            BillRepository.markPaid(ctx, id, true, null);
            waitForWrites();
            assertEquals(before + 1, real.transactionDao().getBetween(0, Long.MAX_VALUE).size());

            BillRepository.markUnpaid(ctx, id, null);
            waitForWrites();
            Bill again = real.billDao().getById(id);
            assertNotNull(again);
            assertEquals(false, again.paid);
            assertEquals(before, real.transactionDao().getBetween(0, Long.MAX_VALUE).size());
        } finally {
            real.billDao().deleteById(id);
        }
    }

    private static void waitForWrites() throws Exception {
        AppDatabase.WRITE_EXECUTOR.submit(() -> { }).get();
    }
}

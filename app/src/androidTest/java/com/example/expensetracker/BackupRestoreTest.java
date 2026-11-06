package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Stage 5: full backup -> JSON -> restore round trip on in-memory Room databases (never touches the real app
 * data). Run with: ./gradlew connectedDebugAndroidTest (needs a device or emulator).
 */
public class BackupRestoreTest {
    private AppDatabase source;
    private AppDatabase target;

    @Before
    public void setUp() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        source = Room.inMemoryDatabaseBuilder(c, AppDatabase.class).allowMainThreadQueries().build();
        target = Room.inMemoryDatabaseBuilder(c, AppDatabase.class).allowMainThreadQueries().build();
    }

    @After
    public void tearDown() {
        source.close();
        target.close();
    }

    private static Transaction tx(String type, long paisa, String cat) {
        Transaction t = new Transaction();
        t.type = type;
        t.amountPaisa = paisa;
        t.category = cat;
        t.note = "n";
        t.dateMillis = 1760000000000L;
        t.createdAt = 1;
        return t;
    }

    private void fill(AppDatabase db) {
        long t1 = db.transactionDao().insertReturningId(tx(Transaction.TYPE_EXPENSE, 125050, "Food"));
        db.transactionDao().insertReturningId(tx(Transaction.TYPE_INCOME, 5000000, "Salary"));

        SavingsGoal g = new SavingsGoal();
        g.name = "Laptop";
        g.targetPaisa = 8000000;
        g.createdAt = 1;
        long goalId = db.savingsGoalDao().insert(g);
        GoalContribution gc = new GoalContribution();
        gc.goalId = goalId;
        gc.amountPaisa = 100000;
        gc.dateMillis = 1760000000000L;
        gc.note = "";
        gc.createdAt = 1;
        db.goalContributionDao().insert(gc);

        RecurringRule r = new RecurringRule();
        r.type = Transaction.TYPE_EXPENSE;
        r.amountPaisa = 50000;
        r.category = "Rent";
        r.note = "";
        r.frequency = RecurrenceCalculator.MONTHLY;
        r.startDateMillis = 1760000000000L;
        r.createdAt = 1;
        long ruleId = db.recurringRuleDao().insert(r);
        RecurringPosting p = new RecurringPosting();
        p.ruleId = ruleId;
        p.occurrenceMillis = 1760000000000L;
        p.transactionId = t1;
        p.createdAt = 1;
        db.recurringPostingDao().insertIfAbsent(p);

        Bill b = new Bill();
        b.name = "Rent";
        b.category = "Housing";
        b.amountPaisa = 1500000;
        b.dueDateMillis = 1760000000000L;
        b.paid = true;
        b.paymentTransactionId = t1;
        b.createdAt = 1;
        db.billDao().insert(b);

        Budget bu = new Budget();
        bu.monthKey = "2026-10";
        bu.category = Budget.OVERALL;
        bu.amountPaisa = 3000000;
        bu.createdAt = 1;
        db.budgetDao().upsert(bu);
    }

    @Test
    public void roundTripRestoresEverythingWithSameIds() throws Exception {
        fill(source);
        BackupData snap = BackupRepository.snapshot(source);
        assertEquals(2, snap.transactions.size());

        BackupData parsed = BackupJson.parse(BackupJson.toJson(snap));
        // the target already holds different data that must be replaced
        target.transactionDao().insertReturningId(tx(Transaction.TYPE_EXPENSE, 1, "Old"));
        BackupRepository.replaceAll(target, parsed);

        BackupData again = BackupRepository.snapshot(target);
        assertEquals(BackupJson.toJson(snap).replaceAll("\"createdAt\": \\d+,\\n  \"settings\"", ""),
                BackupJson.toJson(again).replaceAll("\"createdAt\": \\d+,\\n  \"settings\"", ""));
        assertEquals(snap.transactions.get(0).id, again.transactions.get(0).id);
        assertEquals(snap.bills.get(0).paymentTransactionId, again.bills.get(0).paymentTransactionId);
        assertEquals(1, again.contributions.size());
        assertEquals(1, again.postings.size());
    }

    @Test
    public void failedRestoreRollsBackAndKeepsExistingData() {
        fill(target);
        BackupData bad = new BackupData();
        Transaction a = tx(Transaction.TYPE_EXPENSE, 100, "A");
        a.id = 5;
        Transaction dup = tx(Transaction.TYPE_EXPENSE, 200, "B");
        dup.id = 5; // duplicate primary key makes the insert fail half-way through the transaction
        bad.transactions.add(a);
        bad.transactions.add(dup);
        try {
            BackupRepository.replaceAll(target, bad);
            fail("expected the insert to fail");
        } catch (RuntimeException expected) {
            // fine
        }
        BackupData after = BackupRepository.snapshot(target);
        assertEquals(2, after.transactions.size()); // original rows are all still there
        assertTrue(after.goals.size() == 1 && after.bills.size() == 1);
    }

    @Test
    public void restoringAnEmptyBackupClearsEverything() {
        fill(target);
        BackupRepository.replaceAll(target, new BackupData());
        assertEquals(0, BackupRepository.snapshot(target).totalRows());
    }
}

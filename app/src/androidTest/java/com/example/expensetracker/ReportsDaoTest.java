package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.List;

public class ReportsDaoTest {
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

    private void insert(String type, long paisa, String category, long date) {
        Transaction t = new Transaction();
        t.type = type;
        t.amountPaisa = paisa;
        t.category = category;
        t.note = "";
        t.dateMillis = date;
        t.createdAt = date;
        db.transactionDao().insert(t);
    }

    @Test
    public void betweenIsStartInclusiveEndExclusiveAndNewestFirst() {
        insert(Transaction.TYPE_EXPENSE, 100, "Food", day(2026, 9, 30));
        insert(Transaction.TYPE_EXPENSE, 200, "Food", day(2026, 10, 1));
        insert(Transaction.TYPE_INCOME, 300, "Salary", day(2026, 10, 31));
        insert(Transaction.TYPE_EXPENSE, 400, "Food", day(2026, 11, 1));

        List<Transaction> rows = db.transactionDao().getBetween(day(2026, 10, 1), day(2026, 11, 1));
        assertEquals(2, rows.size());
        assertEquals(300, rows.get(0).amountPaisa);
        assertEquals(200, rows.get(1).amountPaisa);
    }

    @Test
    public void budgetsBetweenMonthsAreInclusiveAndCrossYears() {
        for (String m : new String[]{"2026-11", "2026-12", "2027-01", "2027-02"}) {
            Budget b = new Budget();
            b.monthKey = m;
            b.category = Budget.OVERALL;
            b.amountPaisa = 1000;
            b.createdAt = 1;
            db.budgetDao().upsert(b);
        }
        assertEquals(3, db.budgetDao().getBetweenMonths("2026-12", "2027-02").size());
        assertEquals(1, db.budgetDao().getBetweenMonths("2027-01", "2027-01").size());
    }

    @Test
    public void reportFromDatabaseRowsMatchesExpectedFigures() {
        insert(Transaction.TYPE_INCOME, 5_000_000, "Salary", day(2026, 10, 1));
        insert(Transaction.TYPE_EXPENSE, 1_000_000, "Rent", day(2026, 10, 2));
        insert(Transaction.TYPE_EXPENSE, 250_000, "Food", day(2026, 10, 9));
        insert(Transaction.TYPE_EXPENSE, 500_000, "Food", day(2026, 9, 9));
        Budget food = new Budget();
        food.monthKey = "2026-10";
        food.category = "Food";
        food.amountPaisa = 500_000;
        db.budgetDao().upsert(food);

        ReportPeriod period = ReportPeriod.month("2026-10");
        List<Transaction> txs = db.transactionDao()
                .getBetween(ReportCalculator.dataWindowStart(period), period.endMillis);
        List<Budget> budgets = db.budgetDao()
                .getBetweenMonths(period.firstMonthKey(), period.lastMonthKey());
        ReportData d = ReportCalculator.build(period, txs, budgets, day(2026, 12, 1));

        assertEquals(5_000_000, d.current.incomePaisa);
        assertEquals(1_250_000, d.current.expensePaisa);
        assertEquals(3_750_000, d.current.netPaisa);
        assertEquals(Long.valueOf(750), d.current.savingsRateTenths);
        assertEquals(500_000, d.previous.expensePaisa);
        assertTrue(d.budget.hasBudgets());
        assertEquals("50", d.budget.rows.get(0).status.percentText);
    }
}

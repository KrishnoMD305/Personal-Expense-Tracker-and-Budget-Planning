package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import android.database.Cursor;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import java.io.IOException;

public class MigrationTest {
    private static final String TEST_DB = "migration-test";
    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase.class, java.util.Collections.emptyList(), new FrameworkSQLiteOpenHelperFactory());
    @Test
    public void migrate1To2_preservesTransactionsAndCreatesBudgets() throws IOException {
        SupportSQLiteDatabase v1 = helper.createDatabase(TEST_DB, 1);
        v1.execSQL("INSERT INTO transactions (id, type, amountPaisa, category, note, dateMillis, createdAt) " + "VALUES (1, 'EXPENSE', 125050, 'Food', 'Lunch', 1760000000000, 1760000000000)");
        v1.execSQL("INSERT INTO transactions (id, type, amountPaisa, category, note, dateMillis, createdAt) " + "VALUES (2, 'INCOME', 5000000, 'Salary', '', 1760000000000, 1760000000000)");
        v1.close();

        SupportSQLiteDatabase v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2);

        Cursor c = v2.query("SELECT COUNT(*), SUM(amountPaisa) FROM transactions");
        c.moveToFirst();
        assertEquals(2, c.getInt(0));
        assertEquals(5125050L, c.getLong(1));
        c.close();
        Cursor b = v2.query("SELECT COUNT(*) FROM budgets");
        b.moveToFirst();
        assertEquals(0, b.getInt(0));
        b.close();

        v2.execSQL("INSERT INTO budgets (monthKey, category, amountPaisa, createdAt) " + "VALUES ('2026-10', 'Food', 300000, 1)");
        v2.close();
    }
}

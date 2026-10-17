package com.example.expensetracker;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Transaction.class, Budget.class}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final ExecutorService WRITE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile AppDatabase instance;

    
    public static final String SQL_CREATE_BUDGETS =
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " + "`monthKey` TEXT NOT NULL, `category` TEXT NOT NULL, "
                    + "`amountPaisa` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)";
    public static final String SQL_CREATE_BUDGETS_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_monthKey_category` " + "ON `budgets` (`monthKey`, `category`)";

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(SQL_CREATE_BUDGETS);
            db.execSQL(SQL_CREATE_BUDGETS_INDEX);
        }
    };

    public abstract TransactionDao transactionDao();

    public abstract BudgetDao budgetDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),AppDatabase.class, "expense_tracker.db").addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return instance;
    }
}

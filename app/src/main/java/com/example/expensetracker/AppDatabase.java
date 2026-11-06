package com.example.expensetracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Transaction.class, Budget.class,
        SavingsGoal.class, GoalContribution.class,
        RecurringRule.class, RecurringPosting.class, Bill.class},
        version = AppDatabase.VERSION, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final int VERSION = 3;
    public static final ExecutorService WRITE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile AppDatabase instance;

    public static final String SQL_CREATE_BUDGETS =
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`monthKey` TEXT NOT NULL, `category` TEXT NOT NULL, "
                    + "`amountPaisa` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)";
    public static final String SQL_CREATE_BUDGETS_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_monthKey_category` "
                    + "ON `budgets` (`monthKey`, `category`)";


    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(SQL_CREATE_BUDGETS);
            db.execSQL(SQL_CREATE_BUDGETS_INDEX);
        }
    };

    public static final String SQL_CREATE_SAVINGS_GOALS =
            "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `targetPaisa` INTEGER NOT NULL, `deadlineMillis` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL)";
    public static final String SQL_CREATE_GOAL_CONTRIBUTIONS =
            "CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`goalId` INTEGER NOT NULL, `amountPaisa` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, "
                    + "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )";
    public static final String SQL_CREATE_GOAL_CONTRIBUTIONS_INDEX =
            "CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)";
    public static final String SQL_CREATE_RECURRING_RULES =
            "CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`type` TEXT NOT NULL, `amountPaisa` INTEGER NOT NULL, `category` TEXT NOT NULL, "
                    + "`note` TEXT NOT NULL, `frequency` TEXT NOT NULL, `startDateMillis` INTEGER NOT NULL, "
                    + "`endDateMillis` INTEGER NOT NULL, `active` INTEGER NOT NULL, "
                    + "`nextCheckMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)";
    public static final String SQL_CREATE_RECURRING_POSTINGS =
            "CREATE TABLE IF NOT EXISTS `recurring_postings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`ruleId` INTEGER NOT NULL, `occurrenceMillis` INTEGER NOT NULL, "
                    + "`transactionId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`ruleId`) REFERENCES `recurring_rules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )";
    public static final String SQL_CREATE_RECURRING_POSTINGS_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_postings_ruleId_occurrenceMillis` "
                    + "ON `recurring_postings` (`ruleId`, `occurrenceMillis`)";
    public static final String SQL_CREATE_BILLS =
            "CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `amountPaisa` INTEGER NOT NULL, `category` TEXT NOT NULL, "
                    + "`dueDateMillis` INTEGER NOT NULL, `reminderDaysBefore` INTEGER NOT NULL, "
                    + "`paid` INTEGER NOT NULL, `paidAt` INTEGER NOT NULL, "
                    + "`paymentTransactionId` INTEGER NOT NULL, `lastNotifiedDay` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL)";


    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL(SQL_CREATE_SAVINGS_GOALS);
            db.execSQL(SQL_CREATE_GOAL_CONTRIBUTIONS);
            db.execSQL(SQL_CREATE_GOAL_CONTRIBUTIONS_INDEX);
            db.execSQL(SQL_CREATE_RECURRING_RULES);
            db.execSQL(SQL_CREATE_RECURRING_POSTINGS);
            db.execSQL(SQL_CREATE_RECURRING_POSTINGS_INDEX);
            db.execSQL(SQL_CREATE_BILLS);
        }
    };

    public abstract TransactionDao transactionDao();

    public abstract BudgetDao budgetDao();

    public abstract SavingsGoalDao savingsGoalDao();

    public abstract GoalContributionDao goalContributionDao();

    public abstract RecurringRuleDao recurringRuleDao();

    public abstract RecurringPostingDao recurringPostingDao();

    public abstract BillDao billDao();
    public abstract BackupDao backupDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "expense_tracker.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }
}

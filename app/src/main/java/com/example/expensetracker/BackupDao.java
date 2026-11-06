package com.example.expensetracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Stage 5: whole-table reads and writes used by backup, restore and CSV export. Rows are inserted with their
 * original ids, so links between tables (goal contributions -> goals, recurring postings -> rules,
 * a paid bill -> its payment transaction) stay valid after a restore.
 */
@Dao
public interface BackupDao {
    @Query("SELECT * FROM transactions ORDER BY id")
    List<Transaction> transactions();

    @Query("SELECT * FROM budgets ORDER BY id")
    List<Budget> budgets();

    @Query("SELECT * FROM savings_goals ORDER BY id")
    List<SavingsGoal> goals();

    @Query("SELECT * FROM goal_contributions ORDER BY id")
    List<GoalContribution> contributions();

    @Query("SELECT * FROM recurring_rules ORDER BY id")
    List<RecurringRule> rules();

    @Query("SELECT * FROM recurring_postings ORDER BY id")
    List<RecurringPosting> postings();

    @Query("SELECT * FROM bills ORDER BY id")
    List<Bill> bills();

    @Query("SELECT MIN(dateMillis) FROM transactions")
    Long firstTransactionMillis();

    // ---- restore: delete children before parents (cascades would do it too, this is explicit)
    @Query("DELETE FROM goal_contributions")
    void clearContributions();

    @Query("DELETE FROM recurring_postings")
    void clearPostings();

    @Query("DELETE FROM savings_goals")
    void clearGoals();

    @Query("DELETE FROM recurring_rules")
    void clearRules();

    @Query("DELETE FROM bills")
    void clearBills();

    @Query("DELETE FROM budgets")
    void clearBudgets();

    @Query("DELETE FROM transactions")
    void clearTransactions();

    @Insert
    void insertTransactions(List<Transaction> rows);

    @Insert
    void insertBudgets(List<Budget> rows);

    @Insert
    void insertGoals(List<SavingsGoal> rows);

    @Insert
    void insertContributions(List<GoalContribution> rows);

    @Insert
    void insertRules(List<RecurringRule> rows);

    @Insert
    void insertPostings(List<RecurringPosting> rows);

    @Insert
    void insertBills(List<Bill> rows);
}

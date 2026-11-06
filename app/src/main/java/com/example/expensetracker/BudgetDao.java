package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BudgetDao {
    /** Inserts, or replaces the existing row for the same (monthKey, category). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Budget budget);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Budget> budgets);

    @Query("DELETE FROM budgets WHERE monthKey = :monthKey AND category = :category")
    void delete(String monthKey, String category);

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    LiveData<List<Budget>> observeForMonth(String monthKey);

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    List<Budget> getForMonth(String monthKey);

    /**
     * Stage 3 (reports): budgets for every month from {@code fromKey} to {@code toKey}, inclusive.
     * Month keys are zero-padded "yyyy-MM", so plain text comparison orders them chronologically.
     */
    @Query("SELECT * FROM budgets WHERE monthKey >= :fromKey AND monthKey <= :toKey")
    LiveData<List<Budget>> observeBetweenMonths(String fromKey, String toKey);

    @Query("SELECT * FROM budgets WHERE monthKey >= :fromKey AND monthKey <= :toKey")
    List<Budget> getBetweenMonths(String fromKey, String toKey);

    /** Overall budget for the month in paisa, 0 if not set. */
    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM budgets WHERE monthKey = :monthKey AND category = :overall")
    long overallAmount(String monthKey, String overall);

    /** Sum of category budgets for the month, ignoring the overall row and {@code excludeCategory}. */
    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM budgets WHERE monthKey = :monthKey "
            + "AND category != :overall AND category != :excludeCategory")
    long sumCategoryBudgetsExcluding(String monthKey, String overall, String excludeCategory);
}

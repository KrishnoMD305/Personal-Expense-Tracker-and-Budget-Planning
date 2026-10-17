package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface BudgetDao {
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

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM budgets WHERE monthKey = :monthKey AND category = :overall")
    long overallAmount(String monthKey, String overall);

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM budgets WHERE monthKey = :monthKey " + "AND category != :overall AND category != :excludeCategory")
    long sumCategoryBudgetsExcluding(String monthKey, String overall, String excludeCategory);
}

package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GoalContributionDao {
    @Insert
    long insert(GoalContribution contribution);

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY dateMillis DESC, id DESC")
    LiveData<List<GoalContribution>> observeForGoal(long goalId);

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM goal_contributions WHERE goalId = :goalId")
    long totalForGoal(long goalId);
}

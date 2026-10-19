package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SavingsGoalDao {
    @Insert
    long insert(SavingsGoal goal);

    @Update
    void update(SavingsGoal goal);

    @Query("DELETE FROM savings_goals WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE savings_goals SET archived = :archived WHERE id = :id")
    void setArchived(long id, boolean archived);

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    SavingsGoal getById(long id);

    /** Every goal with the total of its contributions. Re-emits when either table changes. */
    @Query("SELECT g.*, COALESCE(SUM(c.amountPaisa), 0) AS savedPaisa FROM savings_goals g "
            + "LEFT JOIN goal_contributions c ON c.goalId = g.id GROUP BY g.id "
            + "ORDER BY g.archived ASC, g.createdAt DESC, g.id DESC")
    LiveData<List<GoalWithSaved>> observeAllWithSaved();

    @Query("SELECT g.*, COALESCE(SUM(c.amountPaisa), 0) AS savedPaisa FROM savings_goals g "
            + "LEFT JOIN goal_contributions c ON c.goalId = g.id WHERE g.id = :id GROUP BY g.id")
    LiveData<GoalWithSaved> observeOneWithSaved(long id);
}

package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface RecurringRuleDao {
    @Insert
    long insert(RecurringRule rule);

    @Update
    void update(RecurringRule rule);

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    RecurringRule getById(long id);

    @Query("SELECT * FROM recurring_rules WHERE active = 1 ORDER BY id")
    List<RecurringRule> getActive();

    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, createdAt DESC, id DESC")
    LiveData<List<RecurringRule>> observeAll();

    @Query("UPDATE recurring_rules SET nextCheckMillis = :nextCheck WHERE id = :id")
    void setNextCheck(long id, long nextCheck);
}

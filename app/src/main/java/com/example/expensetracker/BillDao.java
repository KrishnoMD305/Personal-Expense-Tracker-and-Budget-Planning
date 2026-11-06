package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BillDao {
    @Insert
    long insert(Bill bill);

    @Update
    void update(Bill bill);

    @Query("DELETE FROM bills WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM bills WHERE id = :id")
    Bill getById(long id);

    @Query("SELECT * FROM bills ORDER BY paid ASC, dueDateMillis ASC, id ASC")
    LiveData<List<Bill>> observeAll();

    @Query("SELECT * FROM bills WHERE paid = 0 ORDER BY dueDateMillis ASC, id ASC")
    List<Bill> getUnpaid();

    @Query("UPDATE bills SET lastNotifiedDay = :day WHERE id = :id")
    void setLastNotifiedDay(long id, long day);
}

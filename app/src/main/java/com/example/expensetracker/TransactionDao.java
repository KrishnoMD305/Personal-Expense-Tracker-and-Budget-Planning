package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert
    void insert(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    LiveData<List<Transaction>> getAll();

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM transactions WHERE type = :type")
    LiveData<Long> sumByType(String type);
}

package com.example.expensetracker;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TransactionDao {
    @Insert
    void insert(Transaction transaction);
    @Delete
    void delete(Transaction transaction);
    @Insert
    long insertReturningId(Transaction transaction);
    @Query("DELETE FROM transactions WHERE id = :id")
    void deleteById(long id);
    @Update
    void update(Transaction transaction);
    @Query("SELECT * FROM transactions WHERE id = :id")
    Transaction getById(long id);
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    LiveData<List<Transaction>> getAll();
    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM transactions WHERE type = :type")
    LiveData<Long> sumByType(String type);
    @Query("SELECT category AS category, COALESCE(SUM(amountPaisa), 0) AS total FROM transactions " + "WHERE type = 'EXPENSE' AND dateMillis >= :start AND dateMillis < :end GROUP BY category")
    LiveData<List<CategoryTotal>> observeExpensesByCategory(long start, long end);
    @Query("SELECT category AS category, COALESCE(SUM(amountPaisa), 0) AS total FROM transactions " + "WHERE type = 'EXPENSE' AND dateMillis >= :start AND dateMillis < :end GROUP BY category")
    List<CategoryTotal> expensesByCategory(long start, long end);
    @Query("SELECT * FROM transactions WHERE dateMillis >= :start AND dateMillis < :end " + "ORDER BY dateMillis DESC, id DESC")
    LiveData<List<Transaction>> observeBetween(long start, long end);
    @Query("SELECT * FROM transactions WHERE dateMillis >= :start AND dateMillis < :end " + "ORDER BY dateMillis DESC, id DESC")
    List<Transaction> getBetween(long start, long end);
}

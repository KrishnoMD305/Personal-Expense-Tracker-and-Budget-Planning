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

    /** Stage 4: insert and return the new row id (used by recurring rules and bill payments). */
    @Insert
    long insertReturningId(Transaction transaction);

    /** Stage 4: delete by id; does nothing if the row is already gone. */
    @Query("DELETE FROM transactions WHERE id = :id")
    void deleteById(long id);

    /** Stage 2: edit an existing transaction (matched by primary key). */
    @Update
    void update(Transaction transaction);

    @Query("SELECT * FROM transactions WHERE id = :id")
    Transaction getById(long id);

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    LiveData<List<Transaction>> getAll();

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM transactions WHERE type = :type")
    LiveData<Long> sumByType(String type);

    /** Expense totals per category for dateMillis in [start, end). Re-emits on any transaction change. */
    @Query("SELECT category AS category, COALESCE(SUM(amountPaisa), 0) AS total FROM transactions "
            + "WHERE type = 'EXPENSE' AND dateMillis >= :start AND dateMillis < :end GROUP BY category")
    LiveData<List<CategoryTotal>> observeExpensesByCategory(long start, long end);

    @Query("SELECT category AS category, COALESCE(SUM(amountPaisa), 0) AS total FROM transactions "
            + "WHERE type = 'EXPENSE' AND dateMillis >= :start AND dateMillis < :end GROUP BY category")
    List<CategoryTotal> expensesByCategory(long start, long end);

    /**
     * Stage 3 (reports): every transaction with dateMillis in [start, end). Re-emits whenever the
     * transactions table changes, so open reports stay in sync with edits, deletes and undo.
     */
    @Query("SELECT * FROM transactions WHERE dateMillis >= :start AND dateMillis < :end "
            + "ORDER BY dateMillis DESC, id DESC")
    LiveData<List<Transaction>> observeBetween(long start, long end);

    /** Blocking version of {@link #observeBetween}; call from a background thread. */
    @Query("SELECT * FROM transactions WHERE dateMillis >= :start AND dateMillis < :end "
            + "ORDER BY dateMillis DESC, id DESC")
    List<Transaction> getBetween(long start, long end);
}

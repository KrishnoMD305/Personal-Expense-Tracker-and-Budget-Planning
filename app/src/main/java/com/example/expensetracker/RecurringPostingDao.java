package com.example.expensetracker;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface RecurringPostingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfAbsent(RecurringPosting posting);
    @Query("UPDATE recurring_postings SET transactionId = :transactionId WHERE id = :id")
    void setTransactionId(long id, long transactionId);
    @Query("SELECT COUNT(*) FROM recurring_postings WHERE ruleId = :ruleId AND occurrenceMillis = :occurrenceMillis")
    int count(long ruleId, long occurrenceMillis);
}

package com.example.expensetracker;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "recurring_postings",
        foreignKeys = @ForeignKey(entity = RecurringRule.class, parentColumns = "id", childColumns = "ruleId", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"ruleId", "occurrenceMillis"}, unique = true)})
public class RecurringPosting {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long ruleId;
    public long occurrenceMillis;
    public long transactionId;
    public long createdAt;
}

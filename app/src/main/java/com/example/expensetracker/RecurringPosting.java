package com.example.expensetracker;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stage 4: remembers that the occurrence of {@code ruleId} on {@code occurrenceMillis} was turned
 * into a transaction. The UNIQUE index (ruleId, occurrenceMillis) is what makes duplicates impossible,
 * even if two workers run at once or the user deletes the generated transaction afterwards.
 */
@Entity(tableName = "recurring_postings",
        foreignKeys = @ForeignKey(entity = RecurringRule.class, parentColumns = "id",
                childColumns = "ruleId", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"ruleId", "occurrenceMillis"}, unique = true)})
public class RecurringPosting {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long ruleId;
    public long occurrenceMillis;
    /** Id of the created transaction (0 only for the instant between the two inserts). */
    public long transactionId;
    public long createdAt;
}

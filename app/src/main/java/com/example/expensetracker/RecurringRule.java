package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stage 4: a schedule that automatically creates income or expense transactions.
 * Occurrences are computed from {@link #startDateMillis} (see {@link RecurrenceCalculator}).
 */
@Entity(tableName = "recurring_rules")
public class RecurringRule {
    @PrimaryKey(autoGenerate = true)
    public long id;
    /** {@link Transaction#TYPE_INCOME} or {@link Transaction#TYPE_EXPENSE}. */
    @NonNull
    public String type = Transaction.TYPE_EXPENSE;
    public long amountPaisa;
    @NonNull
    public String category = "";
    @NonNull
    public String note = "";
    /** {@link RecurrenceCalculator#DAILY}, {@link RecurrenceCalculator#WEEKLY} or {@link RecurrenceCalculator#MONTHLY}. */
    @NonNull
    public String frequency = RecurrenceCalculator.MONTHLY;
    /** Start of the first occurrence day. */
    public long startDateMillis;
    /** Start of the last allowed day; 0 means "no end date". */
    public long endDateMillis;
    public boolean active = true;
    /** Occurrences on or after this instant have not been looked at yet (catch-up marker). */
    public long nextCheckMillis;
    public long createdAt;

    public boolean isIncome() {
        return Transaction.TYPE_INCOME.equals(type);
    }
}

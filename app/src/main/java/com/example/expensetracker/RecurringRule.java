package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recurring_rules")
public class RecurringRule {
    @PrimaryKey(autoGenerate = true)
    public long id;
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
    public long startDateMillis;
    public long endDateMillis;
    public boolean active = true;
    public long nextCheckMillis;
    public long createdAt;

    public boolean isIncome() {
        return Transaction.TYPE_INCOME.equals(type);
    }
}

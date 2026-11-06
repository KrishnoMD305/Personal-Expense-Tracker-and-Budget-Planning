package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Stage 4: a savings goal. Amounts are whole paisa. Money saved lives in {@link GoalContribution}. */
@Entity(tableName = "savings_goals")
public class SavingsGoal {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String name = "";
    public long targetPaisa;
    /** Start of the deadline day (device time zone); 0 means "no deadline". */
    public long deadlineMillis;
    public long createdAt;
    /** Archived goals are kept (with their history) but left out of dashboard totals. */
    public boolean archived;
}

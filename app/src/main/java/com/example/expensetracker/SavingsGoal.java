package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "savings_goals")
public class SavingsGoal {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String name = "";
    public long targetPaisa;
    public long deadlineMillis;
    public long createdAt;
    public boolean archived;
}

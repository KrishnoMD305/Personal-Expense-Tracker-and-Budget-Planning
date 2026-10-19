package com.example.expensetracker;
import androidx.room.Embedded;

public class GoalWithSaved {
    @Embedded
    public SavingsGoal goal;
    public long savedPaisa;
}

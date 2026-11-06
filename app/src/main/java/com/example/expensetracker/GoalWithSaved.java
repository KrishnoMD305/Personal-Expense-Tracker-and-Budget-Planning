package com.example.expensetracker;

import androidx.room.Embedded;

/** Query result: a goal together with the sum of its contributions. */
public class GoalWithSaved {
    @Embedded
    public SavingsGoal goal;
    public long savedPaisa;
}

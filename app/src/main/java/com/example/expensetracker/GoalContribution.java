package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "goal_contributions",
        foreignKeys = @ForeignKey(entity = SavingsGoal.class, parentColumns = "id",
                childColumns = "goalId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("goalId")})
public class GoalContribution {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long goalId;
    public long amountPaisa;
    public long dateMillis;
    @NonNull
    public String note = "";
    public long createdAt;
}

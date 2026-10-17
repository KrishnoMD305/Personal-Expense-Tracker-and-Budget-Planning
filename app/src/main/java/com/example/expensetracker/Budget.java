package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "budgets",
        indices = {@Index(value = {"monthKey", "category"}, unique = true)})
public class Budget {
    public static final String OVERALL = "__OVERALL__";

    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String monthKey = "";
    @NonNull
    public String category = OVERALL;
    public long amountPaisa;
    public long createdAt;

    public boolean isOverall() {
        return OVERALL.equals(category);
    }
}

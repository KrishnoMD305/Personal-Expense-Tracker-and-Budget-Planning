package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A spending limit for one calendar month. {@link #category} is either an expense category name
 * or {@link #OVERALL} for the whole-month budget. There is at most one row per (monthKey, category).
 * Amounts are stored in paisa, like {@link Transaction}.
 */
@Entity(tableName = "budgets",
        indices = {@Index(value = {"monthKey", "category"}, unique = true)})
public class Budget {
    /** Sentinel category meaning "the overall monthly budget". */
    public static final String OVERALL = "__OVERALL__";

    @PrimaryKey(autoGenerate = true)
    public long id;
    /** Month in "yyyy-MM" form, see {@link MonthUtils}. */
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

package com.example.expensetracker;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One income or expense record. Amount is stored in paisa (1 BDT = 100 paisa) as a long. */
@Entity(tableName = "transactions")
public class Transaction {
    public static final String TYPE_INCOME = "INCOME";
    public static final String TYPE_EXPENSE = "EXPENSE";

    @PrimaryKey(autoGenerate = true)
    public long id;
    public String type;
    public long amountPaisa;
    public String category;
    public String note;
    public long dateMillis;
    public long createdAt;

    public boolean isIncome() {
        return TYPE_INCOME.equals(type);
    }
}

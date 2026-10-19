package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "bills")
public class Bill {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String name = "";
    public long amountPaisa;
    @NonNull
    public String category = "";
    public long dueDateMillis;
    public int reminderDaysBefore;
    public boolean paid;
    public long paidAt;
    public long paymentTransactionId;
    public long lastNotifiedDay;
    public long createdAt;
}

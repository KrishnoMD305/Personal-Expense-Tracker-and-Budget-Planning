package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Stage 4: a bill with a due date, a reminder lead time and a paid / unpaid status. */
@Entity(tableName = "bills")
public class Bill {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String name = "";
    public long amountPaisa;
    @NonNull
    public String category = "";
    /** Start of the due day. */
    public long dueDateMillis;
    /** Remind this many days before the due date (0 = on the due date). */
    public int reminderDaysBefore;
    public boolean paid;
    public long paidAt;
    /** Expense transaction created when the bill was marked paid; 0 if none. */
    public long paymentTransactionId;
    /** Start of the last day a notification was shown for this bill (limits it to one per day). */
    public long lastNotifiedDay;
    public long createdAt;
}

package com.example.expensetracker;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;

public final class BillRepository {
    private static final String FAILED = "Could not save. Please try again.";

    private BillRepository() {}

    public static LiveData<List<Bill>> observeAll(Context context) {
        return AppDatabase.getInstance(context).billDao().observeAll();
    }

    public static void save(Context context, long id, String name, long amountPaisa, String category, long dueMillis, int reminderDays, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final String cleanName = name == null ? "" : name.trim();
        PlanCallback.run(callback, () -> db.runInTransaction(() -> {
            String err = PlanValidator.validateName(cleanName);
            if (err == null) err = PlanValidator.validateDueDate(dueMillis);
            if (err == null) err = PlanValidator.validateReminderDays(reminderDays);
            if (err != null) return err;
            long due = MonthUtils.startOfDay(dueMillis);
            if (id == 0) {
                Bill b = new Bill();
                b.name = cleanName;
                b.amountPaisa = amountPaisa;
                b.category = category == null ? "" : category;
                b.dueDateMillis = due;
                b.reminderDaysBefore = reminderDays;
                b.createdAt = System.currentTimeMillis();
                db.billDao().insert(b);
            } else {
                Bill b = db.billDao().getById(id);
                if (b == null) return "This bill no longer exists";
                if (b.paid) return "Mark the bill as unpaid before editing it";
                b.name = cleanName;
                b.amountPaisa = amountPaisa;
                b.category = category == null ? "" : category;
                b.dueDateMillis = due;
                b.reminderDaysBefore = reminderDays;
                b.lastNotifiedDay = 0; // a changed schedule may need a fresh reminder
                db.billDao().update(b);
            }
            return null;
        }), FAILED);
    }

    public static void markPaid(Context context, long id, boolean recordExpense, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final Context app = context.getApplicationContext();
        PlanCallback.run(callback, () -> {
            String err = db.runInTransaction(() -> {
                Bill b = db.billDao().getById(id);
                if (b == null) return "This bill no longer exists";
                if (b.paid) return null;
                long now = System.currentTimeMillis();
                long txId = 0;
                if (recordExpense) {
                    Transaction t = new Transaction();
                    t.type = Transaction.TYPE_EXPENSE;
                    t.amountPaisa = b.amountPaisa;
                    t.category = b.category;
                    t.note = "Bill: " + b.name;
                    t.dateMillis = MonthUtils.startOfDay(now);
                    t.createdAt = now;
                    txId = db.transactionDao().insertReturningId(t);
                }
                b.paid = true;
                b.paidAt = now;
                b.paymentTransactionId = txId;
                db.billDao().update(b);
                return null;
            });
            if (err == null) NotificationHelper.cancelBill(app, id);
            return err;
        }, FAILED);
    }

    public static void markUnpaid(Context context, long id, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> db.runInTransaction(() -> {
            Bill b = db.billDao().getById(id);
            if (b == null) return "This bill no longer exists";
            if (!b.paid) return null;
            if (b.paymentTransactionId != 0) db.transactionDao().deleteById(b.paymentTransactionId);
            b.paid = false;
            b.paidAt = 0;
            b.paymentTransactionId = 0;
            b.lastNotifiedDay = 0;
            db.billDao().update(b);
            return null;
        }), FAILED);
    }
    public static void delete(Context context, long id, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final Context app = context.getApplicationContext();
        PlanCallback.run(callback, () -> {
            db.billDao().deleteById(id);
            NotificationHelper.cancelBill(app, id);
            return null;
        }, FAILED);
    }
}

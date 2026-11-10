package com.example.expensetracker;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;

/** Bills: due dates, paid / unpaid status, and the optional matching expense transaction. */
public final class BillRepository {
    private static final String FAILED = "Could not save. Please try again.";

    private BillRepository() {}

    public static LiveData<List<Bill>> observeAll(Context context) {
        return AppDatabase.getInstance(context).billDao().observeAll();
    }

    /** Creates a bill (id == 0) or edits an unpaid one. The receipt photo is left as it is. */
    public static void save(Context context, long id, String name, long amountPaisa, String category,
                            long dueMillis, int reminderDays, PlanCallback callback) {
        save(context, id, name, amountPaisa, category, dueMillis, reminderDays, null, callback);
    }

    /**
     * Creates a bill (id == 0) or edits an unpaid one.
     *
     * @param receiptFile Stage 7: {@code null} = keep the bill's receipt photo, {@code ""} = remove it, otherwise the
     *                    name of a photo already stored by {@link ReceiptStore}. A replaced or removed photo is
     *                    deleted from the device (and, best effort, from Firebase Storage) after the save succeeded.
     */
    public static void save(Context context, long id, String name, long amountPaisa, String category,
                            long dueMillis, int reminderDays, String receiptFile, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final Context app = context.getApplicationContext();
        final String cleanName = name == null ? "" : name.trim();
        final String[] replaced = {"", ""}; // old receipt file, old cloud path
        PlanCallback.run(callback, () -> {
            String error = db.runInTransaction(() -> {
                String err = PlanValidator.validateName(cleanName);
                if (err == null) err = PlanValidator.validateDueDate(dueMillis);
                if (err == null) err = PlanValidator.validateReminderDays(reminderDays);
                if (err == null && receiptFile != null && !ReceiptNames.isValidOrEmpty(receiptFile)) {
                    err = "The receipt picture is not valid";
                }
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
                    if (receiptFile != null) b.receiptFile = receiptFile;
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
                    if (receiptFile != null && !receiptFile.equals(b.receiptFile)) {
                        replaced[0] = b.receiptFile;
                        replaced[1] = b.receiptCloudPath;
                        b.receiptFile = receiptFile;
                        b.receiptCloudPath = ""; // a new photo has not been uploaded yet
                    }
                    db.billDao().update(b);
                }
                return null;
            });
            if (error == null && !replaced[0].isEmpty()) {
                ReceiptStore.delete(app, replaced[0]);
                ReceiptSync.deleteRemote(app, replaced[1]);
            }
            return error;
        }, FAILED);
    }

    /**
     * Marks the bill paid. With {@code recordExpense} an expense transaction (today's date) is created
     * in the same database transaction and remembered, so that "mark unpaid" can remove it again.
     */
    public static void markPaid(Context context, long id, boolean recordExpense, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final Context app = context.getApplicationContext();
        PlanCallback.run(callback, () -> {
            String err = db.runInTransaction(() -> {
                Bill b = db.billDao().getById(id);
                if (b == null) return "This bill no longer exists";
                if (b.paid) return null; // already paid: nothing to do, never a second expense
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

    /** Back to unpaid; removes the expense transaction that "mark paid" created (if it still exists). */
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

    /** Deletes the bill; an expense recorded when it was paid stays in the history. */
    public static void delete(Context context, long id, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final Context app = context.getApplicationContext();
        PlanCallback.run(callback, () -> {
            Bill old = db.billDao().getById(id);
            db.billDao().deleteById(id);
            NotificationHelper.cancelBill(app, id);
            if (old != null && !old.receiptFile.isEmpty()) { // Stage 7: the receipt goes with its bill
                ReceiptStore.delete(app, old.receiptFile);
                ReceiptSync.deleteRemote(app, old.receiptCloudPath);
            }
            return null;
        }, FAILED);
    }
}

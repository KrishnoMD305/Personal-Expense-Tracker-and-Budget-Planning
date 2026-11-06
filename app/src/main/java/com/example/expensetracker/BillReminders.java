package com.example.expensetracker;

import android.content.Context;

import java.util.List;

/** Posts today's bill notifications. Called by {@link BillReminderWorker}. */
public final class BillReminders {
    private BillReminders() {}

    /** @return the number of notifications shown */
    public static int run(Context context, long nowMillis) {
        // Without permission nothing is marked as notified, so the reminder appears once it is granted.
        if (!NotificationHelper.canNotify(context)) return 0;
        AppDatabase db = AppDatabase.getInstance(context);
        long today = MonthUtils.startOfDay(nowMillis);
        List<Bill> unpaid = db.billDao().getUnpaid();
        int shown = 0;
        for (Bill bill : unpaid) {
            if (!BillReminderPolicy.shouldNotify(bill, today)) continue;
            if (NotificationHelper.notifyBill(context, bill, today)) {
                db.billDao().setLastNotifiedDay(bill.id, today);
                shown++;
            }
        }
        return shown;
    }
}

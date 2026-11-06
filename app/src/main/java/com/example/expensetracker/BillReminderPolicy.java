package com.example.expensetracker;

/** Decides whether a bill needs a notification today. Pure, so it can be unit tested. */
public final class BillReminderPolicy {
    private BillReminderPolicy() {}

    /**
     * True for an unpaid bill that is inside its reminder window (due date minus
     * {@code reminderDaysBefore}) or already overdue, and has not been notified today.
     */
    public static boolean shouldNotify(Bill bill, long todayStart) {
        if (bill.paid) return false;
        if (bill.lastNotifiedDay >= todayStart) return false;
        long due = MonthUtils.startOfDay(bill.dueDateMillis);
        long windowStart = MonthUtils.addDays(due, -Math.max(0, bill.reminderDaysBefore));
        return todayStart >= windowStart;
    }
}

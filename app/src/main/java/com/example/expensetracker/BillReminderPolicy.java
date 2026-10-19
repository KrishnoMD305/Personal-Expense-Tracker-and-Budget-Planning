package com.example.expensetracker;
public final class BillReminderPolicy {
    private BillReminderPolicy() {}
    public static boolean shouldNotify(Bill bill, long todayStart) {
        if (bill.paid) return false;
        if (bill.lastNotifiedDay >= todayStart) return false;
        long due = MonthUtils.startOfDay(bill.dueDateMillis);
        long windowStart = MonthUtils.addDays(due, -Math.max(0, bill.reminderDaysBefore));
        return todayStart >= windowStart;
    }
}

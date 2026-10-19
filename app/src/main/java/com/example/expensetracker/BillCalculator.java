package com.example.expensetracker;
public final class BillCalculator {
    private BillCalculator() {}
    public static BillState state(Bill bill, long todayStart) {
        if (bill.paid) return BillState.PAID;
        long due = MonthUtils.startOfDay(bill.dueDateMillis);
        if (due < todayStart) return BillState.OVERDUE;
        if (due == todayStart) return BillState.DUE_TODAY;
        return BillState.UPCOMING;
    }
    public static int daysUntilDue(Bill bill, long todayStart) {
        return PlanDates.daysBetween(todayStart, MonthUtils.startOfDay(bill.dueDateMillis));
    }
}

package com.example.expensetracker;

import android.content.Context;
import androidx.core.content.ContextCompat;


final class PlanText {
    private PlanText() {}

    static String days(Context c, int n) {
        return NotificationHelper.daysText(c, n);
    }

    static String goalDeadline(Context c, GoalStatus s, long deadlineMillis) {
        if (!s.hasDeadline) return c.getString(R.string.goal_no_deadline);
        String date = PlanDates.format(deadlineMillis);
        if (s.isCompleted()) return c.getString(R.string.goal_deadline_plain, date);
        if (s.daysLeft < 0) return c.getString(R.string.goal_deadline_overdue, date, days(c, -s.daysLeft));
        if (s.daysLeft == 0) return c.getString(R.string.goal_deadline_today, date);
        return c.getString(R.string.goal_deadline_left, date, days(c, s.daysLeft));
    }

    static int goalColor(Context c, GoalStatus s) {
        switch (s.state) {
            case COMPLETED:
                return ContextCompat.getColor(c, R.color.income);
            case OVERDUE:
                return ContextCompat.getColor(c, R.color.expense);
            default:
                return ContextCompat.getColor(c, R.color.accent);
        }
    }

    static String frequency(Context c, String frequency) {
        if (RecurrenceCalculator.DAILY.equals(frequency)) return c.getString(R.string.freq_daily);
        if (RecurrenceCalculator.WEEKLY.equals(frequency)) return c.getString(R.string.freq_weekly);
        return c.getString(R.string.freq_monthly);
    }

    static String billDue(Context c, Bill b, long today) {
        String date = PlanDates.format(b.dueDateMillis);
        int d = BillCalculator.daysUntilDue(b, today);
        if (d < 0) return c.getString(R.string.bill_due_overdue, date, days(c, -d));
        if (d == 0) return c.getString(R.string.bill_due_today, date);
        return c.getString(R.string.bill_due_in, date, days(c, d));
    }

    static int billColor(Context c, BillState s) {
        switch (s) {
            case OVERDUE:
                return ContextCompat.getColor(c, R.color.expense);
            case DUE_TODAY:
                return ContextCompat.getColor(c, R.color.warning);
            case PAID:
                return ContextCompat.getColor(c, R.color.income);
            default:
                return ContextCompat.getColor(c, android.R.color.black);
        }
    }
}

package com.example.expensetracker;

import android.content.Context;

import androidx.core.content.ContextCompat;

/** Text and colour rules shared by the Stage 4 screens and the dashboard. */
final class PlanText {
    private PlanText() {}

    static String days(Context c, int n) {
        return NotificationHelper.daysText(c, n);
    }

    /** "Deadline 12 Dec 2026 • 56 days left", "... • due today", "... • overdue by 3 days", or "No deadline". */
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
                return ThemeColors.get(c, R.attr.etIncome);
            case OVERDUE:
                return ThemeColors.get(c, R.attr.etExpense);
            default:
                return ThemeColors.get(c, R.attr.etAccent);
        }
    }

    static String frequency(Context c, String frequency) {
        if (RecurrenceCalculator.DAILY.equals(frequency)) return c.getString(R.string.freq_daily);
        if (RecurrenceCalculator.WEEKLY.equals(frequency)) return c.getString(R.string.freq_weekly);
        return c.getString(R.string.freq_monthly);
    }

    /** "Due 20 Oct 2026 • in 3 days" / "• due today" / "• overdue by 2 days". */
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
                return ThemeColors.get(c, R.attr.etExpense);
            case DUE_TODAY:
                return ThemeColors.get(c, R.attr.etWarning);
            case PAID:
                return ThemeColors.get(c, R.attr.etIncome);
            default:
                return ThemeColors.get(c, R.attr.etTextPrimary);
        }
    }
}

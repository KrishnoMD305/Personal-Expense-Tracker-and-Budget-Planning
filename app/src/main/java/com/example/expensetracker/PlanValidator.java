package com.example.expensetracker;

/** Input validation for Stage 4 forms. Every method returns an error message, or null when valid. */
public final class PlanValidator {
    public static final int MAX_NAME = 40;
    public static final int MAX_REMINDER_DAYS = 30;

    private PlanValidator() {}

    public static String validateName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return "Name is required";
        if (s.length() > MAX_NAME) return "Name is too long (max " + MAX_NAME + " characters)";
        return null;
    }

    /** A new or changed deadline must not be in the past; an unchanged one is always accepted. */
    public static String validateDeadline(long deadlineMillis, long todayStart, boolean isNewOrChanged) {
        if (deadlineMillis > 0 && isNewOrChanged && MonthUtils.startOfDay(deadlineMillis) < todayStart) {
            return "Deadline can't be in the past";
        }
        return null;
    }

    public static String validateRuleDates(long startMillis, long endMillis, long todayStart,
                                           boolean startIsNewOrChanged) {
        if (startMillis <= 0) return "Choose a start date";
        long start = MonthUtils.startOfDay(startMillis);
        if (startIsNewOrChanged && start < todayStart) return "Start date can't be in the past";
        if (endMillis > 0 && MonthUtils.startOfDay(endMillis) < start) {
            return "End date must be on or after the start date";
        }
        return null;
    }

    public static String validateDueDate(long dueMillis) {
        return dueMillis > 0 ? null : "Choose a due date";
    }

    public static String validateReminderDays(int days) {
        if (days < 0 || days > MAX_REMINDER_DAYS) return "Reminder must be 0 to " + MAX_REMINDER_DAYS + " days";
        return null;
    }
}

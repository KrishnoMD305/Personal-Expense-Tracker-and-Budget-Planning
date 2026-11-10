package com.example.expensetracker;

import java.util.Calendar;

/** Test helper: start-of-day millis for a local calendar date (month is 1-based). */
final class TestDays {
    private TestDays() {}

    static long d(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day, 0, 0, 0);
        return c.getTimeInMillis();
    }

    static Bill bill(long due, boolean paid, long amount) {
        Bill b = new Bill();
        b.name = "Bill";
        b.amountPaisa = amount;
        b.category = "Utilities";
        b.dueDateMillis = due;
        b.paid = paid;
        return b;
    }

    static RecurringRule rule(String type, long amount, String freq, long start, long end, boolean active) {
        RecurringRule r = new RecurringRule();
        r.type = type;
        r.amountPaisa = amount;
        r.category = "Cat";
        r.frequency = freq;
        r.startDateMillis = start;
        r.endDateMillis = end;
        r.active = active;
        return r;
    }

    static GoalWithSaved goal(String name, long target, long saved, long deadline, boolean archived) {
        GoalWithSaved g = new GoalWithSaved();
        g.goal = new SavingsGoal();
        g.goal.name = name;
        g.goal.targetPaisa = target;
        g.goal.deadlineMillis = deadline;
        g.goal.archived = archived;
        g.savedPaisa = saved;
        return g;
    }
}

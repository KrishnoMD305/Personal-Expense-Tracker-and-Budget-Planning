package com.example.expensetracker;

import java.util.List;

/**
 * Pure calculations behind the three Stage 4 dashboard sections (savings goals, recurring
 * transactions, bills). Everything is computed from the lists passed in plus "today", so the
 * numbers are the same on the dashboard and on the detail screens and are easy to unit test.
 */
public final class DashboardCalculator {
    /** Look-ahead used for the recurring forecast and "due soon" bills. */
    public static final int FORECAST_DAYS = 30;
    public static final int DUE_SOON_DAYS = 7;
    private static final int OCCURRENCE_LIMIT = 400;

    private DashboardCalculator() {}

    // ------------------------------------------------------------------------------ goals

    public static final class GoalsSummary {
        /** Goals that are not archived. */
        public int activeCount;
        /** Non-archived goals that reached their target. */
        public int completedCount;
        public int overdueCount;
        public long totalTargetPaisa;
        /** Everything saved towards the counted goals (can exceed the target). */
        public long totalSavedPaisa;
        /** 0..100 rounded down; each goal counts at most its own target, so one over-funded goal cannot hide another. */
        public int progress;
        public String percentText = "0.0";
        /** Incomplete goal with the earliest deadline (overdue ones come first); null if none. */
        public SavingsGoal nextDeadlineGoal;
        public int nextDeadlineDays;

        public boolean hasGoals() {
            return activeCount > 0;
        }
    }

    public static GoalsSummary goals(List<GoalWithSaved> all, long todayStart) {
        GoalsSummary s = new GoalsSummary();
        long cappedSaved = 0;
        if (all != null) {
            for (GoalWithSaved g : all) {
                if (g.goal.archived) continue;
                GoalStatus st = GoalCalculator.status(g, todayStart);
                s.activeCount++;
                s.totalTargetPaisa += st.targetPaisa;
                s.totalSavedPaisa += st.savedPaisa;
                cappedSaved += Math.min(st.savedPaisa, st.targetPaisa);
                if (st.isCompleted()) {
                    s.completedCount++;
                } else {
                    if (st.state == GoalStatus.State.OVERDUE) s.overdueCount++;
                    if (st.hasDeadline && (s.nextDeadlineGoal == null
                            || g.goal.deadlineMillis < s.nextDeadlineGoal.deadlineMillis)) {
                        s.nextDeadlineGoal = g.goal;
                        s.nextDeadlineDays = st.daysLeft;
                    }
                }
            }
        }
        GoalStatus total = GoalCalculator.status(s.totalTargetPaisa, cappedSaved, 0, todayStart);
        s.progress = total.progress;
        s.percentText = total.percentText;
        return s;
    }

    // -------------------------------------------------------------------------- recurring

    public static final class RecurringSummary {
        public int activeCount;
        public int pausedCount;
        /** Income / expense that the active rules will create from tomorrow through the next 30 days. */
        public long upcomingIncomePaisa;
        public long upcomingExpensePaisa;
        public int upcomingCount;
        public RecurringRule nextRule;
        public long nextMillis = -1;

        public boolean hasRules() {
            return activeCount + pausedCount > 0;
        }
    }

    /**
     * Today's occurrence is created by the scheduler (it runs when the app opens and whenever a rule is
     * saved), so the forecast starts tomorrow: counting today as well would double count it.
     */
    public static RecurringSummary recurring(List<RecurringRule> rules, long todayStart) {
        RecurringSummary s = new RecurringSummary();
        if (rules == null) return s;
        long from = MonthUtils.addDays(todayStart, 1);
        long to = MonthUtils.addDays(todayStart, FORECAST_DAYS);
        for (RecurringRule r : rules) {
            if (!r.active) {
                s.pausedCount++;
                continue;
            }
            s.activeCount++;
            List<Long> occ = RecurrenceCalculator.occurrencesBetween(r.startDateMillis, r.endDateMillis,
                    r.frequency, from, to, OCCURRENCE_LIMIT);
            for (long ignored : occ) {
                if (r.isIncome()) s.upcomingIncomePaisa += r.amountPaisa;
                else s.upcomingExpensePaisa += r.amountPaisa;
                s.upcomingCount++;
            }
            long next = RecurrenceCalculator.nextOccurrence(r.startDateMillis, r.endDateMillis, r.frequency, from);
            if (next >= 0 && (s.nextMillis < 0 || next < s.nextMillis
                    || (next == s.nextMillis && r.id < s.nextRule.id))) {
                s.nextMillis = next;
                s.nextRule = r;
            }
        }
        return s;
    }

    // ------------------------------------------------------------------------------ bills

    public static final class BillsSummary {
        public int unpaidCount;
        public long unpaidTotalPaisa;
        public int overdueCount;
        public long overdueTotalPaisa;
        /** Unpaid bills due from today up to (not including) today + 7 days. */
        public int dueSoonCount;
        public long dueSoonTotalPaisa;
        /** Unpaid bill with the earliest due day that is today or later; null if none. */
        public Bill nextBill;
        public int nextBillDays;

        public boolean hasBills() {
            return unpaidCount > 0;
        }
    }

    public static BillsSummary bills(List<Bill> all, long todayStart) {
        BillsSummary s = new BillsSummary();
        if (all == null) return s;
        long soonEnd = MonthUtils.addDays(todayStart, DUE_SOON_DAYS);
        for (Bill b : all) {
            BillState st = BillCalculator.state(b, todayStart);
            if (st == BillState.PAID) continue;
            s.unpaidCount++;
            s.unpaidTotalPaisa += b.amountPaisa;
            if (st == BillState.OVERDUE) {
                s.overdueCount++;
                s.overdueTotalPaisa += b.amountPaisa;
                continue;
            }
            long due = MonthUtils.startOfDay(b.dueDateMillis);
            if (due < soonEnd) {
                s.dueSoonCount++;
                s.dueSoonTotalPaisa += b.amountPaisa;
            }
            if (s.nextBill == null || due < MonthUtils.startOfDay(s.nextBill.dueDateMillis)) {
                s.nextBill = b;
                s.nextBillDays = BillCalculator.daysUntilDue(b, todayStart);
            }
        }
        return s;
    }
}

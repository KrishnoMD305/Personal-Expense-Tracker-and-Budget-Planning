package com.example.expensetracker;

import java.util.List;

public final class DashboardCalculator {
    public static final int FORECAST_DAYS = 30;
    public static final int DUE_SOON_DAYS = 7;
    private static final int OCCURRENCE_LIMIT = 400;

    private DashboardCalculator() {}
    public static final class GoalsSummary {
        public int activeCount;
        public int completedCount;
        public int overdueCount;
        public long totalTargetPaisa;
        public long totalSavedPaisa;
        public int progress;
        public String percentText = "0.0";
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


    public static final class RecurringSummary {
        public int activeCount;
        public int pausedCount;
        public long upcomingIncomePaisa;
        public long upcomingExpensePaisa;
        public int upcomingCount;
        public RecurringRule nextRule;
        public long nextMillis = -1;

        public boolean hasRules() {
            return activeCount + pausedCount > 0;
        }
    }

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

    public static final class BillsSummary {
        public int unpaidCount;
        public long unpaidTotalPaisa;
        public int overdueCount;
        public long overdueTotalPaisa;
        public int dueSoonCount;
        public long dueSoonTotalPaisa;
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

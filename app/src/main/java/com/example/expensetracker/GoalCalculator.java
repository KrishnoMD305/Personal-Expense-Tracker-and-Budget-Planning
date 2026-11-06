package com.example.expensetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure savings-goal maths (no Android, no database). */
public final class GoalCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GoalCalculator() {}

    /**
     * @param deadlineMillis start of the deadline day, or 0 for none
     * @param todayStart     start of today
     */
    public static GoalStatus status(long targetPaisa, long savedPaisa, long deadlineMillis, long todayStart) {
        long saved = Math.max(0, savedPaisa);
        long target = Math.max(0, targetPaisa);
        long remaining = Math.max(0, target - saved);
        long excess = Math.max(0, saved - target);

        BigDecimal pct = target == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(saved).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(target), 1, RoundingMode.DOWN);
        if (pct.compareTo(HUNDRED) > 0) pct = HUNDRED.setScale(1);
        int progress = pct.setScale(0, RoundingMode.DOWN).intValue();

        boolean hasDeadline = deadlineMillis > 0;
        int daysLeft = hasDeadline ? PlanDates.daysBetween(todayStart, MonthUtils.startOfDay(deadlineMillis)) : 0;

        GoalStatus.State state;
        if (target > 0 && saved >= target) state = GoalStatus.State.COMPLETED;
        else if (hasDeadline && daysLeft < 0) state = GoalStatus.State.OVERDUE;
        else state = GoalStatus.State.ACTIVE;

        long monthly = 0;
        if (state == GoalStatus.State.ACTIVE && hasDeadline && remaining > 0) {
            long monthsLeft = Math.max(1, (daysLeft + 29) / 30); // ceil(daysLeft / 30), at least 1
            monthly = (remaining + monthsLeft - 1) / monthsLeft;   // ceil, so the plan never falls short
        }
        return new GoalStatus(target, saved, remaining, excess, progress, pct.toPlainString(),
                state, hasDeadline, daysLeft, monthly);
    }

    public static GoalStatus status(GoalWithSaved g, long todayStart) {
        return status(g.goal.targetPaisa, g.savedPaisa, g.goal.deadlineMillis, todayStart);
    }
}

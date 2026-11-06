package com.example.expensetracker;

/** Computed progress of one savings goal. Build it with {@link GoalCalculator}. */
public final class GoalStatus {
    public enum State { ACTIVE, COMPLETED, OVERDUE }

    public final long targetPaisa;
    public final long savedPaisa;
    /** Target minus saved, never negative. */
    public final long remainingPaisa;
    /** Saved beyond the target, never negative. */
    public final long excessPaisa;
    /** 0..100, rounded down, so a goal is never shown as 100% before it is reached. */
    public final int progress;
    /** One decimal, rounded down, capped at 100.0 (e.g. "79.9"). */
    public final String percentText;
    public final State state;
    public final boolean hasDeadline;
    /** Calendar days from today to the deadline; negative once it has passed. 0 when there is none. */
    public final int daysLeft;
    /** Amount to save per month to hit the deadline; 0 when not applicable. */
    public final long suggestedMonthlyPaisa;

    GoalStatus(long targetPaisa, long savedPaisa, long remainingPaisa, long excessPaisa, int progress,
               String percentText, State state, boolean hasDeadline, int daysLeft, long suggestedMonthlyPaisa) {
        this.targetPaisa = targetPaisa;
        this.savedPaisa = savedPaisa;
        this.remainingPaisa = remainingPaisa;
        this.excessPaisa = excessPaisa;
        this.progress = progress;
        this.percentText = percentText;
        this.state = state;
        this.hasDeadline = hasDeadline;
        this.daysLeft = daysLeft;
        this.suggestedMonthlyPaisa = suggestedMonthlyPaisa;
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }
}

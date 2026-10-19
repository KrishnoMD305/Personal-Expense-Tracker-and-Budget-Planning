package com.example.expensetracker;

public final class GoalStatus {
    public enum State { ACTIVE, COMPLETED, OVERDUE }

    public final long targetPaisa;
    public final long savedPaisa;
    public final long remainingPaisa;
    public final long excessPaisa;
    public final int progress;
    public final String percentText;
    public final State state;
    public final boolean hasDeadline;
    public final int daysLeft;
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

package com.example.expensetracker;

/** Immutable result of comparing a budget with what has been spent against it. */
public final class BudgetStatus {
    public enum Level { NONE, NORMAL, WARNING, EXCEEDED }

    /** Budget limit in paisa; 0 when no budget has been set. */
    public final long budgetPaisa;
    public final long spentPaisa;
    /** budget - spent; negative when overspent. */
    public final long remainingPaisa;
    /** Percentage used rounded DOWN to one decimal, e.g. "79.9" never becomes "80". */
    public final String percentText;
    /** Whole percent 0..100 for progress bars (rounded down, capped at 100). */
    public final int progress;
    public final Level level;

    BudgetStatus(long budgetPaisa, long spentPaisa, long remainingPaisa, String percentText,
                 int progress, Level level) {
        this.budgetPaisa = budgetPaisa;
        this.spentPaisa = spentPaisa;
        this.remainingPaisa = remainingPaisa;
        this.percentText = percentText;
        this.progress = progress;
        this.level = level;
    }

    public boolean hasBudget() {
        return level != Level.NONE;
    }

    public boolean isOverspent() {
        return hasBudget() && spentPaisa > budgetPaisa;
    }
}

package com.example.expensetracker;

public final class BudgetStatus {
    public enum Level { NONE, NORMAL, WARNING, EXCEEDED }

    public final long budgetPaisa;
    public final long spentPaisa;
    public final long remainingPaisa;
    public final String percentText;
    public final int progress;
    public final Level level;

    BudgetStatus(long budgetPaisa, long spentPaisa, long remainingPaisa, String percentText, int progress, Level level) {
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

package com.example.expensetracker;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BudgetCalculator {
    public static final int WARNING_PERCENT = 80;
    public static final int LIMIT_PERCENT = 100;

    private BudgetCalculator() {}

    public static BudgetStatus status(long budgetPaisa, long spentPaisa) {
        long spent = Math.max(0, spentPaisa);
        if (budgetPaisa <= 0) {
            return new BudgetStatus(0, spent, 0, "0", 0, BudgetStatus.Level.NONE);
        }
        BudgetStatus.Level level;
        if (spent * 100 >= budgetPaisa * LIMIT_PERCENT) {
            level = BudgetStatus.Level.EXCEEDED;
        } else if (spent * 100 >= budgetPaisa * WARNING_PERCENT) {
            level = BudgetStatus.Level.WARNING;
        } else {
            level = BudgetStatus.Level.NORMAL;
        }
        BigDecimal pct = BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(budgetPaisa), 1, RoundingMode.DOWN);
        String text = pct.stripTrailingZeros().toPlainString();
        int progress = (int) Math.min(100, spent * 100 / budgetPaisa);
        return new BudgetStatus(budgetPaisa, spent, budgetPaisa - spent, text, progress, level);
    }

    public static BudgetStatus overall(MonthOverview o) {
        return status(o.overallBudget(), o.totalSpent());
    }
    public static BudgetStatus forCategory(MonthOverview o, String category) {
        return status(o.categoryBudget(category), o.spentIn(category));
    }
}

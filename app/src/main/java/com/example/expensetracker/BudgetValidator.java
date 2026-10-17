package com.example.expensetracker;

public final class BudgetValidator {
    private BudgetValidator() {}

    public static String validateCategoryBudget(long newCategoryAmount, long otherCategoriesSum, long overallBudget) {
        if (newCategoryAmount <= 0) return "Budget must be greater than 0";
        if (newCategoryAmount > MoneyUtils.MAX_PAISA) return "Amount is too large";
        if (overallBudget > 0 && otherCategoriesSum + newCategoryAmount > overallBudget) {
            return "Category budgets would total " + MoneyUtils.format(otherCategoriesSum + newCategoryAmount) + ", more than your monthly budget of " + MoneyUtils.format(overallBudget) + ". Raise the monthly budget first.";
        }
        return null;
    }

    public static String validateOverallBudget(long newOverall, long categoriesSum) {
        if (newOverall <= 0) return "Budget must be greater than 0";
        if (newOverall > MoneyUtils.MAX_PAISA) return "Amount is too large";
        if (newOverall < categoriesSum) {
            return "Monthly budget can't be lower than your category budgets (" + MoneyUtils.format(categoriesSum) + "). Reduce those first.";
        }
        return null;
    }
}

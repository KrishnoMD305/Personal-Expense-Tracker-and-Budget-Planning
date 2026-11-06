package com.example.expensetracker;

/** Cross-field budget rules. Returns a user-facing error message, or null when valid. */
public final class BudgetValidator {
    private BudgetValidator() {}

    /**
     * Category budgets together may not exceed the overall monthly budget (when one is set).
     *
     * @param newCategoryAmount   proposed budget for the category being saved
     * @param otherCategoriesSum  sum of the month's budgets for all OTHER categories
     * @param overallBudget       the month's overall budget, 0 if none
     */
    public static String validateCategoryBudget(long newCategoryAmount, long otherCategoriesSum,
                                                long overallBudget) {
        if (newCategoryAmount <= 0) return "Budget must be greater than 0";
        if (newCategoryAmount > MoneyUtils.MAX_PAISA) return "Amount is too large";
        if (overallBudget > 0 && otherCategoriesSum + newCategoryAmount > overallBudget) {
            return "Category budgets would total " + MoneyUtils.format(otherCategoriesSum + newCategoryAmount)
                    + ", more than your monthly budget of " + MoneyUtils.format(overallBudget)
                    + ". Raise the monthly budget first.";
        }
        return null;
    }

    /** The overall budget may not be lower than the category budgets already assigned. */
    public static String validateOverallBudget(long newOverall, long categoriesSum) {
        if (newOverall <= 0) return "Budget must be greater than 0";
        if (newOverall > MoneyUtils.MAX_PAISA) return "Amount is too large";
        if (newOverall < categoriesSum) {
            return "Monthly budget can't be lower than your category budgets (" +
                    MoneyUtils.format(categoriesSum) + "). Reduce those first.";
        }
        return null;
    }
}

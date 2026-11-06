package com.example.expensetracker;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Snapshot of one month's budgets and expense totals (all in paisa). */
public final class MonthOverview {
    public final String monthKey;
    private final long overallBudget;
    private final Map<String, Long> categoryBudgets;
    private final Map<String, Long> spentByCategory;

    public MonthOverview(String monthKey, long overallBudget,
                         Map<String, Long> categoryBudgets, Map<String, Long> spentByCategory) {
        this.monthKey = monthKey;
        this.overallBudget = Math.max(0, overallBudget);
        this.categoryBudgets = Collections.unmodifiableMap(new HashMap<>(categoryBudgets));
        this.spentByCategory = Collections.unmodifiableMap(new HashMap<>(spentByCategory));
    }

    /** 0 when no overall budget is set. */
    public long overallBudget() {
        return overallBudget;
    }

    /** 0 when the category has no budget. */
    public long categoryBudget(String category) {
        Long v = categoryBudgets.get(category);
        return v == null ? 0 : v;
    }

    public long spentIn(String category) {
        Long v = spentByCategory.get(category);
        return v == null ? 0 : v;
    }

    /** All expenses in the month, including categories that have no budget. */
    public long totalSpent() {
        long sum = 0;
        for (long v : spentByCategory.values()) sum += v;
        return sum;
    }

    public long categoryBudgetsTotal() {
        long sum = 0;
        for (long v : categoryBudgets.values()) sum += v;
        return sum;
    }

    public boolean hasAnyBudget() {
        return overallBudget > 0 || !categoryBudgets.isEmpty();
    }

    public Map<String, Long> categoryBudgets() {
        return categoryBudgets;
    }

    /**
     * Builds an overview from raw database rows. The row with category {@link Budget#OVERALL} is
     * the monthly budget; expense totals with a null category are grouped under "".
     */
    public static MonthOverview build(String monthKey, List<Budget> budgets, List<CategoryTotal> spent) {
        long overall = 0;
        Map<String, Long> catBudgets = new HashMap<>();
        for (Budget b : budgets) {
            if (b.isOverall()) overall = b.amountPaisa;
            else catBudgets.put(b.category, b.amountPaisa);
        }
        Map<String, Long> spentMap = new HashMap<>();
        for (CategoryTotal t : spent) {
            String key = t.category == null ? "" : t.category;
            Long prev = spentMap.get(key);
            spentMap.put(key, (prev == null ? 0 : prev) + t.total);
        }
        return new MonthOverview(monthKey, overall, catBudgets, spentMap);
    }
}

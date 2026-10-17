package com.example.expensetracker;
import java.util.ArrayList;
import java.util.List;

public final class BudgetAlerts {
    private BudgetAlerts() {}

    public static final class Alert {
        public final String scope;
        public final BudgetStatus.Level level;
        public final String message;
        Alert(String scope, BudgetStatus.Level level, String message) {
            this.scope = scope;
            this.level = level;
            this.message = message;
        }
    }

    public static final String OVERALL_LABEL = "Monthly budget";
    public static Alert forStatus(String scope, BudgetStatus s) {
        if (s.level != BudgetStatus.Level.WARNING && s.level != BudgetStatus.Level.EXCEEDED) return null;
        String msg;
        if (s.isOverspent()) {
            msg = scope + ": OVERSPENT by " + MoneyUtils.format(-s.remainingPaisa) + " (" + s.percentText + "% of " + MoneyUtils.format(s.budgetPaisa) + ")";
        } else if (s.level == BudgetStatus.Level.EXCEEDED) {
            msg = scope + ": 100% of budget used (" + MoneyUtils.format(s.budgetPaisa) + "). Nothing remaining";
        } else {
            msg = scope + ": " + s.percentText + "% used, " + MoneyUtils.format(s.remainingPaisa) + " remaining of " + MoneyUtils.format(s.budgetPaisa);
        }
        return new Alert(scope, s.level, msg);
    }

    public static List<Alert> forMonth(MonthOverview o, List<String> categories) {
        List<Alert> out = new ArrayList<>();
        add(out, forStatus(OVERALL_LABEL, BudgetCalculator.overall(o)));
        for (String c : categories) add(out, forStatus(c, BudgetCalculator.forCategory(o, c)));
        return out;
    }

    public static List<Alert> forExpense(MonthOverview o, String category) {
        List<Alert> out = new ArrayList<>();
        add(out, forStatus(category, BudgetCalculator.forCategory(o, category)));
        add(out, forStatus(OVERALL_LABEL, BudgetCalculator.overall(o)));
        return out;
    }

    private static void add(List<Alert> list, Alert a) {
        if (a != null) list.add(a);
    }
}

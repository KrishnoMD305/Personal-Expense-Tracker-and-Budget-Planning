package com.example.expensetracker;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.List;

/**
 * Glue between Room and the pure budget logic. Reads are LiveData (so budget figures update
 * automatically whenever a transaction or budget changes); writes run on the single database
 * write thread and report back on the main thread.
 */
public final class BudgetRepository {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private BudgetRepository() {}

    /** Receives null on success or a user-facing error message. Always called on the main thread. */
    public interface ResultCallback {
        void onResult(String errorOrNull);
    }

    /** Receives the number of copied budgets, or -1 if the target month already has budgets. */
    public interface CountCallback {
        void onResult(int copied);
    }

    /** Live budgets + expense totals for the month. Emits again after any relevant change. */
    public static LiveData<MonthOverview> observeMonth(Context context, String monthKey) {
        AppDatabase db = AppDatabase.getInstance(context);
        final LiveData<List<Budget>> budgets = db.budgetDao().observeForMonth(monthKey);
        final LiveData<List<CategoryTotal>> spent = db.transactionDao()
                .observeExpensesByCategory(MonthUtils.startMillis(monthKey), MonthUtils.endMillis(monthKey));
        final MediatorLiveData<MonthOverview> out = new MediatorLiveData<>();
        Runnable combine = () -> {
            List<Budget> b = budgets.getValue();
            List<CategoryTotal> s = spent.getValue();
            if (b != null && s != null) out.setValue(MonthOverview.build(monthKey, b, s));
        };
        out.addSource(budgets, v -> combine.run());
        out.addSource(spent, v -> combine.run());
        return out;
    }

    /** Blocking read for use on a background thread. */
    public static MonthOverview loadMonth(AppDatabase db, String monthKey) {
        return MonthOverview.build(monthKey, db.budgetDao().getForMonth(monthKey),
                db.transactionDao().expensesByCategory(MonthUtils.startMillis(monthKey),
                        MonthUtils.endMillis(monthKey)));
    }

    /**
     * Creates or replaces a budget after validating it against the month's other budgets.
     * Use {@link Budget#OVERALL} as the category for the monthly budget.
     */
    public static void saveBudget(Context context, String monthKey, String category,
                                  long amountPaisa, ResultCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            String error;
            try {
                if (!MonthUtils.isValidKey(monthKey) || category == null || category.isEmpty()) {
                    error = "Invalid budget";
                } else {
                    error = db.runInTransaction(() -> {
                        BudgetDao dao = db.budgetDao();
                        String err;
                        if (Budget.OVERALL.equals(category)) {
                            err = BudgetValidator.validateOverallBudget(amountPaisa,
                                    dao.sumCategoryBudgetsExcluding(monthKey, Budget.OVERALL, ""));
                        } else {
                            err = BudgetValidator.validateCategoryBudget(amountPaisa,
                                    dao.sumCategoryBudgetsExcluding(monthKey, Budget.OVERALL, category),
                                    dao.overallAmount(monthKey, Budget.OVERALL));
                        }
                        if (err == null) {
                            Budget b = new Budget();
                            b.monthKey = monthKey;
                            b.category = category;
                            b.amountPaisa = amountPaisa;
                            b.createdAt = System.currentTimeMillis();
                            dao.upsert(b);
                        }
                        return err;
                    });
                }
            } catch (RuntimeException e) {
                error = "Could not save the budget. Please try again.";
            }
            final String result = error;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    public static void removeBudget(Context context, String monthKey, String category,
                                    ResultCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            String error = null;
            try {
                // Removing the monthly limit leaves category budgets standalone, which is allowed.
                db.budgetDao().delete(monthKey, category);
            } catch (RuntimeException e) {
                error = "Could not remove the budget. Please try again.";
            }
            final String result = error;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    /** Copies last month's budgets into {@code monthKey}, only if that month has none yet. */
    public static void copyFromPreviousMonth(Context context, String monthKey, CountCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            int copied;
            try {
                copied = db.runInTransaction(() -> {
                    BudgetDao dao = db.budgetDao();
                    if (!dao.getForMonth(monthKey).isEmpty()) return -1;
                    List<Budget> previous = dao.getForMonth(MonthUtils.shift(monthKey, -1));
                    long now = System.currentTimeMillis();
                    for (Budget b : previous) {
                        b.id = 0;
                        b.monthKey = monthKey;
                        b.createdAt = now;
                    }
                    if (!previous.isEmpty()) dao.insertAll(previous);
                    return previous.size();
                });
            } catch (RuntimeException e) {
                copied = -1;
            }
            final int result = copied;
            MAIN.post(() -> callback.onResult(result));
        });
    }
}

package com.example.expensetracker;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;

/** Savings goals and contributions. Reads are LiveData; writes run on the database write thread. */
public final class GoalRepository {
    private static final String FAILED = "Could not save. Please try again.";

    private GoalRepository() {}

    public static LiveData<List<GoalWithSaved>> observeAll(Context context) {
        return AppDatabase.getInstance(context).savingsGoalDao().observeAllWithSaved();
    }

    public static LiveData<GoalWithSaved> observeOne(Context context, long goalId) {
        return AppDatabase.getInstance(context).savingsGoalDao().observeOneWithSaved(goalId);
    }

    public static LiveData<List<GoalContribution>> observeContributions(Context context, long goalId) {
        return AppDatabase.getInstance(context).goalContributionDao().observeForGoal(goalId);
    }

    /** Creates a goal (id == 0) or updates an existing one. {@code deadlineMillis} 0 = no deadline. */
    public static void save(Context context, long id, String name, long targetPaisa, long deadlineMillis,
                            PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        final String cleanName = name == null ? "" : name.trim();
        PlanCallback.run(callback, () -> db.runInTransaction(() -> {
            String err = PlanValidator.validateName(cleanName);
            if (err != null) return err;
            long deadline = deadlineMillis > 0 ? MonthUtils.startOfDay(deadlineMillis) : 0;
            if (id == 0) {
                SavingsGoal g = new SavingsGoal();
                g.name = cleanName;
                g.targetPaisa = targetPaisa;
                g.deadlineMillis = deadline;
                g.createdAt = System.currentTimeMillis();
                db.savingsGoalDao().insert(g);
            } else {
                SavingsGoal g = db.savingsGoalDao().getById(id);
                if (g == null) return "This goal no longer exists";
                g.name = cleanName;
                g.targetPaisa = targetPaisa;
                g.deadlineMillis = deadline;
                db.savingsGoalDao().update(g);
            }
            return null;
        }), FAILED);
    }

    public static void addContribution(Context context, long goalId, long amountPaisa, long dateMillis,
                                       String note, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> db.runInTransaction(() -> {
            if (amountPaisa <= 0) return "Amount must be greater than 0";
            if (db.savingsGoalDao().getById(goalId) == null) return "This goal no longer exists";
            GoalContribution c = new GoalContribution();
            c.goalId = goalId;
            c.amountPaisa = amountPaisa;
            c.dateMillis = MonthUtils.startOfDay(dateMillis);
            c.note = note == null ? "" : note.trim();
            c.createdAt = System.currentTimeMillis();
            db.goalContributionDao().insert(c);
            return null;
        }), FAILED);
    }

    public static void deleteContribution(Context context, long contributionId, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> {
            db.goalContributionDao().deleteById(contributionId);
            return null;
        }, FAILED);
    }

    public static void setArchived(Context context, long goalId, boolean archived, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> {
            db.savingsGoalDao().setArchived(goalId, archived);
            return null;
        }, FAILED);
    }

    /** Deletes the goal and (by foreign-key cascade) its contribution history. */
    public static void delete(Context context, long goalId, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> {
            db.savingsGoalDao().deleteById(goalId);
            return null;
        }, FAILED);
    }
}

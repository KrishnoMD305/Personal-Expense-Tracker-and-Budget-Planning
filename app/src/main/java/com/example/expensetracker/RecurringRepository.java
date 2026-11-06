package com.example.expensetracker;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;

/** Recurring rules, and the scheduler that turns due occurrences into real transactions. */
public final class RecurringRepository {
    private static final String FAILED = "Could not save. Please try again.";
    /** Safety valve: occurrences handled per rule per run (the rest are picked up by the next run). */
    static final int MAX_PER_RULE_PER_RUN = 1000;

    private RecurringRepository() {}

    public static LiveData<List<RecurringRule>> observeAll(Context context) {
        return AppDatabase.getInstance(context).recurringRuleDao().observeAll();
    }

    /** Creates a rule (id == 0) or updates one, then immediately creates any occurrence that is due. */
    public static void save(Context context, long id, String type, long amountPaisa, String category,
                            String note, String frequency, long startMillis, long endMillis,
                            PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> {
            String err = db.runInTransaction(() -> {
                if (!RecurrenceCalculator.isValidFrequency(frequency)) return "Choose how often it repeats";
                long today = MonthUtils.startOfDay(System.currentTimeMillis());
                long start = MonthUtils.startOfDay(startMillis);
                long end = endMillis > 0 ? MonthUtils.startOfDay(endMillis) : 0;
                RecurringRuleDao dao = db.recurringRuleDao();
                RecurringRule existing = id == 0 ? null : dao.getById(id);
                if (id != 0 && existing == null) return "This recurring transaction no longer exists";
                boolean startChanged = existing == null || existing.startDateMillis != start;
                String dateErr = PlanValidator.validateRuleDates(start, end, today, startChanged);
                if (dateErr != null) return dateErr;

                RecurringRule r = existing != null ? existing : new RecurringRule();
                r.type = Transaction.TYPE_INCOME.equals(type) ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
                r.amountPaisa = amountPaisa;
                r.category = category == null ? "" : category;
                r.note = note == null ? "" : note.trim();
                r.frequency = frequency;
                r.startDateMillis = start;
                r.endDateMillis = end;
                if (existing == null) {
                    r.active = true;
                    r.nextCheckMillis = start;
                    r.createdAt = System.currentTimeMillis();
                    dao.insert(r);
                } else {
                    if (startChanged) r.nextCheckMillis = start;
                    dao.update(r);
                }
                return null;
            });
            if (err == null) processDue(db, System.currentTimeMillis());
            return err;
        }, FAILED);
    }

    /** Pause (false) or resume (true). Resuming never back-fills the occurrences missed while paused. */
    public static void setActive(Context context, long id, boolean active, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> {
            String err = db.runInTransaction(() -> {
                RecurringRule r = db.recurringRuleDao().getById(id);
                if (r == null) return "This recurring transaction no longer exists";
                r.active = active;
                if (active) {
                    r.nextCheckMillis = Math.max(r.nextCheckMillis, MonthUtils.startOfDay(System.currentTimeMillis()));
                }
                db.recurringRuleDao().update(r);
                return null;
            });
            if (err == null && active) processDue(db, System.currentTimeMillis());
            return err;
        }, FAILED);
    }

    /** Deletes the rule. Transactions it already created stay in the history. */
    public static void delete(Context context, long id, PlanCallback callback) {
        AppDatabase db = AppDatabase.getInstance(context);
        PlanCallback.run(callback, () -> {
            db.recurringRuleDao().deleteById(id);
            return null;
        }, FAILED);
    }

    /**
     * Creates a transaction for every active-rule occurrence that is due (on or before today) and has not
     * been created yet. Safe to call as often as you like, from any thread, even concurrently:
     * the whole run is one database transaction and each occurrence is guarded by the UNIQUE
     * (ruleId, occurrenceMillis) index in {@code recurring_postings}.
     *
     * @return the number of transactions created
     */
    public static int processDue(AppDatabase db, long nowMillis) {
        return db.runInTransaction(() -> {
            long today = MonthUtils.startOfDay(nowMillis);
            long tomorrow = MonthUtils.addDays(today, 1);
            int created = 0;
            for (RecurringRule rule : db.recurringRuleDao().getActive()) {
                long from = Math.max(MonthUtils.startOfDay(rule.startDateMillis), rule.nextCheckMillis);
                List<Long> due = RecurrenceCalculator.occurrencesBetween(rule.startDateMillis,
                        rule.endDateMillis, rule.frequency, from, today, MAX_PER_RULE_PER_RUN);
                for (long occurrence : due) {
                    if (postOccurrence(db, rule, occurrence, nowMillis)) created++;
                }
                long next = due.size() >= MAX_PER_RULE_PER_RUN
                        ? MonthUtils.addDays(due.get(due.size() - 1), 1) : tomorrow;
                if (next != rule.nextCheckMillis) db.recurringRuleDao().setNextCheck(rule.id, next);
            }
            return created;
        });
    }

    /** Inserts the posting marker first; only if that was new is the transaction created. */
    private static boolean postOccurrence(AppDatabase db, RecurringRule rule, long occurrence, long now) {
        RecurringPosting p = new RecurringPosting();
        p.ruleId = rule.id;
        p.occurrenceMillis = occurrence;
        p.createdAt = now;
        long postingId = db.recurringPostingDao().insertIfAbsent(p);
        if (postingId == -1) return false; // already created before
        Transaction t = new Transaction();
        t.type = rule.type;
        t.amountPaisa = rule.amountPaisa;
        t.category = rule.category;
        t.note = rule.note.isEmpty() ? "Recurring" : rule.note;
        t.dateMillis = occurrence;
        t.createdAt = now;
        long txId = db.transactionDao().insertReturningId(t);
        db.recurringPostingDao().setTransactionId(postingId, txId);
        return true;
    }
}

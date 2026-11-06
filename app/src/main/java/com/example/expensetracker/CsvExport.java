package com.example.expensetracker;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stage 5: builds the CSV files (one per data set). Pure Java: takes entity lists, writes to an Appendable. */
final class CsvExport {
    enum Dataset {
        TRANSACTIONS("transactions"),
        BUDGETS("budgets"),
        GOALS("savings_goals"),
        CONTRIBUTIONS("goal_contributions"),
        RECURRING("recurring_rules"),
        BILLS("bills");

        final String fileStem;

        Dataset(String fileStem) {
            this.fileStem = fileStem;
        }
    }

    /** Written first so Excel opens the file as UTF-8 (category names and notes may be Bengali). */
    static final String BOM = "\uFEFF";

    private CsvExport() {}

    static String dateText(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }

    /** Transactions with {@code start <= date < end}, newest first, as CSV. */
    static void writeTransactions(Appendable out, List<Transaction> all, long start, long end) throws IOException {
        List<Transaction> rows = new ArrayList<>();
        for (Transaction t : all) {
            if (t.dateMillis >= start && t.dateMillis < end) rows.add(t);
        }
        Collections.sort(rows, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction a, Transaction b) {
                if (a.dateMillis != b.dateMillis) return a.dateMillis < b.dateMillis ? 1 : -1;
                return Long.compare(b.id, a.id);
            }
        });
        out.append(BOM);
        CsvWriter w = new CsvWriter(out);
        w.text("Date").text("Type").text("Category").text("Amount (BDT)").text("Note").endRow();
        for (Transaction t : rows) {
            w.number(dateText(t.dateMillis))
                    .text(t.isIncome() ? "Income" : "Expense")
                    .text(t.category == null ? "" : t.category)
                    .number(MoneyUtils.plain(t.amountPaisa))
                    .text(t.note == null ? "" : t.note)
                    .endRow();
        }
    }

    static void writeBudgets(Appendable out, List<Budget> budgets) throws IOException {
        List<Budget> rows = new ArrayList<>(budgets);
        Collections.sort(rows, new Comparator<Budget>() {
            @Override
            public int compare(Budget a, Budget b) {
                int c = b.monthKey.compareTo(a.monthKey);
                return c != 0 ? c : a.category.compareTo(b.category);
            }
        });
        out.append(BOM);
        CsvWriter w = new CsvWriter(out);
        w.text("Month").text("Category").text("Budget (BDT)").endRow();
        for (Budget b : rows) {
            w.number(b.monthKey).text(b.isOverall() ? "Overall monthly budget" : b.category)
                    .number(MoneyUtils.plain(b.amountPaisa)).endRow();
        }
    }

    static void writeGoals(Appendable out, List<SavingsGoal> goals, List<GoalContribution> contributions)
            throws IOException {
        Map<Long, Long> saved = new HashMap<>();
        for (GoalContribution c : contributions) {
            Long cur = saved.get(c.goalId);
            saved.put(c.goalId, (cur == null ? 0L : cur) + c.amountPaisa);
        }
        out(out);
        CsvWriter w = new CsvWriter(out);
        w.text("Goal").text("Target (BDT)").text("Saved (BDT)").text("Remaining (BDT)")
                .text("Deadline").text("Archived").text("Created").endRow();
        for (SavingsGoal g : goals) {
            Long s = saved.get(g.id);
            long savedPaisa = s == null ? 0L : s;
            w.text(g.name).number(MoneyUtils.plain(g.targetPaisa)).number(MoneyUtils.plain(savedPaisa))
                    .number(MoneyUtils.plain(Math.max(0L, g.targetPaisa - savedPaisa)))
                    .number(dateText(g.deadlineMillis)).text(g.archived ? "Yes" : "No")
                    .number(dateText(g.createdAt)).endRow();
        }
    }

    static void writeContributions(Appendable out, List<GoalContribution> contributions, List<SavingsGoal> goals)
            throws IOException {
        Map<Long, String> names = new HashMap<>();
        for (SavingsGoal g : goals) names.put(g.id, g.name);
        List<GoalContribution> rows = new ArrayList<>(contributions);
        Collections.sort(rows, new Comparator<GoalContribution>() {
            @Override
            public int compare(GoalContribution a, GoalContribution b) {
                if (a.dateMillis != b.dateMillis) return a.dateMillis < b.dateMillis ? 1 : -1;
                return Long.compare(b.id, a.id);
            }
        });
        out(out);
        CsvWriter w = new CsvWriter(out);
        w.text("Goal").text("Date").text("Amount (BDT)").text("Note").endRow();
        for (GoalContribution c : rows) {
            String name = names.get(c.goalId);
            w.text(name == null ? "" : name).number(dateText(c.dateMillis))
                    .number(MoneyUtils.plain(c.amountPaisa)).text(c.note).endRow();
        }
    }

    static void writeRecurring(Appendable out, List<RecurringRule> rules) throws IOException {
        out(out);
        CsvWriter w = new CsvWriter(out);
        w.text("Type").text("Category").text("Amount (BDT)").text("Repeats").text("Start date")
                .text("End date").text("Active").text("Note").endRow();
        for (RecurringRule r : rules) {
            w.text(r.isIncome() ? "Income" : "Expense").text(r.category).number(MoneyUtils.plain(r.amountPaisa))
                    .text(frequencyText(r.frequency)).number(dateText(r.startDateMillis))
                    .number(dateText(r.endDateMillis)).text(r.active ? "Yes" : "Paused").text(r.note).endRow();
        }
    }

    static void writeBills(Appendable out, List<Bill> bills) throws IOException {
        List<Bill> rows = new ArrayList<>(bills);
        Collections.sort(rows, new Comparator<Bill>() {
            @Override
            public int compare(Bill a, Bill b) {
                return a.dueDateMillis != b.dueDateMillis ? Long.compare(a.dueDateMillis, b.dueDateMillis)
                        : Long.compare(a.id, b.id);
            }
        });
        out(out);
        CsvWriter w = new CsvWriter(out);
        w.text("Bill").text("Category").text("Amount (BDT)").text("Due date")
                .text("Reminder (days before)").text("Status").text("Paid on").endRow();
        for (Bill b : rows) {
            w.text(b.name).text(b.category).number(MoneyUtils.plain(b.amountPaisa))
                    .number(dateText(b.dueDateMillis)).number(String.valueOf(b.reminderDaysBefore))
                    .text(b.paid ? "Paid" : "Unpaid").number(b.paid ? dateText(b.paidAt) : "").endRow();
        }
    }

    private static void out(Appendable out) throws IOException {
        out.append(BOM);
    }

    private static String frequencyText(String f) {
        if (RecurrenceCalculator.DAILY.equals(f)) return "Daily";
        if (RecurrenceCalculator.WEEKLY.equals(f)) return "Weekly";
        return "Monthly";
    }
}

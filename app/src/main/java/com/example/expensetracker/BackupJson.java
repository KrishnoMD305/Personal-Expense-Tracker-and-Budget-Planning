package com.example.expensetracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Stage 5: reads and writes the backup file (one UTF-8 JSON document).
 *
 * <p>Writing is straightforward. Reading is deliberately strict: the whole file is parsed and checked
 * (format marker, version, field types, value ranges, unique ids, and that every reference points to a
 * row that exists) <em>before</em> anything touches the database, so a damaged or foreign file can never
 * leave the app half-restored. Uses only {@code org.json}, so it is unit-tested on the JVM.
 */
final class BackupJson {
    static final String APP_MARKER = "Personal_Expense_Tracker_and_Budget_Planning";
    static final int FORMAT_VERSION = 1;
    /** Highest Room schema version this build can restore (see {@link AppDatabase}). */
    static final int MAX_DB_VERSION = AppDatabase.VERSION; // compile-time constant: no class loading

    private BackupJson() {}

    // ------------------------------------------------------------------ write

    static String toJson(BackupData d) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", APP_MARKER);
        root.put("backupFormat", FORMAT_VERSION);
        root.put("dbVersion", d.dbVersion);
        root.put("appVersion", d.appVersion);
        root.put("createdAt", d.createdAtMillis);

        JSONObject settings = new JSONObject();
        settings.put("theme", d.theme);
        root.put("settings", settings);

        JSONObject counts = new JSONObject();
        JSONObject data = new JSONObject();

        JSONArray tx = new JSONArray();
        for (Transaction t : d.transactions) {
            JSONObject o = new JSONObject();
            o.put("id", t.id);
            o.put("type", t.type == null ? JSONObject.NULL : t.type);
            o.put("amountPaisa", t.amountPaisa);
            o.put("category", t.category == null ? JSONObject.NULL : t.category);
            o.put("note", t.note == null ? JSONObject.NULL : t.note);
            o.put("dateMillis", t.dateMillis);
            o.put("createdAt", t.createdAt);
            tx.put(o);
        }
        data.put("transactions", tx);

        JSONArray bu = new JSONArray();
        for (Budget b : d.budgets) {
            JSONObject o = new JSONObject();
            o.put("id", b.id);
            o.put("monthKey", b.monthKey);
            o.put("category", b.category);
            o.put("amountPaisa", b.amountPaisa);
            o.put("createdAt", b.createdAt);
            bu.put(o);
        }
        data.put("budgets", bu);

        JSONArray go = new JSONArray();
        for (SavingsGoal g : d.goals) {
            JSONObject o = new JSONObject();
            o.put("id", g.id);
            o.put("name", g.name);
            o.put("targetPaisa", g.targetPaisa);
            o.put("deadlineMillis", g.deadlineMillis);
            o.put("createdAt", g.createdAt);
            o.put("archived", g.archived);
            go.put(o);
        }
        data.put("savingsGoals", go);

        JSONArray co = new JSONArray();
        for (GoalContribution c : d.contributions) {
            JSONObject o = new JSONObject();
            o.put("id", c.id);
            o.put("goalId", c.goalId);
            o.put("amountPaisa", c.amountPaisa);
            o.put("dateMillis", c.dateMillis);
            o.put("note", c.note);
            o.put("createdAt", c.createdAt);
            co.put(o);
        }
        data.put("goalContributions", co);

        JSONArray ru = new JSONArray();
        for (RecurringRule r : d.rules) {
            JSONObject o = new JSONObject();
            o.put("id", r.id);
            o.put("type", r.type);
            o.put("amountPaisa", r.amountPaisa);
            o.put("category", r.category);
            o.put("note", r.note);
            o.put("frequency", r.frequency);
            o.put("startDateMillis", r.startDateMillis);
            o.put("endDateMillis", r.endDateMillis);
            o.put("active", r.active);
            o.put("nextCheckMillis", r.nextCheckMillis);
            o.put("createdAt", r.createdAt);
            ru.put(o);
        }
        data.put("recurringRules", ru);

        JSONArray po = new JSONArray();
        for (RecurringPosting p : d.postings) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("ruleId", p.ruleId);
            o.put("occurrenceMillis", p.occurrenceMillis);
            o.put("transactionId", p.transactionId);
            o.put("createdAt", p.createdAt);
            po.put(o);
        }
        data.put("recurringPostings", po);

        JSONArray bi = new JSONArray();
        for (Bill b : d.bills) {
            JSONObject o = new JSONObject();
            o.put("id", b.id);
            o.put("name", b.name);
            o.put("amountPaisa", b.amountPaisa);
            o.put("category", b.category);
            o.put("dueDateMillis", b.dueDateMillis);
            o.put("reminderDaysBefore", b.reminderDaysBefore);
            o.put("paid", b.paid);
            o.put("paidAt", b.paidAt);
            o.put("paymentTransactionId", b.paymentTransactionId);
            o.put("lastNotifiedDay", b.lastNotifiedDay);
            o.put("createdAt", b.createdAt);
            bi.put(o);
        }
        data.put("bills", bi);

        counts.put("transactions", tx.length());
        counts.put("budgets", bu.length());
        counts.put("savingsGoals", go.length());
        counts.put("goalContributions", co.length());
        counts.put("recurringRules", ru.length());
        counts.put("recurringPostings", po.length());
        counts.put("bills", bi.length());
        root.put("counts", counts);
        root.put("data", data);
        return root.toString(2);
    }

    // ------------------------------------------------------------------ read

    /** Parses and validates a backup document. Never returns partially valid data: it throws instead. */
    static BackupData parse(String text) throws BackupException {
        if (text == null || text.trim().isEmpty()) {
            throw new BackupException("The file is empty.");
        }
        try {
            return parseObject(new JSONObject(stripBom(text)));
        } catch (JSONException e) {
            throw new BackupException("This is not a valid backup file (" + e.getMessage() + ").", e);
        }
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }

    private static BackupData parseObject(JSONObject root) throws JSONException, BackupException {
        if (!APP_MARKER.equals(root.optString("app", ""))) {
            throw new BackupException("This file was not created by this app.");
        }
        int format = root.getInt("backupFormat");
        if (format < 1 || format > FORMAT_VERSION) {
            throw new BackupException("This backup uses a newer file format (" + format
                    + "). Update the app to restore it.");
        }
        BackupData d = new BackupData();
        d.dbVersion = root.getInt("dbVersion");
        if (d.dbVersion < 1 || d.dbVersion > MAX_DB_VERSION) {
            throw new BackupException("This backup comes from a newer version of the app (database version "
                    + d.dbVersion + "). Update the app to restore it.");
        }
        d.appVersion = root.optString("appVersion", "");
        d.createdAtMillis = root.optLong("createdAt", 0L);
        JSONObject settings = root.optJSONObject("settings");
        d.theme = settings == null ? "" : settings.optString("theme", "");

        JSONObject data = root.getJSONObject("data");
        JSONObject counts = root.optJSONObject("counts");

        JSONArray tx = array(data, "transactions");
        Set<Long> txIds = new HashSet<>();
        for (int i = 0; i < tx.length(); i++) {
            JSONObject o = tx.getJSONObject(i);
            Transaction t = new Transaction();
            t.id = id(o, "transactions", i, txIds);
            t.type = nullableString(o, "type");
            if (t.type != null && !Transaction.TYPE_INCOME.equals(t.type) && !Transaction.TYPE_EXPENSE.equals(t.type)) {
                throw bad("transactions", i, "unknown type \"" + t.type + "\"");
            }
            t.amountPaisa = money(o, "amountPaisa", "transactions", i);
            t.category = nullableString(o, "category");
            t.note = nullableString(o, "note");
            t.dateMillis = o.getLong("dateMillis");
            t.createdAt = o.getLong("createdAt");
            d.transactions.add(t);
        }

        JSONArray bu = array(data, "budgets");
        Set<Long> ids = new HashSet<>();
        Set<String> budgetKeys = new HashSet<>();
        for (int i = 0; i < bu.length(); i++) {
            JSONObject o = bu.getJSONObject(i);
            Budget b = new Budget();
            b.id = id(o, "budgets", i, ids);
            b.monthKey = string(o, "monthKey", "budgets", i);
            if (!MonthUtils.isValidKey(b.monthKey)) throw bad("budgets", i, "invalid month \"" + b.monthKey + "\"");
            b.category = string(o, "category", "budgets", i);
            if (!budgetKeys.add(b.monthKey + "|" + b.category)) {
                throw bad("budgets", i, "duplicate budget for " + b.monthKey + " / " + b.category);
            }
            b.amountPaisa = money(o, "amountPaisa", "budgets", i);
            b.createdAt = o.getLong("createdAt");
            d.budgets.add(b);
        }

        JSONArray go = array(data, "savingsGoals");
        Set<Long> goalIds = new HashSet<>();
        for (int i = 0; i < go.length(); i++) {
            JSONObject o = go.getJSONObject(i);
            SavingsGoal g = new SavingsGoal();
            g.id = id(o, "savingsGoals", i, goalIds);
            g.name = string(o, "name", "savingsGoals", i);
            g.targetPaisa = money(o, "targetPaisa", "savingsGoals", i);
            g.deadlineMillis = o.getLong("deadlineMillis");
            g.createdAt = o.getLong("createdAt");
            g.archived = o.getBoolean("archived");
            d.goals.add(g);
        }

        JSONArray co = array(data, "goalContributions");
        ids = new HashSet<>();
        for (int i = 0; i < co.length(); i++) {
            JSONObject o = co.getJSONObject(i);
            GoalContribution c = new GoalContribution();
            c.id = id(o, "goalContributions", i, ids);
            c.goalId = o.getLong("goalId");
            if (!goalIds.contains(c.goalId)) throw bad("goalContributions", i, "refers to a missing savings goal");
            c.amountPaisa = money(o, "amountPaisa", "goalContributions", i);
            c.dateMillis = o.getLong("dateMillis");
            c.note = string(o, "note", "goalContributions", i);
            c.createdAt = o.getLong("createdAt");
            d.contributions.add(c);
        }

        JSONArray ru = array(data, "recurringRules");
        Set<Long> ruleIds = new HashSet<>();
        for (int i = 0; i < ru.length(); i++) {
            JSONObject o = ru.getJSONObject(i);
            RecurringRule r = new RecurringRule();
            r.id = id(o, "recurringRules", i, ruleIds);
            r.type = string(o, "type", "recurringRules", i);
            if (!Transaction.TYPE_INCOME.equals(r.type) && !Transaction.TYPE_EXPENSE.equals(r.type)) {
                throw bad("recurringRules", i, "unknown type \"" + r.type + "\"");
            }
            r.amountPaisa = money(o, "amountPaisa", "recurringRules", i);
            r.category = string(o, "category", "recurringRules", i);
            r.note = string(o, "note", "recurringRules", i);
            r.frequency = string(o, "frequency", "recurringRules", i);
            if (!RecurrenceCalculator.DAILY.equals(r.frequency) && !RecurrenceCalculator.WEEKLY.equals(r.frequency)
                    && !RecurrenceCalculator.MONTHLY.equals(r.frequency)) {
                throw bad("recurringRules", i, "unknown frequency \"" + r.frequency + "\"");
            }
            r.startDateMillis = o.getLong("startDateMillis");
            r.endDateMillis = o.getLong("endDateMillis");
            r.active = o.getBoolean("active");
            r.nextCheckMillis = o.getLong("nextCheckMillis");
            r.createdAt = o.getLong("createdAt");
            d.rules.add(r);
        }

        JSONArray po = array(data, "recurringPostings");
        ids = new HashSet<>();
        Set<String> postingKeys = new HashSet<>();
        for (int i = 0; i < po.length(); i++) {
            JSONObject o = po.getJSONObject(i);
            RecurringPosting p = new RecurringPosting();
            p.id = id(o, "recurringPostings", i, ids);
            p.ruleId = o.getLong("ruleId");
            if (!ruleIds.contains(p.ruleId)) throw bad("recurringPostings", i, "refers to a missing recurring rule");
            p.occurrenceMillis = o.getLong("occurrenceMillis");
            if (!postingKeys.add(p.ruleId + "|" + p.occurrenceMillis)) {
                throw bad("recurringPostings", i, "the same occurrence is listed twice");
            }
            p.transactionId = o.getLong("transactionId");
            p.createdAt = o.getLong("createdAt");
            d.postings.add(p);
        }

        JSONArray bi = array(data, "bills");
        ids = new HashSet<>();
        for (int i = 0; i < bi.length(); i++) {
            JSONObject o = bi.getJSONObject(i);
            Bill b = new Bill();
            b.id = id(o, "bills", i, ids);
            b.name = string(o, "name", "bills", i);
            b.amountPaisa = money(o, "amountPaisa", "bills", i);
            b.category = string(o, "category", "bills", i);
            b.dueDateMillis = o.getLong("dueDateMillis");
            b.reminderDaysBefore = o.getInt("reminderDaysBefore");
            if (b.reminderDaysBefore < 0 || b.reminderDaysBefore > 365) {
                throw bad("bills", i, "reminder days out of range");
            }
            b.paid = o.getBoolean("paid");
            b.paidAt = o.getLong("paidAt");
            b.paymentTransactionId = o.getLong("paymentTransactionId");
            b.lastNotifiedDay = o.getLong("lastNotifiedDay");
            b.createdAt = o.getLong("createdAt");
            d.bills.add(b);
        }

        // The header's counts must match what was actually read: catches a file cut off in transit.
        if (counts != null) {
            expectCount(counts, "transactions", d.transactions.size());
            expectCount(counts, "budgets", d.budgets.size());
            expectCount(counts, "savingsGoals", d.goals.size());
            expectCount(counts, "goalContributions", d.contributions.size());
            expectCount(counts, "recurringRules", d.rules.size());
            expectCount(counts, "recurringPostings", d.postings.size());
            expectCount(counts, "bills", d.bills.size());
        }
        return d;
    }

    // ------------------------------------------------------------------ helpers

    private static JSONArray array(JSONObject data, String key) throws BackupException {
        JSONArray a = data.optJSONArray(key);
        if (a == null) throw new BackupException("The backup is incomplete: the \"" + key + "\" section is missing.");
        return a;
    }

    private static long id(JSONObject o, String table, int i, Set<Long> seen) throws JSONException, BackupException {
        long id = o.getLong("id");
        if (id <= 0) throw bad(table, i, "invalid id " + id);
        if (!seen.add(id)) throw bad(table, i, "duplicate id " + id);
        return id;
    }

    private static long money(JSONObject o, String key, String table, int i) throws JSONException, BackupException {
        long v = o.getLong(key);
        if (v <= 0 || v > MoneyUtils.MAX_PAISA) throw bad(table, i, "amount out of range");
        return v;
    }

    private static String string(JSONObject o, String key, String table, int i) throws JSONException, BackupException {
        if (o.isNull(key)) throw bad(table, i, "\"" + key + "\" is missing");
        return o.getString(key);
    }

    private static String nullableString(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }

    private static void expectCount(JSONObject counts, String key, int actual) throws BackupException {
        if (counts.has(key) && counts.optInt(key, -1) != actual) {
            throw new BackupException("The backup is damaged: expected " + counts.optInt(key, -1) + " "
                    + key + " but found " + actual + ".");
        }
    }

    private static BackupException bad(String table, int index, String what) {
        return new BackupException("The backup is damaged (" + table + " #" + (index + 1) + ": " + what + ").");
    }
}

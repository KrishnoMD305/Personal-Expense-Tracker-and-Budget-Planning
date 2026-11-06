package com.example.expensetracker;

import java.util.ArrayList;
import java.util.List;

/** Stage 5: a complete snapshot of the user's data (every table), used for backup files and restore. */
final class BackupData {
    List<Transaction> transactions = new ArrayList<>();
    List<Budget> budgets = new ArrayList<>();
    List<SavingsGoal> goals = new ArrayList<>();
    List<GoalContribution> contributions = new ArrayList<>();
    List<RecurringRule> rules = new ArrayList<>();
    List<RecurringPosting> postings = new ArrayList<>();
    List<Bill> bills = new ArrayList<>();

    /** Theme stored in the backup ("" when the file has none). */
    String theme = "";
    /** When the backup was made (epoch millis; 0 if unknown). */
    long createdAtMillis;
    /** Room database version of the app that wrote the file. */
    int dbVersion;
    String appVersion = "";

    int totalRows() {
        return transactions.size() + budgets.size() + goals.size() + contributions.size()
                + rules.size() + postings.size() + bills.size();
    }
}

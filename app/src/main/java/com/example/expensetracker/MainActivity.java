package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextView balanceView, incomeView, expenseView, emptyView;
    private TextView budgetTitle, budgetPercent, budgetLine1, budgetLine2, budgetAlerts;
    private LinearProgressIndicator budgetProgress;
    private long totalIncome = 0, totalExpense = 0;
    private TransactionAdapter adapter;
    private TransactionDao dao;
    private List<String> expenseCategories;
    private String boundMonth;
    private LiveData<MonthOverview> budgetSource;
    private TextView goalsBadge, goalsLine1, goalsLine2, recurringBadge, recurringLine1, recurringLine2, billsBadge, billsLine1, billsLine2;
    private LinearProgressIndicator goalsProgress;
    private List<GoalWithSaved> lastGoals;
    private List<RecurringRule> lastRules;
    private List<Bill> lastBills;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        balanceView = findViewById(R.id.balanceValue);
        incomeView = findViewById(R.id.incomeValue);
        expenseView = findViewById(R.id.expenseValue);
        emptyView = findViewById(R.id.emptyView);
        budgetTitle = findViewById(R.id.budgetCardTitle);
        budgetPercent = findViewById(R.id.budgetCardPercent);
        budgetLine1 = findViewById(R.id.budgetCardLine1);
        budgetLine2 = findViewById(R.id.budgetCardLine2);
        budgetAlerts = findViewById(R.id.budgetCardAlerts);
        budgetProgress = findViewById(R.id.budgetCardProgress);
        goalsBadge = findViewById(R.id.goalsBadge);
        goalsLine1 = findViewById(R.id.goalsLine1);
        goalsLine2 = findViewById(R.id.goalsLine2);
        goalsProgress = findViewById(R.id.goalsProgress);
        recurringBadge = findViewById(R.id.recurringBadge);
        recurringLine1 = findViewById(R.id.recurringLine1);
        recurringLine2 = findViewById(R.id.recurringLine2);
        billsBadge = findViewById(R.id.billsBadge);
        billsLine1 = findViewById(R.id.billsLine1);
        billsLine2 = findViewById(R.id.billsLine2);
        RecyclerView recycler = findViewById(R.id.transactionList);
        FloatingActionButton fab = findViewById(R.id.fabAdd);
        expenseCategories = Arrays.asList(getResources().getStringArray(R.array.expense_categories));
        dao = AppDatabase.getInstance(this).transactionDao();
        adapter = new TransactionAdapter(this::openEditor, this::showOptions);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        dao.getAll().observe(this, list -> {
            adapter.submitList(list);
            emptyView.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        dao.sumByType(Transaction.TYPE_INCOME).observe(this, v -> {
            totalIncome = v == null ? 0 : v;
            refreshSummary();
        });
        dao.sumByType(Transaction.TYPE_EXPENSE).observe(this, v -> {
            totalExpense = v == null ? 0 : v;
            refreshSummary();
        });

        fab.setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));
        findViewById(R.id.budgetCard).setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        findViewById(R.id.reportsButton).setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
        findViewById(R.id.goalsSection).setOnClickListener(v -> startActivity(new Intent(this, GoalsActivity.class)));
        findViewById(R.id.recurringSection).setOnClickListener(v -> startActivity(new Intent(this, RecurringActivity.class)));
        findViewById(R.id.billsSection).setOnClickListener(v -> startActivity(new Intent(this, BillsActivity.class)));
        GoalRepository.observeAll(this).observe(this, goals -> {
            lastGoals = goals;
            renderPlanning();
        });
        RecurringRepository.observeAll(this).observe(this, rules -> {
            lastRules = rules;
            renderPlanning();
        });
        BillRepository.observeAll(this).observe(this, bills -> {
            lastBills = bills;
            renderPlanning();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindBudgetMonth(); 
        BackgroundWork.runNow(this); 
        renderPlanning();         
    }

    private void renderPlanning() {
        if (lastGoals == null && lastRules == null && lastBills == null) return;
        long today = PlanDates.today();
        renderGoals(DashboardCalculator.goals(lastGoals, today), today);
        renderRecurring(DashboardCalculator.recurring(lastRules, today));
        renderBills(DashboardCalculator.bills(lastBills, today), today);
    }
    private void renderGoals(DashboardCalculator.GoalsSummary g, long today) {
        if (!g.hasGoals()) {
            goalsBadge.setText("");
            goalsProgress.setVisibility(View.GONE);
            goalsLine1.setText(lastGoals != null && !lastGoals.isEmpty() ? R.string.dash_goals_all_archived : R.string.dash_goals_none);
            goalsLine2.setVisibility(View.GONE);
            return;
        }
        int color = ContextCompat.getColor(this, g.completedCount == g.activeCount ? R.color.income : R.color.accent);
        goalsBadge.setText(g.percentText + "%");
        goalsBadge.setTextColor(color);
        goalsProgress.setVisibility(View.VISIBLE);
        goalsProgress.setIndicatorColor(color);
        goalsProgress.setProgressCompat(g.progress, false);
        goalsLine1.setText(getString(R.string.dash_goals_line1, MoneyUtils.format(g.totalSavedPaisa), MoneyUtils.format(g.totalTargetPaisa), g.activeCount, g.completedCount));
        goalsLine2.setVisibility(View.VISIBLE);
        if (g.nextDeadlineGoal != null) {
            String when = g.nextDeadlineDays < 0 ? getString(R.string.dash_overdue_by, PlanText.days(this, -g.nextDeadlineDays)) : g.nextDeadlineDays == 0 ? getString(R.string.dash_today) : getString(R.string.dash_in, PlanText.days(this, g.nextDeadlineDays));
            goalsLine2.setText(getString(R.string.dash_goals_next, g.nextDeadlineGoal.name, PlanDates.format(g.nextDeadlineGoal.deadlineMillis), when));
        } else if (g.completedCount == g.activeCount) {
            goalsLine2.setText(R.string.dash_goals_all_done);
        } else {
            goalsLine2.setText(R.string.dash_goals_no_deadlines);
        }
    }

    private void renderRecurring(DashboardCalculator.RecurringSummary r) {
        if (!r.hasRules()) {
            recurringBadge.setText("");
            recurringLine1.setText(R.string.dash_recurring_none);
            recurringLine2.setVisibility(View.GONE);
            return;
        }
        recurringBadge.setText(r.pausedCount > 0 ? getString(R.string.dash_recurring_badge_paused, r.activeCount, r.pausedCount) : getString(R.string.dash_recurring_badge, r.activeCount));
        recurringBadge.setTextColor(ContextCompat.getColor(this, R.color.accent));
        recurringLine1.setText(r.nextRule == null ? getString(R.string.dash_recurring_no_next) : getString(R.string.dash_recurring_next, r.nextRule.category, (r.nextRule.isIncome() ? "+ " : "- ") + MoneyUtils.format(r.nextRule.amountPaisa), PlanDates.format(r.nextMillis)));
        recurringLine2.setVisibility(View.VISIBLE);
        recurringLine2.setText(getString(R.string.dash_recurring_forecast, DashboardCalculator.FORECAST_DAYS, MoneyUtils.format(r.upcomingIncomePaisa), MoneyUtils.format(r.upcomingExpensePaisa)));
    }

    private void renderBills(DashboardCalculator.BillsSummary b, long today) {
        if (!b.hasBills()) {
            billsBadge.setText("");
            billsLine1.setText(lastBills != null && !lastBills.isEmpty() ? R.string.dash_bills_all_paid : R.string.dash_bills_none);
            billsLine2.setVisibility(View.GONE);
            return;
        }
        if (b.overdueCount > 0) {
            billsBadge.setText(getString(R.string.dash_bills_badge_overdue, b.overdueCount));
            billsBadge.setTextColor(ContextCompat.getColor(this, R.color.expense));
            billsLine1.setText(getString(R.string.dash_bills_overdue, billCount(b.overdueCount), MoneyUtils.format(b.overdueTotalPaisa)));
        } else {
            billsBadge.setText(b.dueSoonCount > 0 ? getString(R.string.dash_bills_badge_soon, b.dueSoonCount) : "");
            billsBadge.setTextColor(ContextCompat.getColor(this, R.color.warning));
            billsLine1.setText(R.string.dash_bills_no_overdue);
        }
        StringBuilder l2 = new StringBuilder(getString(R.string.dash_bills_soon, DashboardCalculator.DUE_SOON_DAYS, billCount(b.dueSoonCount), MoneyUtils.format(b.dueSoonTotalPaisa)));
        if (b.nextBill != null) {
            String when = b.nextBillDays == 0 ? getString(R.string.dash_today) : getString(R.string.dash_in, PlanText.days(this, b.nextBillDays));
            l2.append('\n').append(getString(R.string.dash_bills_next, b.nextBill.name, MoneyUtils.format(b.nextBill.amountPaisa), when));
        }
        billsLine2.setVisibility(View.VISIBLE);
        billsLine2.setText(l2.toString());
    }

    private String billCount(int n) {
        return getResources().getQuantityString(R.plurals.bills_count, n, n);
    }

    private void bindBudgetMonth() {
        String month = MonthUtils.current();
        if (month.equals(boundMonth)) return;
        if (budgetSource != null) budgetSource.removeObservers(this);
        boundMonth = month;
        budgetSource = BudgetRepository.observeMonth(this, month);
        budgetSource.observe(this, this::renderBudget);
    }

    private void renderBudget(MonthOverview o) {
        budgetTitle.setText(getString(R.string.budget_card_title, MonthUtils.label(o.monthKey)));
        BudgetStatus s = BudgetCalculator.overall(o);
        BudgetUi.bindProgress(budgetProgress, budgetPercent, s);
        if (s.hasBudget()) {
            budgetProgress.setVisibility(View.VISIBLE);
            budgetLine1.setText(getString(R.string.budget_line_spent, MoneyUtils.format(s.spentPaisa), MoneyUtils.format(s.budgetPaisa)));
            budgetLine2.setText(s.isOverspent() ? getString(R.string.budget_line_over, MoneyUtils.format(-s.remainingPaisa), s.percentText) : getString(R.string.budget_line_left, MoneyUtils.format(s.remainingPaisa), s.percentText));
            budgetLine2.setVisibility(View.VISIBLE);
        } else {
            budgetProgress.setVisibility(View.GONE);
            if (o.hasAnyBudget()) {
                budgetLine1.setText(getString(R.string.budget_line_spent_only, MoneyUtils.format(o.totalSpent())));
            } else {
                budgetLine1.setText(R.string.no_monthly_budget);
            }
            budgetLine2.setVisibility(View.GONE);
        }
        BudgetUi.bindBanner(budgetAlerts, BudgetAlerts.forMonth(o, expenseCategories));
    }

    private void refreshSummary() {
        long balance = totalIncome - totalExpense; 
        balanceView.setText(MoneyUtils.format(balance));
        balanceView.setTextColor(ContextCompat.getColor(this, balance < 0 ? R.color.expense : R.color.on_primary));
        incomeView.setText(MoneyUtils.format(totalIncome));
        expenseView.setText(MoneyUtils.format(totalExpense));
    }
    private void openEditor(Transaction t) {
        Intent i = new Intent(this, AddTransactionActivity.class);
        i.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.id);
        startActivity(i);
    }
    private void showOptions(Transaction t) {
        new AlertDialog.Builder(this).setTitle(t.category).setItems(new CharSequence[]{getString(R.string.edit), getString(R.string.delete)},
                        (d, which) -> {
                            if (which == 0) openEditor(t);
                            else confirmDelete(t);
                        }).show();
    }
    private void confirmDelete(Transaction t) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, t.category, MoneyUtils.format(t.amountPaisa)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AppDatabase.WRITE_EXECUTOR.execute(() -> dao.delete(t));
                    Snackbar.make(findViewById(R.id.mainRoot), R.string.deleted, Snackbar.LENGTH_LONG).setAction(R.string.undo, v ->AppDatabase.WRITE_EXECUTOR.execute(() -> dao.insert(t))).show();
                }).show();
    }
}

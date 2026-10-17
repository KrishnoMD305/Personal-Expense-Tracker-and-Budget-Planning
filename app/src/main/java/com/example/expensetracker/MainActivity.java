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
        findViewById(R.id.budgetCard).setOnClickListener(v ->startActivity(new Intent(this, BudgetActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindBudgetMonth(); 
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
            budgetLine1.setText(getString(R.string.budget_line_spent,MoneyUtils.format(s.spentPaisa), MoneyUtils.format(s.budgetPaisa)));
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
        new AlertDialog.Builder(this).setTitle(R.string.delete_title).setMessage(getString(R.string.delete_message, t.category, MoneyUtils.format(t.amountPaisa))).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AppDatabase.WRITE_EXECUTOR.execute(() -> dao.delete(t));
                    Snackbar.make(findViewById(R.id.mainRoot), R.string.deleted, Snackbar.LENGTH_LONG).setAction(R.string.undo, v ->
                                    AppDatabase.WRITE_EXECUTOR.execute(() -> dao.insert(t))).show();
                }).show();
    }
}

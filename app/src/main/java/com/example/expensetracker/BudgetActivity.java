package com.example.expensetracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Monthly budget setup, category budgets, and live progress (spent / remaining / % used). */
public class BudgetActivity extends BaseActivity {
    private static final String STATE_MONTH = "month";
    /** Navigation limit so month keys always stay valid. */
    private static final int MAX_MONTHS_AWAY = 600;

    private String monthKey;
    private LiveData<MonthOverview> source;
    private List<String> categories;
    private BudgetAdapter adapter;

    private TextView monthLabel, overallPercent, overallBudgetValue, overallSpentValue,
            overallRemainingLabel, overallRemainingValue, alertBanner;
    private LinearProgressIndicator overallProgress;
    private Button setOverallButton, copyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        categories = Arrays.asList(getResources().getStringArray(R.array.expense_categories));
        String saved = savedInstanceState == null ? null : savedInstanceState.getString(STATE_MONTH);
        monthKey = MonthUtils.isValidKey(saved) ? saved : MonthUtils.current();

        monthLabel = findViewById(R.id.monthLabel);
        overallPercent = findViewById(R.id.overallPercent);
        overallBudgetValue = findViewById(R.id.overallBudgetValue);
        overallSpentValue = findViewById(R.id.overallSpentValue);
        overallRemainingLabel = findViewById(R.id.overallRemainingLabel);
        overallRemainingValue = findViewById(R.id.overallRemainingValue);
        alertBanner = findViewById(R.id.alertBanner);
        overallProgress = findViewById(R.id.overallProgress);
        setOverallButton = findViewById(R.id.setOverallButton);
        copyButton = findViewById(R.id.copyButton);

        RecyclerView list = findViewById(R.id.budgetList);
        adapter = new BudgetAdapter(row -> showAmountDialog(row.category,
                getString(R.string.budget_for, row.category), row.status.budgetPaisa));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.prevMonth).setOnClickListener(v -> changeMonth(-1));
        findViewById(R.id.nextMonth).setOnClickListener(v -> changeMonth(1));
        setOverallButton.setOnClickListener(v -> {
            MonthOverview o = lastOverview;
            showAmountDialog(Budget.OVERALL, getString(R.string.monthly_budget),
                    o == null ? 0 : o.overallBudget());
        });
        copyButton.setOnClickListener(v -> copyFromLastMonth());

        bindMonth();
    }

    private MonthOverview lastOverview;

    private void changeMonth(int delta) {
        String target = MonthUtils.shift(monthKey, delta);
        int away = monthsBetween(MonthUtils.current(), target);
        if (Math.abs(away) > MAX_MONTHS_AWAY) return;
        monthKey = target;
        bindMonth();
    }

    private static int monthsBetween(String from, String to) {
        int a = Integer.parseInt(from.substring(0, 4)) * 12 + Integer.parseInt(from.substring(5));
        int b = Integer.parseInt(to.substring(0, 4)) * 12 + Integer.parseInt(to.substring(5));
        return b - a;
    }

    private void bindMonth() {
        if (source != null) source.removeObservers(this);
        lastOverview = null;
        monthLabel.setText(MonthUtils.label(monthKey));
        source = BudgetRepository.observeMonth(this, monthKey);
        source.observe(this, this::render);
    }

    private void render(MonthOverview o) {
        lastOverview = o;
        BudgetStatus s = BudgetCalculator.overall(o);
        BudgetUi.bindProgress(overallProgress, overallPercent, s);
        overallSpentValue.setText(MoneyUtils.format(o.totalSpent()));
        if (s.hasBudget()) {
            overallBudgetValue.setText(MoneyUtils.format(s.budgetPaisa));
            if (s.isOverspent()) {
                overallRemainingLabel.setText(R.string.over_by);
                overallRemainingValue.setText(MoneyUtils.format(-s.remainingPaisa));
            } else {
                overallRemainingLabel.setText(R.string.remaining);
                overallRemainingValue.setText(MoneyUtils.format(s.remainingPaisa));
            }
            overallRemainingValue.setTextColor(BudgetUi.colorFor(this, s.level));
            setOverallButton.setText(R.string.edit_monthly_budget);
        } else {
            overallBudgetValue.setText(R.string.not_set);
            overallRemainingLabel.setText(R.string.remaining);
            overallRemainingValue.setText("—");
            overallRemainingValue.setTextColor(ThemeColors.get(this, R.attr.etTextTertiary));
            setOverallButton.setText(R.string.set_monthly_budget);
        }
        copyButton.setVisibility(o.hasAnyBudget() ? View.GONE : View.VISIBLE);
        BudgetUi.bindBanner(alertBanner, BudgetAlerts.forMonth(o, categories));

        List<BudgetAdapter.Row> rows = new ArrayList<>();
        for (String c : categories) rows.add(new BudgetAdapter.Row(c, BudgetCalculator.forCategory(o, c)));
        adapter.submitList(rows);
    }

    private void copyFromLastMonth() {
        copyButton.setEnabled(false);
        BudgetRepository.copyFromPreviousMonth(this, monthKey, copied -> {
            copyButton.setEnabled(true);
            if (copied < 0) Toast.makeText(this, R.string.copy_not_empty, Toast.LENGTH_SHORT).show();
            else if (copied == 0) Toast.makeText(this, R.string.copy_nothing, Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, getString(R.string.copied_budgets, copied), Toast.LENGTH_SHORT).show();
        });
    }

    /** Dialog to set, change or remove one budget. Validation errors are shown inline. */
    private void showAmountDialog(String category, String title, long currentPaisa) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_budget_amount, null);
        TextInputLayout layout = content.findViewById(R.id.budgetAmountLayout);
        TextInputEditText input = content.findViewById(R.id.budgetAmountInput);
        if (currentPaisa > 0) {
            input.setText(BigDecimal.valueOf(currentPaisa, 2).toPlainString());
            if (input.getText() != null) input.setSelection(input.getText().length());
        }
        final String month = monthKey;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null); // real handler set below (keeps dialog open on error)
        if (currentPaisa > 0) {
            b.setNeutralButton(R.string.remove, (d, w) ->
                    BudgetRepository.removeBudget(this, month, category, err ->
                            Toast.makeText(this, err == null ? getString(R.string.budget_removed) : err,
                                    Toast.LENGTH_SHORT).show()));
        }
        AlertDialog dialog = b.create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(v -> {
            layout.setError(null);
            long paisa;
            try {
                paisa = MoneyUtils.parseToPaisa(input.getText() == null ? "" : input.getText().toString());
            } catch (MoneyUtils.InvalidAmountException e) {
                layout.setError(e.getMessage());
                return;
            }
            ok.setEnabled(false);
            BudgetRepository.saveBudget(this, month, category, paisa, err -> {
                if (err == null) {
                    dialog.dismiss();
                    Toast.makeText(this, R.string.budget_saved, Toast.LENGTH_SHORT).show();
                } else {
                    ok.setEnabled(true);
                    layout.setError(err);
                }
            });
        });
        input.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MONTH, monthKey);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

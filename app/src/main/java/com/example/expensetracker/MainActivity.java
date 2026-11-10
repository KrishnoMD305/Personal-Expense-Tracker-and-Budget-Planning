package com.example.expensetracker;

import com.example.expensetracker.databinding.ActivityMainBinding;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;

    private TextView balanceView, incomeView, expenseView, emptyView;
    private TextView budgetTitle, budgetPercent, budgetLine1, budgetLine2, budgetAlerts;
    private LinearProgressIndicator budgetProgress;
    private long totalIncome = 0, totalExpense = 0;
    private TransactionAdapter adapter;
    private TransactionDao dao;
    private List<String> expenseCategories;
    private String boundMonth;
    private LiveData<MonthOverview> budgetSource;
    private DeviceStateReceiver deviceReceiver; // Stage 7

    // Stage 4: planning summaries (savings goals, recurring transactions, bills)
    private TextView goalsBadge, goalsLine1, goalsLine2, recurringBadge, recurringLine1, recurringLine2,
            billsBadge, billsLine1, billsLine2;
    private LinearProgressIndicator goalsProgress;
    private List<GoalWithSaved> lastGoals;
    private List<RecurringRule> lastRules;
    private List<Bill> lastBills;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        balanceView = binding.balanceValue;
        incomeView = binding.incomeValue;
        expenseView = binding.expenseValue;
        emptyView = binding.emptyView;
        budgetTitle = binding.budgetCardTitle;
        budgetPercent = binding.budgetCardPercent;
        budgetLine1 = binding.budgetCardLine1;
        budgetLine2 = binding.budgetCardLine2;
        budgetAlerts = binding.budgetCardAlerts;
        budgetProgress = binding.budgetCardProgress;
        goalsBadge = binding.goalsBadge;
        goalsLine1 = binding.goalsLine1;
        goalsLine2 = binding.goalsLine2;
        goalsProgress = binding.goalsProgress;
        recurringBadge = binding.recurringBadge;
        recurringLine1 = binding.recurringLine1;
        recurringLine2 = binding.recurringLine2;
        billsBadge = binding.billsBadge;
        billsLine1 = binding.billsLine1;
        billsLine2 = binding.billsLine2;
        RecyclerView recycler = binding.transactionList;
        FloatingActionButton fab = binding.fabAdd;

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
        binding.budgetCard.setOnClickListener(v ->
                startActivity(new Intent(this, BudgetActivity.class)));
        binding.reportsButton.setOnClickListener(v ->
                startActivity(new Intent(this, ReportsActivity.class)));

        // Stage 4: planning card. Each section opens its screen; LiveData keeps the numbers current.
        binding.goalsSection.setOnClickListener(v ->
                startActivity(new Intent(this, GoalsActivity.class)));
        binding.recurringSection.setOnClickListener(v ->
                startActivity(new Intent(this, RecurringActivity.class)));
        binding.billsSection.setOnClickListener(v ->
                startActivity(new Intent(this, BillsActivity.class)));
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

    // ------------------------------------------------------------------ Stage 7: device-state banner

    /** Dynamic registration: these broadcasts cannot be declared in the manifest, so we listen only while visible. */
    @Override
    protected void onStart() {
        super.onStart();
        if (deviceReceiver == null) {
            deviceReceiver = new DeviceStateReceiver(DeviceStateReader.read(this), this::renderDeviceBanner);
        }
        deviceReceiver.register(this); // registerReceiver(); also shows the current state straight away
    }

    @Override
    protected void onStop() {
        if (deviceReceiver != null) deviceReceiver.unregister(this); // unregisterReceiver()
        super.onStop();
    }

    private void renderDeviceBanner(DeviceStatus status) {
        String text;
        switch (status.banner()) {
            case OFFLINE:
                text = getString(R.string.banner_offline);
                break;
            case BATTERY_LOW:
                text = getString(R.string.banner_battery_low);
                break;
            case OFFLINE_AND_BATTERY_LOW:
                text = getString(R.string.banner_offline) + "\n" + getString(R.string.banner_battery_low);
                break;
            default:
                text = null;
        }
        binding.deviceBanner.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) binding.deviceBanner.setText(text);
    }

    // ------------------------------------------------------------------ Stage 7: exchange rates (REST + JSON)

    private void showExchangeRates() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rates_title)
                .setMessage(R.string.rates_loading)
                .setPositiveButton(R.string.ok, null)
                .show();
        final long balancePaisa = totalIncome - totalExpense;
        ExchangeRateClient.load(this, result -> {
            if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
            dialog.setMessage(ratesText(result, balancePaisa));
        });
    }

    private String ratesText(ExchangeRateClient.Result r, long balancePaisa) {
        if (r.rates == null) return getString(R.string.rates_failed, r.error);
        StringBuilder sb = new StringBuilder();
        for (String code : ExchangeRates.SHOWN) {
            if (!r.rates.has(code)) continue;
            sb.append(getString(R.string.rates_line, code, ExchangeRates.plain(r.rates.homePer(code)))).append('\n');
        }
        double balance = balancePaisa / 100.0;
        if (r.rates.has("USD")) {
            sb.append('\n').append(getString(R.string.rates_balance, MoneyUtils.format(balancePaisa),
                    ExchangeRates.plain(r.rates.convertFromHome(balance, "USD"))));
        }
        java.text.DateFormat f = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT);
        if (r.rates.updatedMillis > 0) {
            sb.append("\n\n").append(getString(R.string.rates_updated, f.format(new java.util.Date(r.rates.updatedMillis))));
        }
        if (r.fromCache) {
            sb.append("\n").append(getString(R.string.rates_cached, r.fetchedMillis > 0
                    ? f.format(new java.util.Date(r.fetchedMillis)) : "?", r.error));
        }
        return sb.toString().trim();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_cloud) {
            startActivity(new Intent(this, CloudAccountActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_rates) {
            showExchangeRates();
            return true;
        }
        if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindBudgetMonth(); // re-binds if the calendar month changed while the app was in the background
        BackgroundWork.runNow(this); // create due recurring transactions / reminders after a long absence
        renderPlanning();          // "today" may have changed: overdue bills, days left, forecast window
    }

    // ------------------------------------------------------------------ Stage 4 dashboard

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
            goalsLine1.setText(lastGoals != null && !lastGoals.isEmpty()
                    ? R.string.dash_goals_all_archived : R.string.dash_goals_none);
            goalsLine2.setVisibility(View.GONE);
            return;
        }
        int color = ThemeColors.get(this, g.completedCount == g.activeCount ? R.attr.etIncome : R.attr.etAccent);
        goalsBadge.setText(g.percentText + "%");
        goalsBadge.setTextColor(color);
        goalsProgress.setVisibility(View.VISIBLE);
        goalsProgress.setIndicatorColor(color);
        goalsProgress.setProgressCompat(g.progress, false);
        goalsLine1.setText(getString(R.string.dash_goals_line1, MoneyUtils.format(g.totalSavedPaisa),
                MoneyUtils.format(g.totalTargetPaisa), g.activeCount, g.completedCount));
        goalsLine2.setVisibility(View.VISIBLE);
        if (g.nextDeadlineGoal != null) {
            String when = g.nextDeadlineDays < 0
                    ? getString(R.string.dash_overdue_by, PlanText.days(this, -g.nextDeadlineDays))
                    : g.nextDeadlineDays == 0 ? getString(R.string.dash_today)
                    : getString(R.string.dash_in, PlanText.days(this, g.nextDeadlineDays));
            goalsLine2.setText(getString(R.string.dash_goals_next, g.nextDeadlineGoal.name,
                    PlanDates.format(g.nextDeadlineGoal.deadlineMillis), when));
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
        recurringBadge.setText(r.pausedCount > 0
                ? getString(R.string.dash_recurring_badge_paused, r.activeCount, r.pausedCount)
                : getString(R.string.dash_recurring_badge, r.activeCount));
        recurringBadge.setTextColor(ThemeColors.get(this, R.attr.etAccent));
        recurringLine1.setText(r.nextRule == null ? getString(R.string.dash_recurring_no_next)
                : getString(R.string.dash_recurring_next, r.nextRule.category,
                (r.nextRule.isIncome() ? "+ " : "- ") + MoneyUtils.format(r.nextRule.amountPaisa),
                PlanDates.format(r.nextMillis)));
        recurringLine2.setVisibility(View.VISIBLE);
        recurringLine2.setText(getString(R.string.dash_recurring_forecast, DashboardCalculator.FORECAST_DAYS,
                MoneyUtils.format(r.upcomingIncomePaisa), MoneyUtils.format(r.upcomingExpensePaisa)));
    }

    private void renderBills(DashboardCalculator.BillsSummary b, long today) {
        if (!b.hasBills()) {
            billsBadge.setText("");
            billsLine1.setText(lastBills != null && !lastBills.isEmpty()
                    ? R.string.dash_bills_all_paid : R.string.dash_bills_none);
            billsLine2.setVisibility(View.GONE);
            return;
        }
        if (b.overdueCount > 0) {
            billsBadge.setText(getString(R.string.dash_bills_badge_overdue, b.overdueCount));
            billsBadge.setTextColor(ThemeColors.get(this, R.attr.etExpense));
            billsLine1.setText(getString(R.string.dash_bills_overdue, billCount(b.overdueCount),
                    MoneyUtils.format(b.overdueTotalPaisa)));
        } else {
            billsBadge.setText(b.dueSoonCount > 0 ? getString(R.string.dash_bills_badge_soon, b.dueSoonCount) : "");
            billsBadge.setTextColor(ThemeColors.get(this, R.attr.etWarning));
            billsLine1.setText(R.string.dash_bills_no_overdue);
        }
        StringBuilder l2 = new StringBuilder(getString(R.string.dash_bills_soon, DashboardCalculator.DUE_SOON_DAYS,
                billCount(b.dueSoonCount), MoneyUtils.format(b.dueSoonTotalPaisa)));
        if (b.nextBill != null) {
            String when = b.nextBillDays == 0 ? getString(R.string.dash_today)
                    : getString(R.string.dash_in, PlanText.days(this, b.nextBillDays));
            l2.append('\n').append(getString(R.string.dash_bills_next, b.nextBill.name,
                    MoneyUtils.format(b.nextBill.amountPaisa), when));
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
            budgetLine1.setText(getString(R.string.budget_line_spent,
                    MoneyUtils.format(s.spentPaisa), MoneyUtils.format(s.budgetPaisa)));
            budgetLine2.setText(s.isOverspent()
                    ? getString(R.string.budget_line_over, MoneyUtils.format(-s.remainingPaisa), s.percentText)
                    : getString(R.string.budget_line_left, MoneyUtils.format(s.remainingPaisa), s.percentText));
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
        long balance = totalIncome - totalExpense; // exact: whole paisa
        balanceView.setText(MoneyUtils.format(balance));
        balanceView.setTextColor(ThemeColors.get(this,
                balance < 0 ? R.attr.etHeroExpense : R.attr.etOnHero));
        incomeView.setText(MoneyUtils.format(totalIncome));
        expenseView.setText(MoneyUtils.format(totalExpense));
    }

    private void openEditor(Transaction t) {
        Intent i = new Intent(this, AddTransactionActivity.class);
        i.putExtra(AddTransactionActivity.EXTRA_TRANSACTION_ID, t.id);
        startActivity(i);
    }

    /** Long-press menu: Edit or Delete (Stage 1 long-press delete is still one tap away). */
    private void showOptions(Transaction t) {
        new AlertDialog.Builder(this)
                .setTitle(t.category)
                .setItems(new CharSequence[]{getString(R.string.edit), getString(R.string.delete)},
                        (d, which) -> {
                            if (which == 0) openEditor(t);
                            else confirmDelete(t);
                        })
                .show();
    }

    private void confirmDelete(Transaction t) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, t.category, MoneyUtils.format(t.amountPaisa)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AppDatabase.WRITE_EXECUTOR.execute(() -> dao.delete(t));
                    // Totals and budgets refresh by themselves through LiveData; offer an undo.
                    Snackbar.make(binding.mainRoot, R.string.deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo, v ->
                                    AppDatabase.WRITE_EXECUTOR.execute(() -> dao.insert(t)))
                            .show();
                })
                .show();
    }
}

package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Stage 3: category pie/donut chart, monthly income-vs-expense bars, summaries, month-to-month
 * comparison, spending insights and budget-vs-actual, for a chosen month or a custom date range.
 *
 * <p>Every number comes from {@link ReportCalculator} over live Room data. Changing the period
 * detaches the old LiveData and attaches a new one, so the screen can never show stale figures.
 */
public class ReportsActivity extends BaseActivity {
    private static final String STATE_MODE = "mode";
    private static final String STATE_MONTH = "month";
    private static final String STATE_FIRST = "first_day";
    private static final String STATE_LAST = "last_day";
    private static final int DEFAULT_RANGE_DAYS = 30;

    // ---- selected period
    private ReportPeriod.Type mode = ReportPeriod.Type.MONTH;
    private String monthKey;
    private long customFirstDay;
    private long customLastDay;
    private ReportPeriod period;
    private LiveData<ReportData> source;
    private ReportData shown;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    // ---- views
    private MaterialButtonToggleGroup modeToggle;
    private View monthRow, customRow;
    private TextView monthLabel, statusMessage;
    private Button startDateButton, endDateButton;
    private LinearProgressIndicator loadingBar;

    private View summaryCard, compareCard, categoryCard, barCard, trendCard, largestCard;
    private TextView periodTitle, sumIncome, sumExpense, sumNet, sumSavingsRate, sumMeta;
    private TextView compareTitle, barNote, trendTitle, budgetNote;
    private View topCategoriesHeading, insightsHeading, largestExpensesHeading, largestIncomeHeading;
    private ViewGroup compareContainer, topCategoriesContainer, monthlyContainer, insightsContainer,
            largestExpensesContainer, largestIncomeContainer, budgetContainer;
    private PieChart pieChart;
    private BarChart barChart;
    private LineChart lineChart;

    private int[] palette;
    private ExportController export;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        export = new ExportController(this, savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        palette = new int[]{
                color(R.color.chart_1), color(R.color.chart_2), color(R.color.chart_3),
                color(R.color.chart_4), color(R.color.chart_5), color(R.color.chart_6),
                color(R.color.chart_7), color(R.color.chart_8), color(R.color.chart_9),
                color(R.color.chart_10)};

        findViews();
        configureCharts();

        long today = MonthUtils.startOfDay(System.currentTimeMillis());
        monthKey = MonthUtils.current();
        customLastDay = today;
        customFirstDay = MonthUtils.addDays(today, -(DEFAULT_RANGE_DAYS - 1));
        if (savedInstanceState != null) {
            String m = savedInstanceState.getString(STATE_MONTH);
            if (MonthUtils.isValidKey(m)) monthKey = m;
            long first = savedInstanceState.getLong(STATE_FIRST, -1);
            long last = savedInstanceState.getLong(STATE_LAST, -1);
            if (first != -1 && last != -1 && first <= last) {
                customFirstDay = first;
                customLastDay = last;
            }
            if ("CUSTOM".equals(savedInstanceState.getString(STATE_MODE))) {
                mode = ReportPeriod.Type.CUSTOM;
            }
        }

        modeToggle.check(mode == ReportPeriod.Type.MONTH ? R.id.modeMonth : R.id.modeCustom);
        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            ReportPeriod.Type selected = checkedId == R.id.modeCustom
                    ? ReportPeriod.Type.CUSTOM : ReportPeriod.Type.MONTH;
            if (selected != mode) {
                mode = selected;
                bind();
            }
        });

        findViewById(R.id.prevMonth).setOnClickListener(v -> changeMonth(-1));
        findViewById(R.id.nextMonth).setOnClickListener(v -> changeMonth(1));
        startDateButton.setOnClickListener(v -> pickDate(true));
        endDateButton.setOnClickListener(v -> pickDate(false));
        findViewById(R.id.preset7).setOnClickListener(v -> applyLastDays(7));
        findViewById(R.id.preset30).setOnClickListener(v -> applyLastDays(30));
        findViewById(R.id.preset90).setOnClickListener(v -> applyLastDays(90));
        findViewById(R.id.presetYtd).setOnClickListener(v -> applyYearToDate());

        bind();
    }

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    /** Colour of a theme attribute (income / expense / text ...) for the active theme. */
    private int attr(int attrRes) {
        return ThemeColors.get(this, attrRes);
    }

    private void findViews() {
        modeToggle = findViewById(R.id.modeToggle);
        monthRow = findViewById(R.id.monthRow);
        customRow = findViewById(R.id.customRow);
        monthLabel = findViewById(R.id.monthLabel);
        statusMessage = findViewById(R.id.statusMessage);
        startDateButton = findViewById(R.id.startDateButton);
        endDateButton = findViewById(R.id.endDateButton);
        loadingBar = findViewById(R.id.loadingBar);

        summaryCard = findViewById(R.id.summaryCard);
        compareCard = findViewById(R.id.compareCard);
        categoryCard = findViewById(R.id.categoryCard);
        barCard = findViewById(R.id.barCard);
        trendCard = findViewById(R.id.trendCard);
        largestCard = findViewById(R.id.largestCard);

        periodTitle = findViewById(R.id.periodTitle);
        sumIncome = findViewById(R.id.sumIncome);
        sumExpense = findViewById(R.id.sumExpense);
        sumNet = findViewById(R.id.sumNet);
        sumSavingsRate = findViewById(R.id.sumSavingsRate);
        sumMeta = findViewById(R.id.sumMeta);
        compareTitle = findViewById(R.id.compareTitle);
        compareContainer = findViewById(R.id.compareContainer);
        topCategoriesHeading = findViewById(R.id.topCategoriesHeading);
        topCategoriesContainer = findViewById(R.id.topCategoriesContainer);
        barNote = findViewById(R.id.barNote);
        monthlyContainer = findViewById(R.id.monthlyContainer);
        trendTitle = findViewById(R.id.trendTitle);
        insightsHeading = findViewById(R.id.insightsHeading);
        insightsContainer = findViewById(R.id.insightsContainer);
        largestExpensesHeading = findViewById(R.id.largestExpensesHeading);
        largestExpensesContainer = findViewById(R.id.largestExpensesContainer);
        largestIncomeHeading = findViewById(R.id.largestIncomeHeading);
        largestIncomeContainer = findViewById(R.id.largestIncomeContainer);
        budgetNote = findViewById(R.id.budgetNote);
        budgetContainer = findViewById(R.id.budgetContainer);

        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);
        lineChart = findViewById(R.id.lineChart);
    }

    // ------------------------------------------------------------------ period selection

    private void changeMonth(int delta) {
        String target = MonthUtils.shift(monthKey, delta);
        if (!MonthUtils.isValidKey(target)) return;
        int away = MonthUtils.monthsBetween(MonthUtils.current(), target);
        if (Math.abs(away) > ReportPeriod.MAX_MONTHS_AWAY) return;
        monthKey = target;
        bind();
    }

    private void applyLastDays(int days) {
        long today = MonthUtils.startOfDay(System.currentTimeMillis());
        customLastDay = today;
        customFirstDay = MonthUtils.addDays(today, -(days - 1));
        bind();
    }

    private void applyYearToDate() {
        Calendar c = Calendar.getInstance();
        c.set(c.get(Calendar.YEAR), Calendar.JANUARY, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        customFirstDay = c.getTimeInMillis();
        customLastDay = MonthUtils.startOfDay(System.currentTimeMillis());
        bind();
    }

    private void pickDate(final boolean isStart) {
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(isStart ? customFirstDay : customLastDay);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(year, month, dayOfMonth);
            long chosen = picked.getTimeInMillis();
            // Keep the range valid: moving one end past the other drags the other end along.
            if (isStart) {
                customFirstDay = chosen;
                if (customLastDay < chosen) customLastDay = chosen;
            } else {
                customLastDay = chosen;
                if (customFirstDay > chosen) customFirstDay = chosen;
            }
            bind();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dialog.setTitle(isStart ? R.string.rep_pick_from : R.string.rep_pick_to);
        Calendar min = Calendar.getInstance();
        min.clear();
        min.set(1970, Calendar.JANUARY, 1);
        Calendar max = Calendar.getInstance();
        max.clear();
        max.set(2100, Calendar.DECEMBER, 31);
        dialog.getDatePicker().setMinDate(min.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(max.getTimeInMillis());
        dialog.show();
    }

    /** (Re)creates the period from the current selection and subscribes to its report. */
    private void bind() {
        ReportPeriod p;
        try {
            p = mode == ReportPeriod.Type.MONTH
                    ? ReportPeriod.month(monthKey)
                    : ReportPeriod.custom(customFirstDay, customLastDay);
        } catch (IllegalArgumentException e) {
            showStatus(getString(R.string.rep_load_error));
            return;
        }
        period = p;
        updateControls();
        if (source != null) source.removeObservers(this); // the old period must never draw again
        loadingBar.setVisibility(View.VISIBLE);
        source = ReportsRepository.observe(getApplicationContext(), p);
        source.observe(this, this::render);
    }

    private void updateControls() {
        boolean month = mode == ReportPeriod.Type.MONTH;
        monthRow.setVisibility(month ? View.VISIBLE : View.GONE);
        customRow.setVisibility(month ? View.GONE : View.VISIBLE);
        monthLabel.setText(MonthUtils.label(monthKey));
        startDateButton.setText(getString(R.string.rep_from, dateFormat.format(new Date(customFirstDay))));
        endDateButton.setText(getString(R.string.rep_to, dateFormat.format(new Date(customLastDay))));
    }

    // ------------------------------------------------------------------ rendering

    private void showStatus(String message) {
        statusMessage.setText(message);
        statusMessage.setVisibility(View.VISIBLE);
        for (View v : new View[]{summaryCard, compareCard, categoryCard, barCard, trendCard, largestCard,
                findViewById(R.id.budgetSectionCard)}) {
            v.setVisibility(View.GONE);
        }
    }

    private void render(ReportData d) {
        loadingBar.setVisibility(View.INVISIBLE);
        if (d == null) {
            shown = null;
            showStatus(getString(R.string.rep_load_error));
            return;
        }
        if (!d.period.equals(period)) return; // belongs to a period that is no longer selected
        shown = d;

        boolean anyBarData = false;
        for (ReportData.MonthBar b : d.bars) {
            if (b.incomePaisa != 0 || b.expensePaisa != 0) anyBarData = true;
        }
        boolean hasExpenses = d.current.expensePaisa > 0;

        summaryCard.setVisibility(View.VISIBLE);
        compareCard.setVisibility(View.VISIBLE);
        findViewById(R.id.budgetSectionCard).setVisibility(View.VISIBLE);
        if (!d.hasData() && !anyBarData) {
            statusMessage.setText(R.string.rep_no_data);
            statusMessage.setVisibility(View.VISIBLE);
        } else {
            statusMessage.setVisibility(View.GONE);
        }

        bindSummary(d);
        bindComparison(d);

        categoryCard.setVisibility(hasExpenses ? View.VISIBLE : View.GONE);
        if (hasExpenses) bindCategories(d);

        barCard.setVisibility(anyBarData ? View.VISIBLE : View.GONE);
        if (anyBarData) bindBars(d);

        boolean hasTrend = hasExpenses || !d.insights.isEmpty();
        trendCard.setVisibility(hasTrend ? View.VISIBLE : View.GONE);
        if (hasTrend) bindTrend(d);

        boolean hasLargest = !d.largestExpenses.isEmpty() || !d.largestIncome.isEmpty();
        largestCard.setVisibility(hasLargest ? View.VISIBLE : View.GONE);
        if (hasLargest) bindLargest(d);

        bindBudgets(d);
    }

    private String periodLabel(ReportPeriod p) {
        if (p.type == ReportPeriod.Type.MONTH) return MonthUtils.label(p.monthKey);
        return getString(R.string.rep_range_label,
                dateFormat.format(new Date(p.startMillis)), dateFormat.format(new Date(p.endMillis - 1)));
    }

    private int goodBad(long value, boolean higherIsGood) {
        if (value == 0) return attr(R.attr.etTextTertiary);
        return attr((value > 0) == higherIsGood ? R.attr.etIncome : R.attr.etExpense);
    }

    // ---- summary + comparison

    private void bindSummary(ReportData d) {
        ReportData.Summary s = d.current;
        periodTitle.setText(periodLabel(d.period));
        sumIncome.setText(MoneyUtils.format(s.incomePaisa));
        sumIncome.setTextColor(attr(R.attr.etIncome));
        sumExpense.setText(MoneyUtils.format(s.expensePaisa));
        sumExpense.setTextColor(attr(R.attr.etExpense));
        sumNet.setText(MoneyUtils.format(s.netPaisa));
        sumNet.setTextColor(attr(s.netPaisa < 0 ? R.attr.etExpense : R.attr.etIncome));
        sumSavingsRate.setText(ReportFormat.percent(s.savingsRateTenths));
        sumSavingsRate.setTextColor(s.savingsRateTenths == null
                ? attr(R.attr.etTextTertiary)
                : attr(s.savingsRateTenths < 0 ? R.attr.etExpense : R.attr.etIncome));
        sumMeta.setText(getString(R.string.rep_summary_meta, s.transactionCount(), d.period.dayCount()));
    }

    private void bindComparison(ReportData d) {
        compareTitle.setText(getString(R.string.rep_compare_title, d.previousLabel));
        compareContainer.removeAllViews();
        ReportData.Summary c = d.current;
        ReportData.Summary p = d.previous;

        addRow(compareContainer, getString(R.string.rep_income),
                getString(R.string.rep_vs, MoneyUtils.format(c.incomePaisa), MoneyUtils.format(p.incomePaisa)),
                ReportFormat.signedPercent(ReportCalculator.changeTenths(c.incomePaisa, p.incomePaisa)),
                goodBad(c.incomePaisa - p.incomePaisa, true), null, 0);
        addRow(compareContainer, getString(R.string.rep_expenses),
                getString(R.string.rep_vs, MoneyUtils.format(c.expensePaisa), MoneyUtils.format(p.expensePaisa)),
                ReportFormat.signedPercent(ReportCalculator.changeTenths(c.expensePaisa, p.expensePaisa)),
                goodBad(c.expensePaisa - p.expensePaisa, false), null, 0);
        addRow(compareContainer, getString(R.string.rep_net),
                getString(R.string.rep_vs, MoneyUtils.format(c.netPaisa), MoneyUtils.format(p.netPaisa)),
                ReportFormat.signedMoney(c.netPaisa - p.netPaisa),
                goodBad(c.netPaisa - p.netPaisa, true), null, 0);
        long rateDelta = c.savingsRateTenths == null || p.savingsRateTenths == null
                ? 0 : c.savingsRateTenths - p.savingsRateTenths;
        addRow(compareContainer, getString(R.string.rep_savings_rate),
                getString(R.string.rep_vs, ReportFormat.percent(c.savingsRateTenths),
                        ReportFormat.percent(p.savingsRateTenths)),
                ReportFormat.pointsChange(c.savingsRateTenths, p.savingsRateTenths),
                goodBad(rateDelta, true), null, 0);
    }

    // ---- category donut + top categories

    private void bindCategories(ReportData d) {
        List<PieEntry> entries = new ArrayList<>();
        for (ReportData.CategorySlice s : d.categories) {
            entries.add(new PieEntry(s.amountPaisa / 100f, s.category, s));
        }
        PieDataSet set = new PieDataSet(entries, "");
        int[] colors = new int[entries.size()];
        for (int i = 0; i < colors.length; i++) colors[i] = palette[i % palette.length];
        set.setColors(colors);
        set.setSliceSpace(2f);
        set.setValueTextSize(11f);
        set.setValueTextColor(Color.WHITE);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getPieLabel(float value, PieEntry entry) {
                Object data = entry.getData();
                if (!(data instanceof ReportData.CategorySlice)) return "";
                long share = ((ReportData.CategorySlice) data).shareTenths;
                return share >= 50 ? ReportFormat.percent(share) : ""; // skip labels on tiny slices
            }
        });
        pieChart.setData(new PieData(set));
        pieChart.highlightValues(null);
        pieChart.setCenterText(getString(R.string.rep_center_total, MoneyUtils.format(d.current.expensePaisa)));
        pieChart.invalidate();

        topCategoriesContainer.removeAllViews();
        int limit = Math.min(5, d.categories.size());
        topCategoriesHeading.setVisibility(limit > 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < limit; i++) {
            ReportData.CategorySlice s = d.categories.get(i);
            Long change = s.changeTenths;
            addRow(topCategoriesContainer, s.category,
                    getString(R.string.rep_category_sub, ReportFormat.percent(s.shareTenths), s.count),
                    MoneyUtils.format(s.amountPaisa),
                    attr(R.attr.etExpense),
                    change == null ? null : getString(R.string.rep_vs_previous, ReportFormat.signedPercent(change)),
                    change == null ? 0 : goodBad(change, false));
        }
    }

    // ---- income vs expense bars + monthly summary

    private void bindBars(ReportData d) {
        int n = d.bars.size();
        List<BarEntry> income = new ArrayList<>();
        List<BarEntry> expense = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ReportData.MonthBar b = d.bars.get(i);
            income.add(new BarEntry(i, b.incomePaisa / 100f));
            expense.add(new BarEntry(i, b.expensePaisa / 100f));
            labels.add(MonthUtils.shortLabel(b.monthKey));
        }
        BarDataSet incomeSet = new BarDataSet(income, getString(R.string.rep_income));
        incomeSet.setColor(attr(R.attr.etIncome));
        incomeSet.setDrawValues(false);
        BarDataSet expenseSet = new BarDataSet(expense, getString(R.string.rep_expenses));
        expenseSet.setColor(attr(R.attr.etExpense));
        expenseSet.setDrawValues(false);

        // Group maths: (barWidth + barSpace) * 2 bars + groupSpace = 1.0, so month i spans [i, i+1).
        final float groupSpace = 0.125f, barSpace = 0.0625f, barWidth = 0.375f;
        BarData data = new BarData(incomeSet, expenseSet);
        data.setBarWidth(barWidth);
        barChart.setData(data);
        XAxis x = barChart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setAxisMinimum(0f);
        x.setAxisMaximum(data.getGroupWidth(groupSpace, barSpace) * n);
        x.setLabelCount(n);
        x.setLabelRotationAngle(n > 6 ? -45f : 0f);
        barChart.groupBars(0f, groupSpace, barSpace);
        barChart.invalidate();

        barNote.setVisibility(d.barsTruncated ? View.VISIBLE : View.GONE);
        if (d.barsTruncated) {
            barNote.setText(getString(R.string.rep_bar_truncated, n, d.totalBarMonths));
        }

        monthlyContainer.removeAllViews();
        for (int i = n - 1; i >= 0; i--) { // newest first
            ReportData.MonthBar b = d.bars.get(i);
            addRow(monthlyContainer, MonthUtils.label(b.monthKey),
                    getString(R.string.rep_month_sub, MoneyUtils.format(b.incomePaisa),
                            MoneyUtils.format(b.expensePaisa), ReportFormat.percent(b.savingsRateTenths)),
                    ReportFormat.signedMoney(b.netPaisa),
                    attr(b.netPaisa < 0 ? R.attr.etExpense : R.attr.etIncome), null, 0);
        }
    }

    // ---- spending trend + insights

    private void bindTrend(ReportData d) {
        int titleRes = d.trendGranularity == ReportData.Granularity.DAY ? R.string.rep_trend_day
                : d.trendGranularity == ReportData.Granularity.WEEK ? R.string.rep_trend_week
                : R.string.rep_trend_month;
        trendTitle.setText(titleRes);

        int n = d.trend.size();
        lineChart.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        if (n > 0) {
            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ReportData.TrendPoint t = d.trend.get(i);
                entries.add(new Entry(i, t.expensePaisa / 100f));
                labels.add(t.label);
            }
            LineDataSet set = new LineDataSet(entries, getString(R.string.rep_expenses));
            set.setColor(attr(R.attr.etExpense));
            set.setLineWidth(2f);
            set.setDrawCircles(n <= 31);
            set.setCircleColor(attr(R.attr.etExpense));
            set.setCircleRadius(2.5f);
            set.setDrawValues(false);
            set.setDrawFilled(true);
            set.setFillColor(attr(R.attr.etExpense));
            set.setFillAlpha(40);
            lineChart.setData(new LineData(set));
            XAxis x = lineChart.getXAxis();
            x.setValueFormatter(new IndexAxisValueFormatter(labels));
            x.setGranularityEnabled(true);
            x.setGranularity(1f);
            x.setLabelCount(Math.min(6, n), false);
            lineChart.invalidate();
        }

        insightsContainer.removeAllViews();
        insightsHeading.setVisibility(d.insights.isEmpty() ? View.GONE : View.VISIBLE);
        for (String s : d.insights) {
            TextView tv = new TextView(this);
            tv.setText("\u2022 " + s);
            tv.setTextColor(attr(R.attr.etChartText));
            tv.setTextSize(13f);
            tv.setPadding(0, dp(4), 0, dp(4));
            insightsContainer.addView(tv);
        }
    }

    // ---- largest transactions

    private void bindLargest(ReportData d) {
        fillTransactions(largestExpensesContainer, largestExpensesHeading, d.largestExpenses, R.attr.etExpense);
        fillTransactions(largestIncomeContainer, largestIncomeHeading, d.largestIncome, R.attr.etIncome);
    }

    private void fillTransactions(ViewGroup container, View heading, List<Transaction> list, int colorAttr) {
        container.removeAllViews();
        heading.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        for (Transaction t : list) {
            String note = t.note == null || t.note.trim().isEmpty() ? "" : " \u2022 " + t.note.trim();
            addRow(container, ReportCalculator.normalize(t.category),
                    dateFormat.format(new Date(t.dateMillis)) + note,
                    MoneyUtils.format(t.amountPaisa), attr(colorAttr), null, 0);
        }
    }

    // ---- budgets

    private void bindBudgets(ReportData d) {
        budgetContainer.removeAllViews();
        ReportData.BudgetReport b = d.budget;
        if (!b.periodSupported) {
            budgetNote.setText(R.string.rep_budget_unsupported);
            budgetNote.setVisibility(View.VISIBLE);
            return;
        }
        if (!b.hasBudgets()) {
            budgetNote.setText(R.string.rep_budget_none);
            budgetNote.setVisibility(View.VISIBLE);
            return;
        }
        if (b.monthsWithBudgets > 1) {
            budgetNote.setText(getString(R.string.rep_budget_multi_note, b.monthsWithBudgets));
            budgetNote.setVisibility(View.VISIBLE);
        } else {
            budgetNote.setVisibility(View.GONE);
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ReportData.BudgetRow row : b.rows) {
            View v = inflater.inflate(R.layout.item_report_budget, budgetContainer, false);
            BudgetStatus s = row.status;
            ((TextView) v.findViewById(R.id.budgetCategory)).setText(row.label);
            BudgetUi.bindProgress((LinearProgressIndicator) v.findViewById(R.id.budgetProgress),
                    (TextView) v.findViewById(R.id.budgetPercent), s);
            ((TextView) v.findViewById(R.id.budgetLine1)).setText(getString(R.string.budget_line_spent,
                    MoneyUtils.format(s.spentPaisa), MoneyUtils.format(s.budgetPaisa)));
            ((TextView) v.findViewById(R.id.budgetLine2)).setText(s.isOverspent()
                    ? getString(R.string.budget_line_over, MoneyUtils.format(-s.remainingPaisa), s.percentText)
                    : getString(R.string.budget_line_left, MoneyUtils.format(s.remainingPaisa), s.percentText));
            budgetContainer.addView(v);
        }
    }

    // ------------------------------------------------------------------ small helpers

    /** Adds a two-line row (title/subtitle on the left, value/sub-value on the right). */
    private void addRow(ViewGroup parent, String title, String subtitle, String value, int valueColor,
                        String valueSub, int valueSubColor) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_report_row, parent, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.rowSubtitle)).setText(subtitle);
        TextView v = (TextView) row.findViewById(R.id.rowValue);
        v.setText(value);
        v.setTextColor(valueColor);
        TextView sub = (TextView) row.findViewById(R.id.rowValueSub);
        if (valueSub == null) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setText(valueSub);
            sub.setTextColor(valueSubColor);
            sub.setVisibility(View.VISIBLE);
        }
        parent.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ one-time chart setup

    private void configureCharts() {
        int text = attr(R.attr.etChartText);
        int grid = attr(R.attr.etChartGrid);
        String noData = getString(R.string.rep_chart_no_data);

        // Donut
        pieChart.getDescription().setEnabled(false);
        pieChart.setNoDataText(noData);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(55f);
        pieChart.setHoleColor(attr(R.attr.etSurface)); // donut hole follows the theme (dark in Dark theme)
        pieChart.setTransparentCircleColor(attr(R.attr.etSurface));
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setDrawEntryLabels(false);
        pieChart.setRotationEnabled(false);
        pieChart.setCenterTextSize(13f);
        pieChart.setCenterTextColor(text);
        pieChart.setExtraOffsets(0f, 4f, 0f, 4f);
        Legend pl = pieChart.getLegend();
        pl.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        pl.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        pl.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        pl.setDrawInside(false);
        pl.setWordWrapEnabled(true);
        pl.setTextColor(text);
        pl.setTextSize(11f);
        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (!(e instanceof PieEntry) || !(((PieEntry) e).getData() instanceof ReportData.CategorySlice)) return;
                ReportData.CategorySlice s = (ReportData.CategorySlice) ((PieEntry) e).getData();
                pieChart.setCenterText(getString(R.string.rep_center_selected, s.category,
                        MoneyUtils.format(s.amountPaisa), ReportFormat.percent(s.shareTenths)));
            }

            @Override
            public void onNothingSelected() {
                if (shown != null) {
                    pieChart.setCenterText(getString(R.string.rep_center_total,
                            MoneyUtils.format(shown.current.expensePaisa)));
                }
            }
        });

        // Grouped bars
        barChart.getDescription().setEnabled(false);
        barChart.setNoDataText(noData);
        barChart.setTouchEnabled(false); // exact figures are in the list below; lets the page scroll freely
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setGridColor(grid);
        barChart.getAxisLeft().setTextColor(text);
        barChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ReportFormat.compactAmount(value);
            }
        });
        XAxis bx = barChart.getXAxis();
        bx.setPosition(XAxis.XAxisPosition.BOTTOM);
        bx.setDrawGridLines(false);
        bx.setGranularity(1f);
        bx.setCenterAxisLabels(true);
        bx.setTextColor(text);
        Legend bl = barChart.getLegend();
        bl.setTextColor(text);
        bl.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        bl.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        barChart.setExtraBottomOffset(6f);

        // Spending trend line
        lineChart.getDescription().setEnabled(false);
        lineChart.setNoDataText(noData);
        lineChart.setTouchEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setGridColor(grid);
        lineChart.getAxisLeft().setTextColor(text);
        lineChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ReportFormat.compactAmount(value);
            }
        });
        XAxis lx = lineChart.getXAxis();
        lx.setPosition(XAxis.XAxisPosition.BOTTOM);
        lx.setDrawGridLines(false);
        lx.setTextColor(text);
        lineChart.setExtraBottomOffset(6f);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        export.saveState(outState);
        outState.putString(STATE_MODE, mode == ReportPeriod.Type.CUSTOM ? "CUSTOM" : "MONTH");
        outState.putString(STATE_MONTH, monthKey);
        outState.putLong(STATE_FIRST, customFirstDay);
        outState.putLong(STATE_LAST, customLastDay);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.reports_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_export_pdf) {
            // Export exactly the period that is on screen.
            try {
                export.startPdf(period);
            } catch (RuntimeException e) {
                android.widget.Toast.makeText(this, R.string.rep_load_error, android.widget.Toast.LENGTH_LONG).show();
            }
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

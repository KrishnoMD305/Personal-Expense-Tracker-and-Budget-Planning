package com.example.expensetracker;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recurring income / expense rules (daily, weekly, monthly): list, add, edit, pause, delete. */
public class RecurringActivity extends BaseActivity {
    private PlanRowAdapter adapter;
    private TextView emptyView;
    private List<RecurringRule> lastRules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_list);
        setTitle(R.string.recurring);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        emptyView = findViewById(R.id.planEmpty);
        emptyView.setText(R.string.rec_empty);
        ((TextView) findViewById(R.id.planIntro)).setText(R.string.rec_intro);
        RecyclerView list = findViewById(R.id.planList);
        adapter = new PlanRowAdapter(row -> showOptions((RecurringRule) row.tag));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.planFab);
        fab.setContentDescription(getString(R.string.rec_new));
        fab.setOnClickListener(v -> showEditor(null));

        RecurringRepository.observeAll(this).observe(this, rules -> {
            lastRules = rules;
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        List<RecurringRule> rules = lastRules;
        List<PlanRow> rows = new ArrayList<>();
        if (rules != null) {
            long today = PlanDates.today();
            long tomorrow = MonthUtils.addDays(today, 1);
            boolean headerActive = false, headerPaused = false;
            for (RecurringRule r : rules) {
                if (r.active && !headerActive) {
                    rows.add(PlanRow.header("h:active", getString(R.string.rec_active)));
                    headerActive = true;
                }
                if (!r.active && !headerPaused) {
                    rows.add(PlanRow.header("h:paused", getString(R.string.rec_paused)));
                    headerPaused = true;
                }
                rows.add(row(r, tomorrow));
            }
        }
        adapter.submitList(rows);
        emptyView.setVisibility(rules != null && rules.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private PlanRow row(RecurringRule r, long tomorrow) {
        int color = ThemeColors.get(this, r.isIncome() ? R.attr.etIncome : R.attr.etExpense);
        String amount = (r.isIncome() ? "+ " : "- ") + MoneyUtils.format(r.amountPaisa);
        String title = r.note.isEmpty() ? r.category : r.category + " • " + r.note;
        StringBuilder l1 = new StringBuilder(PlanText.frequency(this, r.frequency))
                .append(" • ").append(getString(R.string.rec_from, PlanDates.format(r.startDateMillis)));
        if (r.endDateMillis > 0) {
            l1.append(" • ").append(getString(R.string.rec_until, PlanDates.format(r.endDateMillis)));
        }
        String l2;
        if (!r.active) {
            l2 = getString(R.string.rec_paused_note);
        } else {
            long next = RecurrenceCalculator.nextOccurrence(r.startDateMillis, r.endDateMillis, r.frequency, tomorrow);
            l2 = next < 0 ? getString(R.string.rec_finished) : getString(R.string.rec_next, PlanDates.format(next));
        }
        return PlanRow.item("rule:" + r.id, title, amount, color, l1.toString(), l2,
                PlanRow.NO_PROGRESS, 0, !r.active, r);
    }

    private void showOptions(RecurringRule r) {
        CharSequence[] items = {getString(R.string.edit),
                getString(r.active ? R.string.rec_pause : R.string.rec_resume),
                getString(R.string.delete)};
        new AlertDialog.Builder(this)
                .setTitle(r.category)
                .setItems(items, (d, which) -> {
                    if (which == 0) showEditor(r);
                    else if (which == 1) RecurringRepository.setActive(this, r.id, !r.active, err -> {
                        if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                    });
                    else confirmDelete(r);
                })
                .show();
    }

    private void confirmDelete(RecurringRule r) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rec_delete_title)
                .setMessage(R.string.rec_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) ->
                        RecurringRepository.delete(this, r.id, err -> {
                            if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    // ------------------------------------------------------------------ editor dialog

    private void showEditor(RecurringRule existing) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_recurring, null);
        RadioGroup typeGroup = content.findViewById(R.id.recTypeGroup);
        TextInputLayout amountLayout = content.findViewById(R.id.recAmountLayout);
        TextInputEditText amountInput = content.findViewById(R.id.recAmountInput);
        TextInputEditText noteInput = content.findViewById(R.id.recNoteInput);
        Spinner categorySpinner = content.findViewById(R.id.recCategorySpinner);
        Spinner frequencySpinner = content.findViewById(R.id.recFrequencySpinner);
        TextView formError = content.findViewById(R.id.recFormError);
        final long today = PlanDates.today();
        final long originalStart = existing == null ? 0 : existing.startDateMillis;
        DateField start = new DateField(this, content.findViewById(R.id.recStartButton),
                existing == null ? today : existing.startDateMillis, R.string.rec_start, null);
        DateField end = new DateField(this, content.findViewById(R.id.recEndButton),
                existing == null ? 0 : existing.endDateMillis, R.string.rec_no_end,
                content.findViewById(R.id.recEndClear));

        ArrayAdapter<CharSequence> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getTextArray(R.array.frequency_labels));
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencySpinner.setAdapter(freqAdapter);

        boolean startIncome = existing != null && existing.isIncome();
        typeGroup.check(startIncome ? R.id.recRadioIncome : R.id.recRadioExpense);
        fillCategories(categorySpinner, startIncome, existing == null ? null : existing.category);
        typeGroup.setOnCheckedChangeListener((g, id) ->
                fillCategories(categorySpinner, id == R.id.recRadioIncome, null));
        if (existing != null) {
            amountInput.setText(BigDecimal.valueOf(existing.amountPaisa, 2).toPlainString());
            noteInput.setText(existing.note);
            frequencySpinner.setSelection(Math.max(0, Arrays.asList(RecurrenceCalculator.ALL).indexOf(existing.frequency)));
        } else {
            frequencySpinner.setSelection(2); // monthly
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.rec_new : R.string.rec_edit)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(v -> {
            amountLayout.setError(null);
            formError.setVisibility(View.GONE);
            long paisa;
            try {
                paisa = MoneyUtils.parseToPaisa(amountInput.getText() == null ? "" : amountInput.getText().toString());
            } catch (MoneyUtils.InvalidAmountException e) {
                amountLayout.setError(e.getMessage());
                return;
            }
            Object cat = categorySpinner.getSelectedItem();
            if (cat == null) {
                showError(formError, getString(R.string.err_category));
                return;
            }
            String dateErr = PlanValidator.validateRuleDates(start.get(), end.get(), today,
                    existing == null || start.get() != originalStart);
            if (dateErr != null) {
                showError(formError, dateErr);
                return;
            }
            String type = typeGroup.getCheckedRadioButtonId() == R.id.recRadioIncome
                    ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
            String frequency = RecurrenceCalculator.ALL[Math.max(0, frequencySpinner.getSelectedItemPosition())];
            String note = noteInput.getText() == null ? "" : noteInput.getText().toString();
            ok.setEnabled(false);
            RecurringRepository.save(this, existing == null ? 0 : existing.id, type, paisa, cat.toString(), note,
                    frequency, start.get(), end.get(), err -> {
                        if (err == null) {
                            dialog.dismiss();
                            Toast.makeText(this, R.string.rec_saved, Toast.LENGTH_SHORT).show();
                        } else {
                            ok.setEnabled(true);
                            showError(formError, err);
                        }
                    });
        });
        amountInput.requestFocus();
    }

    private void fillCategories(Spinner spinner, boolean income, String select) {
        List<String> items = new ArrayList<>(Arrays.asList(getResources().getStringArray(
                income ? R.array.income_categories : R.array.expense_categories)));
        if (select != null && !items.contains(select)) items.add(select);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
        if (select != null) spinner.setSelection(items.indexOf(select));
    }

    private static void showError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
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

package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Adds a transaction, or edits/deletes an existing one when {@link #EXTRA_TRANSACTION_ID} is set. */
public class AddTransactionActivity extends BaseActivity {
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    private static final String STATE_DATE = "date_millis";
    private static final String STATE_LOADED = "edit_loaded";

    private RadioGroup typeGroup;
    private TextInputLayout amountLayout;
    private TextInputEditText amountInput, noteInput;
    private Spinner categorySpinner;
    private Button dateButton, saveButton;
    private final Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    private long editingId = 0;
    private boolean editLoaded = false;
    private boolean busy = false;
    /** Category to pre-select the next time the spinner is rebuilt (used when editing). */
    private String pendingCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        typeGroup = findViewById(R.id.typeGroup);
        amountLayout = findViewById(R.id.amountLayout);
        amountInput = findViewById(R.id.amountInput);
        noteInput = findViewById(R.id.noteInput);
        categorySpinner = findViewById(R.id.categorySpinner);
        dateButton = findViewById(R.id.dateButton);
        saveButton = findViewById(R.id.saveButton);

        editingId = getIntent().getLongExtra(EXTRA_TRANSACTION_ID, 0);
        if (savedInstanceState != null) {
            long millis = savedInstanceState.getLong(STATE_DATE, -1);
            if (millis >= 0) selectedDate.setTimeInMillis(millis);
            editLoaded = savedInstanceState.getBoolean(STATE_LOADED, false);
        }

        updateCategories();
        typeGroup.setOnCheckedChangeListener((g, id) -> updateCategories());
        updateDateButton();
        dateButton.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate.set(y, m, d);
            updateDateButton();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show());
        saveButton.setOnClickListener(v -> save());

        if (isEditing()) {
            setTitle(R.string.edit_transaction);
            saveButton.setText(R.string.update_transaction);
            if (!editLoaded) {
                saveButton.setEnabled(false);
                loadForEdit();
            }
        }
    }

    private boolean isEditing() {
        return editingId != 0;
    }

    private boolean isIncome() {
        return typeGroup.getCheckedRadioButtonId() == R.id.radioIncome;
    }

    private void updateCategories() {
        int arr = isIncome() ? R.array.income_categories : R.array.expense_categories;
        List<String> items = new ArrayList<>(Arrays.asList(getResources().getStringArray(arr)));
        if (pendingCategory != null && !items.contains(pendingCategory)) items.add(pendingCategory);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(a);
        if (pendingCategory != null) {
            int index = items.indexOf(pendingCategory);
            if (index >= 0) categorySpinner.setSelection(index);
            pendingCategory = null;
        }
    }

    private void updateDateButton() {
        dateButton.setText(dateFormat.format(selectedDate.getTime()));
    }

    // ---------------------------------------------------------------- edit mode

    private void loadForEdit() {
        final long id = editingId;
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            final Transaction t = AppDatabase.getInstance(this).transactionDao().getById(id);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (t == null) {
                    Toast.makeText(this, R.string.tx_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                populate(t);
            });
        });
    }

    private void populate(Transaction t) {
        pendingCategory = t.category;
        typeGroup.check(t.isIncome() ? R.id.radioIncome : R.id.radioExpense);
        if (pendingCategory != null) updateCategories(); // the check listener did not fire (type unchanged)
        amountInput.setText(BigDecimal.valueOf(t.amountPaisa, 2).toPlainString());
        noteInput.setText(t.note == null ? "" : t.note);
        selectedDate.setTimeInMillis(t.dateMillis);
        updateDateButton();
        editLoaded = true;
        saveButton.setEnabled(true);
    }

    // --------------------------------------------------------------------- save

    private void save() {
        if (busy) return;
        amountLayout.setError(null);
        long paisa;
        try {
            paisa = MoneyUtils.parseToPaisa(amountInput.getText() == null ? "" : amountInput.getText().toString());
        } catch (MoneyUtils.InvalidAmountException e) {
            amountLayout.setError(e.getMessage());
            amountInput.requestFocus();
            return;
        }
        Object sel = categorySpinner.getSelectedItem();
        if (sel == null) {
            Toast.makeText(this, R.string.err_category, Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar day = (Calendar) selectedDate.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);

        final String type = isIncome() ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
        final String category = sel.toString();
        final String note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        final long dateMillis = day.getTimeInMillis();
        final long amount = paisa;
        final long id = editingId;

        setBusy(true);
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            TransactionDao dao = db.transactionDao();
            boolean found = true;
            boolean failed = false;
            MonthOverview overview = null;
            try {
                if (id == 0) {
                    Transaction t = new Transaction();
                    t.type = type;
                    t.amountPaisa = amount;
                    t.category = category;
                    t.note = note;
                    t.dateMillis = dateMillis;
                    t.createdAt = System.currentTimeMillis();
                    dao.insert(t);
                } else {
                    Transaction existing = dao.getById(id);
                    if (existing == null) {
                        found = false;
                    } else {
                        existing.type = type; // createdAt is kept
                        existing.amountPaisa = amount;
                        existing.category = category;
                        existing.note = note;
                        existing.dateMillis = dateMillis;
                        dao.update(existing);
                    }
                }
                if (found && Transaction.TYPE_EXPENSE.equals(type)) {
                    // Budget position after this change, used for the 80% / 100% / overspend alert.
                    overview = BudgetRepository.loadMonth(db, MonthUtils.keyOf(dateMillis));
                }
            } catch (RuntimeException e) {
                failed = true;
            }
            final boolean fFound = found, fFailed = failed;
            final MonthOverview fOverview = overview;
            runOnUiThread(() -> onSaved(fFound, fFailed, fOverview, category));
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        saveButton.setEnabled(!b);
    }

    private void onSaved(boolean found, boolean failed, MonthOverview overview, String category) {
        List<BudgetAlerts.Alert> alerts = overview == null
                ? new ArrayList<>() : BudgetAlerts.forExpense(overview, category);
        boolean gone = isFinishing() || isDestroyed();
        if (failed || !found) {
            Toast.makeText(getApplicationContext(),
                    failed ? R.string.save_failed : R.string.tx_not_found, Toast.LENGTH_SHORT).show();
            if (gone) return;
            if (!found) finish();
            else setBusy(false);
            return;
        }
        Toast.makeText(getApplicationContext(), isEditing() ? R.string.updated : R.string.saved,
                Toast.LENGTH_SHORT).show();
        if (gone) {
            if (!alerts.isEmpty()) {
                Toast.makeText(getApplicationContext(), BudgetUi.joinMessages(alerts), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (alerts.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.budget_alert_title)
                .setMessage(BudgetUi.joinMessages(alerts))
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(d -> finish())
                .show();
    }

    // ------------------------------------------------------------------- delete

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isEditing()) getMenuInflater().inflate(R.menu.edit_transaction_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        if (busy || !editLoaded) return;
        String amountText = amountInput.getText() == null ? "" : amountInput.getText().toString().trim();
        Object sel = categorySpinner.getSelectedItem();
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, sel == null ? "" : sel.toString(),
                        amountText.isEmpty() ? "" : MoneyUtils.SYMBOL + " " + amountText))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> deleteNow())
                .show();
    }

    private void deleteNow() {
        final long id = editingId;
        setBusy(true);
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            boolean failed = false;
            try {
                TransactionDao dao = AppDatabase.getInstance(this).transactionDao();
                Transaction existing = dao.getById(id);
                if (existing != null) dao.delete(existing);
            } catch (RuntimeException e) {
                failed = true;
            }
            final boolean fFailed = failed;
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(), fFailed ? R.string.save_failed : R.string.deleted,
                        Toast.LENGTH_SHORT).show();
                if (isFinishing() || isDestroyed()) return;
                if (fFailed) setBusy(false);
                else finish();
            });
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_DATE, selectedDate.getTimeInMillis());
        outState.putBoolean(STATE_LOADED, editLoaded);
    }
}

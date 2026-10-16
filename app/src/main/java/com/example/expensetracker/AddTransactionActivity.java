package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddTransactionActivity extends AppCompatActivity {

    private RadioGroup typeGroup;
    private TextInputLayout amountLayout;
    private TextInputEditText amountInput, noteInput;
    private Spinner categorySpinner;
    private Button dateButton;
    private final Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

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
        Button save = findViewById(R.id.saveButton);

        updateCategories();
        typeGroup.setOnCheckedChangeListener((g, id) -> updateCategories());
        updateDateButton();
        dateButton.setOnClickListener(v -> new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate.set(y, m, d);
            updateDateButton();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)).show());
        save.setOnClickListener(v -> save());
    }

    private boolean isIncome() {
        return typeGroup.getCheckedRadioButtonId() == R.id.radioIncome;
    }

    private void updateCategories() {
        int arr = isIncome() ? R.array.income_categories : R.array.expense_categories;
        ArrayAdapter<CharSequence> a = ArrayAdapter.createFromResource(this, arr,
                android.R.layout.simple_spinner_item);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(a);
    }

    private void updateDateButton() {
        dateButton.setText(dateFormat.format(selectedDate.getTime()));
    }

    private void save() {
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

        Transaction t = new Transaction();
        t.type = isIncome() ? Transaction.TYPE_INCOME : Transaction.TYPE_EXPENSE;
        t.amountPaisa = paisa;
        t.category = sel.toString();
        t.note = noteInput.getText() == null ? "" : noteInput.getText().toString().trim();
        t.dateMillis = day.getTimeInMillis();
        t.createdAt = System.currentTimeMillis();

        AppDatabase.WRITE_EXECUTOR.execute(() ->
                AppDatabase.getInstance(this).transactionDao().insert(t));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
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

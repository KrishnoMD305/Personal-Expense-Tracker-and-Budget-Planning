package com.example.expensetracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BillsActivity extends AppCompatActivity {
    private static final String PREFS = "stage4_prefs";
    private static final String PREF_ASKED = "notification_permission_asked";
    private static final int[] REMINDER_DAYS = {0, 1, 2, 3, 7};
    private PlanRowAdapter adapter;
    private TextView emptyView;
    private View banner;
    private List<Bill> lastBills;
    private List<String> expenseCategories;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                updateBanner();
                if (granted) {
                    BackgroundWork.runNow(this);
                } else {
                    Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plan_list);
        setTitle(R.string.bills);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        expenseCategories = Arrays.asList(getResources().getStringArray(R.array.expense_categories));

        emptyView = findViewById(R.id.planEmpty);
        emptyView.setText(R.string.bills_empty);
        ((TextView) findViewById(R.id.planIntro)).setText(R.string.bills_intro);
        banner = findViewById(R.id.notifBanner);
        findViewById(R.id.notifBannerButton).setOnClickListener(v -> onEnableNotifications());

        RecyclerView list = findViewById(R.id.planList);
        adapter = new PlanRowAdapter(row -> showOptions((Bill) row.tag));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.planFab);
        fab.setContentDescription(getString(R.string.bill_new));
        fab.setOnClickListener(v -> showEditor(null));

        BillRepository.observeAll(this).observe(this, bills -> {
            lastBills = bills;
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBanner();
        render();
    }

    private void render() {
        List<Bill> bills = lastBills;
        List<PlanRow> rows = new ArrayList<>();
        if (bills != null) {
            long today = PlanDates.today();
            List<Bill> overdue = new ArrayList<>(), upcoming = new ArrayList<>(), paid = new ArrayList<>();
            for (Bill b : bills) {
                BillState s = BillCalculator.state(b, today);
                if (s == BillState.PAID) paid.add(b);
                else if (s == BillState.OVERDUE) overdue.add(b);
                else upcoming.add(b);
            }
            Collections.sort(paid, (a, b) -> Long.compare(b.paidAt, a.paidAt));
            addSection(rows, "h:overdue", R.string.bills_overdue, overdue, today);
            addSection(rows, "h:upcoming", R.string.bills_upcoming, upcoming, today);
            addSection(rows, "h:paid", R.string.bills_paid, paid, today);
        }
        adapter.submitList(rows);
        emptyView.setVisibility(bills != null && bills.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addSection(List<PlanRow> rows, String key, int titleRes, List<Bill> bills, long today) {
        if (bills.isEmpty()) return;
        rows.add(PlanRow.header(key, getString(titleRes, bills.size())));
        for (Bill b : bills) rows.add(row(b, today));
    }

    private PlanRow row(Bill b, long today) {
        BillState s = BillCalculator.state(b, today);
        int color = PlanText.billColor(this, s);
        String line1;
        String line2;
        if (s == BillState.PAID) {
            line1 = getString(b.paymentTransactionId != 0 ? R.string.bill_paid_on_expense : R.string.bill_paid_on,
                    PlanDates.format(b.paidAt));
            line2 = b.category;
        } else {
            line1 = PlanText.billDue(this, b, today);
            line2 = b.category + " • " + reminderLabel(b.reminderDaysBefore);
        }
        return PlanRow.item("bill:" + b.id, b.name, MoneyUtils.format(b.amountPaisa), color, line1, line2,
                PlanRow.NO_PROGRESS, 0, s == BillState.PAID, b);
    }

    private String reminderLabel(int days) {
        int idx = indexOfReminder(days);
        return getResources().getStringArray(R.array.reminder_labels)[idx];
    }

    private static int indexOfReminder(int days) {
        for (int i = 0; i < REMINDER_DAYS.length; i++) if (REMINDER_DAYS[i] == days) return i;
        return 0;
    }

    private void showOptions(Bill b) {
        if (b.paid) {
            CharSequence[] items = {getString(R.string.bill_mark_unpaid), getString(R.string.delete)};
            new AlertDialog.Builder(this).setTitle(b.name).setItems(items, (d, which) -> {
                if (which == 0) confirmMarkUnpaid(b);
                else confirmDelete(b);
            }).show();
        } else {
            CharSequence[] items = {getString(R.string.bill_mark_paid), getString(R.string.edit),
                    getString(R.string.delete)};
            new AlertDialog.Builder(this).setTitle(b.name).setItems(items, (d, which) -> {
                if (which == 0) confirmMarkPaid(b);
                else if (which == 1) showEditor(b);
                else confirmDelete(b);
            }).show();
        }
    }

    private void confirmMarkPaid(Bill b) {
        final boolean[] record = {true};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bill_mark_paid_title, b.name))
                .setMultiChoiceItems(new CharSequence[]{getString(R.string.bill_record_expense,
                        MoneyUtils.format(b.amountPaisa))}, record, (d, i, checked) -> record[0] = checked)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bill_mark_paid, (d, w) ->
                        BillRepository.markPaid(this, b.id, record[0], err ->
                                Toast.makeText(this, err != null ? err : getString(R.string.bill_marked_paid),
                                        Toast.LENGTH_SHORT).show()))
                .show();
    }

    private void confirmMarkUnpaid(Bill b) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bill_mark_unpaid)
                .setMessage(b.paymentTransactionId != 0 ? R.string.bill_unpaid_message_expense
                        : R.string.bill_unpaid_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bill_mark_unpaid, (d, w) ->
                        BillRepository.markUnpaid(this, b.id, err -> {
                            if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    private void confirmDelete(Bill b) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.bill_delete_title)
                .setMessage(getString(R.string.bill_delete_message, b.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) ->
                        BillRepository.delete(this, b.id, err -> {
                            if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    private void showEditor(Bill existing) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_bill, null);
        TextInputLayout nameLayout = content.findViewById(R.id.billNameLayout);
        TextInputEditText nameInput = content.findViewById(R.id.billNameInput);
        TextInputLayout amountLayout = content.findViewById(R.id.billAmountLayout);
        TextInputEditText amountInput = content.findViewById(R.id.billAmountInput);
        Spinner categorySpinner = content.findViewById(R.id.billCategorySpinner);
        Spinner reminderSpinner = content.findViewById(R.id.billReminderSpinner);
        TextView formError = content.findViewById(R.id.billFormError);
        DateField due = new DateField(this, content.findViewById(R.id.billDueButton),
                existing == null ? PlanDates.today() : existing.dueDateMillis, R.string.bill_due_date, null);

        List<String> cats = new ArrayList<>(expenseCategories);
        if (existing != null && !cats.contains(existing.category)) cats.add(existing.category);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cats);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(catAdapter);
        ArrayAdapter<CharSequence> remAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                getResources().getTextArray(R.array.reminder_labels));
        remAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderSpinner.setAdapter(remAdapter);
        if (existing != null) {
            nameInput.setText(existing.name);
            amountInput.setText(BigDecimal.valueOf(existing.amountPaisa, 2).toPlainString());
            categorySpinner.setSelection(Math.max(0, cats.indexOf(existing.category)));
            reminderSpinner.setSelection(indexOfReminder(existing.reminderDaysBefore));
        } else {
            int utilities = cats.indexOf("Utilities");
            if (utilities >= 0) categorySpinner.setSelection(utilities);
            reminderSpinner.setSelection(indexOfReminder(2));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.bill_new : R.string.bill_edit)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(v -> {
            nameLayout.setError(null);
            amountLayout.setError(null);
            formError.setVisibility(View.GONE);
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString();
            String nameErr = PlanValidator.validateName(name);
            if (nameErr != null) {
                nameLayout.setError(nameErr);
                return;
            }
            long paisa;
            try {
                paisa = MoneyUtils.parseToPaisa(amountInput.getText() == null ? "" : amountInput.getText().toString());
            } catch (MoneyUtils.InvalidAmountException e) {
                amountLayout.setError(e.getMessage());
                return;
            }
            String dueErr = PlanValidator.validateDueDate(due.get());
            if (dueErr != null) {
                showError(formError, dueErr);
                return;
            }
            Object cat = categorySpinner.getSelectedItem();
            int reminder = REMINDER_DAYS[Math.max(0, reminderSpinner.getSelectedItemPosition())];
            ok.setEnabled(false);
            BillRepository.save(this, existing == null ? 0 : existing.id, name, paisa,
                    cat == null ? "" : cat.toString(), due.get(), reminder, err -> {
                        if (err == null) {
                            dialog.dismiss();
                            Toast.makeText(this, R.string.bill_saved, Toast.LENGTH_SHORT).show();
                            maybeAskForNotifications();
                        } else {
                            ok.setEnabled(true);
                            showError(formError, err);
                        }
                    });
        });
        nameInput.requestFocus();
    }

    private static void showError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void updateBanner() {
        banner.setVisibility(NotificationHelper.canNotify(this) ? View.GONE : View.VISIBLE);
    }

    private void maybeAskForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasPermission(this)
                && !prefs().getBoolean(PREF_ASKED, false)) {
            prefs().edit().putBoolean(PREF_ASKED, true).apply();
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void onEnableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasPermission(this)) {
            boolean asked = prefs().getBoolean(PREF_ASKED, false);
            // After a denial Android stops showing the dialog once "don't ask again" applies: go to settings.
            if (asked && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                openNotificationSettings();
            } else {
                prefs().edit().putBoolean(PREF_ASKED, true).apply();
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            openNotificationSettings();
        }
    }

    private void openNotificationSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        }
        startActivity(i);
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

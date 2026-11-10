package com.example.expensetracker;

import com.example.expensetracker.databinding.ActivityPlanListBinding;
import com.example.expensetracker.databinding.DialogBillBinding;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
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
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Bills: overdue / upcoming / paid lists, paid status, reminders and the notification permission. */
public class BillsActivity extends BaseActivity {
    private ActivityPlanListBinding binding;
    private static final String PREFS = "stage4_prefs";
    private static final String PREF_ASKED = "notification_permission_asked";
    /** Reminder lead times offered in the editor; must match R.array.reminder_labels. */
    private static final int[] REMINDER_DAYS = {0, 1, 2, 3, 7};

    private PlanRowAdapter adapter;
    private TextView emptyView;
    private View banner;
    private List<Bill> lastBills;
    private List<String> expenseCategories;

    // Stage 7: state of the open bill editor (null when no editor is open)
    private DialogBillBinding editor;
    private String editorReceipt = "";          // receipt file shown in the editor ("" = none)
    private String editorOriginalReceipt = "";  // the bill's receipt when the editor was opened
    private boolean editorSaved;
    private File pendingCapture;

    /** Content Provider: contact picker. ACTION_PICK on Phone.CONTENT_URI needs no READ_CONTACTS permission. */
    private final ActivityResultLauncher<Intent> contactLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    onContactPicked(result.getData().getData());
                }
            });

    /** System photo picker (no storage permission needed). */
    private final ActivityResultLauncher<PickVisualMediaRequest> photoLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) importReceipt(uri);
            });

    /** Camera: the photo is written to our own cache file through the FileProvider (no CAMERA permission). */
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), taken -> {
                File f = pendingCapture;
                pendingCapture = null;
                if (taken && f != null && f.isFile()) importReceipt(Uri.fromFile(f));
            });

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                updateBanner();
                if (granted) {
                    BackgroundWork.runNow(this); // show any reminder that is already due
                } else {
                    Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlanListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle(R.string.bills);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        expenseCategories = Arrays.asList(getResources().getStringArray(R.array.expense_categories));

        emptyView = binding.planEmpty;
        emptyView.setText(R.string.bills_empty);
        binding.planIntro.setText(R.string.bills_intro);
        banner = binding.notifBanner;
        binding.notifBannerButton.setOnClickListener(v -> onEnableNotifications());

        RecyclerView list = binding.planList;
        adapter = new PlanRowAdapter(row -> showOptions((Bill) row.tag));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FloatingActionButton fab = binding.planFab;
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
        render(); // overdue status changes with the date
    }

    // ----------------------------------------------------------------------- list

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
            // the DAO sorts unpaid bills by due date; most recently paid first looks best for history
            Collections.sort(paid, (a, b) -> Long.compare(b.paidAt, a.paidAt)); // List.sort needs API 24
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
            line2 = b.category + receiptTag(b);
        } else {
            line1 = PlanText.billDue(this, b, today);
            line2 = b.category + " • " + reminderLabel(b.reminderDaysBefore) + receiptTag(b);
        }
        return PlanRow.item("bill:" + b.id, b.name, MoneyUtils.format(b.amountPaisa), color, line1, line2,
                PlanRow.NO_PROGRESS, 0, s == BillState.PAID, b);
    }

    private String receiptTag(Bill b) {
        return b.receiptFile.isEmpty() ? "" : " • " + getString(R.string.bill_receipt_tag);
    }

    private String reminderLabel(int days) {
        int idx = indexOfReminder(days);
        return getResources().getStringArray(R.array.reminder_labels)[idx];
    }

    private static int indexOfReminder(int days) {
        for (int i = 0; i < REMINDER_DAYS.length; i++) if (REMINDER_DAYS[i] == days) return i;
        return 0;
    }

    // -------------------------------------------------------------------- actions

    private void showOptions(Bill b) {
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (b.paid) {
            labels.add(getString(R.string.bill_mark_unpaid));
            actions.add(() -> confirmMarkUnpaid(b));
        } else {
            labels.add(getString(R.string.bill_mark_paid));
            actions.add(() -> confirmMarkPaid(b));
            labels.add(getString(R.string.edit));
            actions.add(() -> showEditor(b));
        }
        if (!b.receiptFile.isEmpty()) {
            labels.add(getString(R.string.bill_receipt_view));
            actions.add(() -> viewReceipt(b));
        }
        labels.add(getString(R.string.delete));
        actions.add(() -> confirmDelete(b));
        new AlertDialog.Builder(this).setTitle(b.name)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .show();
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

    // ------------------------------------------------------------------- editor

    private void showEditor(Bill existing) {
        final DialogBillBinding form = DialogBillBinding.inflate(getLayoutInflater());
        editor = form;
        editorSaved = false;
        editorOriginalReceipt = existing == null ? "" : existing.receiptFile;
        editorReceipt = editorOriginalReceipt;
        TextInputLayout nameLayout = form.billNameLayout;
        TextInputEditText nameInput = form.billNameInput;
        TextInputLayout amountLayout = form.billAmountLayout;
        TextInputEditText amountInput = form.billAmountInput;
        Spinner categorySpinner = form.billCategorySpinner;
        Spinner reminderSpinner = form.billReminderSpinner;
        TextView formError = form.billFormError;
        DateField due = new DateField(this, form.billDueButton,
                existing == null ? PlanDates.today() : existing.dueDateMillis, R.string.bill_due_date, null);

        form.billPickContactButton.setOnClickListener(v -> pickContact());
        form.receiptGalleryButton.setOnClickListener(v -> photoLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        form.receiptCameraButton.setOnClickListener(v -> takePhoto());
        form.receiptRemoveButton.setOnClickListener(v -> {
            discardUnsavedReceipt();
            editorReceipt = "";
            refreshReceiptViews();
        });
        refreshReceiptViews();

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
                .setView(form.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (!editorSaved) discardUnsavedReceipt(); // a picture that was never saved must not pile up
            if (editor == form) editor = null;
        });
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
            // null = receipt unchanged; "" = removed; otherwise the newly chosen photo
            final String receiptChange = editorReceipt.equals(editorOriginalReceipt) ? null : editorReceipt;
            ok.setEnabled(false);
            BillRepository.save(this, existing == null ? 0 : existing.id, name, paisa,
                    cat == null ? "" : cat.toString(), due.get(), reminder, receiptChange, err -> {
                        if (err == null) {
                            editorSaved = true;
                            dialog.dismiss();
                            Toast.makeText(this, R.string.bill_saved, Toast.LENGTH_SHORT).show();
                            maybeAskForNotifications();
                            uploadReceiptsInBackground();
                        } else {
                            ok.setEnabled(true);
                            showError(formError, err);
                        }
                    });
        });
        nameInput.requestFocus();
    }

    // ----------------------------------------------------- contacts (Content Provider)

    private void pickContact() {
        Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        try {
            contactLauncher.launch(pick);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.bill_no_contacts_app, Toast.LENGTH_LONG).show();
        }
    }

    /** Reads the contact's display name from the Contacts provider with ContentResolver.query. */
    private void onContactPicked(Uri contactUri) {
        String name = null;
        try (Cursor c = getContentResolver().query(contactUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (RuntimeException e) {
            // the provider refused or vanished: handled below like "no name"
        }
        if (name == null || name.trim().isEmpty()) {
            Toast.makeText(this, R.string.bill_contact_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        name = name.trim();
        if (name.length() > 40) name = name.substring(0, 40); // the field's maxLength
        if (editor != null) {
            editor.billNameInput.setText(name);
            if (editor.billNameInput.getText() != null) {
                editor.billNameInput.setSelection(editor.billNameInput.getText().length());
            }
        }
    }

    // ----------------------------------------------------- receipt photo

    private void takePhoto() {
        try {
            pendingCapture = ReceiptStore.captureTarget(this);
            if (pendingCapture.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pendingCapture.delete();
            }
            cameraLauncher.launch(ReceiptStore.captureUri(this, pendingCapture));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.bill_no_camera_app, Toast.LENGTH_LONG).show();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, R.string.bill_receipt_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Copies the chosen / captured picture into the app's storage (background thread) and shows it. */
    private void importReceipt(Uri source) {
        if (editor == null) return;
        editor.receiptStatus.setText(R.string.bill_receipt_saving);
        final Context app = getApplicationContext();
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            String name = null;
            try {
                name = ReceiptStore.importFrom(app, source);
            } catch (IOException | RuntimeException e) {
                // reported below
            }
            final String stored = name;
            runOnUiThread(() -> {
                if (stored == null) {
                    Toast.makeText(this, R.string.bill_receipt_failed, Toast.LENGTH_LONG).show();
                    refreshReceiptViews();
                } else if (editor == null) {
                    ReceiptStore.delete(app, stored); // the editor was closed while the picture was being saved
                } else {
                    discardUnsavedReceipt(); // replaces an earlier, still unsaved pick
                    editorReceipt = stored;
                    refreshReceiptViews();
                }
            });
        });
    }

    /** Deletes the editor's receipt file if it was picked in this editor and never saved. */
    private void discardUnsavedReceipt() {
        if (!editorReceipt.isEmpty() && !editorReceipt.equals(editorOriginalReceipt)) {
            ReceiptStore.delete(this, editorReceipt);
        }
    }

    private void refreshReceiptViews() {
        DialogBillBinding f = editor;
        if (f == null) return;
        if (editorReceipt.isEmpty()) {
            f.receiptPreview.setVisibility(View.GONE);
            f.receiptStatus.setText(R.string.bill_receipt_none);
            f.receiptRemoveButton.setEnabled(false);
            return;
        }
        f.receiptRemoveButton.setEnabled(true);
        Bitmap thumb = ReceiptStore.thumbnail(this, editorReceipt, 480);
        if (thumb == null) {
            f.receiptPreview.setVisibility(View.GONE);
            f.receiptStatus.setText(R.string.bill_receipt_not_on_device);
        } else {
            f.receiptPreview.setImageBitmap(thumb);
            f.receiptPreview.setVisibility(View.VISIBLE);
            f.receiptStatus.setText(R.string.bill_receipt_attached);
        }
    }

    private void viewReceipt(Bill b) {
        if (ReceiptStore.exists(this, b.receiptFile)) {
            openViewer(b.receiptFile);
            return;
        }
        Toast.makeText(this, R.string.bill_receipt_downloading, Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        CloudSync.IO.execute(() -> {
            String error = null;
            try {
                ReceiptSync.ensureLocal(app, b);
            } catch (CloudException e) {
                error = e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                else openViewer(b.receiptFile);
            });
        });
    }

    private void openViewer(String file) {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(ReceiptStore.shareUri(this, file), "image/jpeg")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.bill_no_image_viewer, Toast.LENGTH_LONG).show();
        }
    }

    /** After saving: copy new receipt photos to Firebase Storage when signed in and online (silent otherwise). */
    private void uploadReceiptsInBackground() {
        final Context app = getApplicationContext();
        if (!ReceiptSync.available(app) || DeviceStateReader.read(app).airplaneMode) return;
        CloudSync.IO.execute(() -> {
            ReceiptSync.Result r = ReceiptSync.uploadPending(app, null);
            if (r.done > 0) CloudLog.log(app, CloudLog.ACTION_RECEIPT, CloudLog.RESULT_OK, r.done, "");
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (r.failed > 0) {
                    Toast.makeText(this, getString(R.string.bill_receipt_upload_failed,
                            r.error == null ? "" : r.error), Toast.LENGTH_LONG).show();
                } else if (r.done > 0) {
                    Toast.makeText(this, R.string.bill_receipt_uploaded, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (editor != null && !editorSaved) discardUnsavedReceipt();
        super.onDestroy();
    }

    private static void showError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------- notification permission

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void updateBanner() {
        banner.setVisibility(NotificationHelper.canNotify(this) ? View.GONE : View.VISIBLE);
    }

    /** After the first bill is saved, ask once (Android 13+) so reminders can actually be shown. */
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
            openNotificationSettings(); // permission granted but notifications switched off in system settings
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

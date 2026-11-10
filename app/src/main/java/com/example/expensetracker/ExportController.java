package com.example.expensetracker;

import com.example.expensetracker.databinding.DialogProgressBinding;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stage 5: everything about getting data out of (and back into) the app through the system file picker:
 * CSV export, PDF reports, full backup and restore. The Storage Access Framework is used, so the app needs
 * no storage permission and the user chooses where the file goes.
 *
 * <p>Create one per screen in {@code onCreate} (the result launchers must be registered before the screen starts)
 * and call {@link #saveState} from {@code onSaveInstanceState}.
 */
final class ExportController {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String STATE_PENDING = "export_pending";

    private final BaseActivity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> createLauncher;
    private final ActivityResultLauncher<Intent> openLauncher;
    private ExportRequest pending;
    private AlertDialog progress;

    ExportController(BaseActivity activity, @Nullable Bundle savedState) {
        this.activity = activity;
        if (savedState != null) pending = ExportRequest.fromBundle(savedState.getBundle(STATE_PENDING));
        createLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onCreateResult);
        openLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onOpenResult);
    }

    void saveState(Bundle out) {
        if (pending != null) out.putBundle(STATE_PENDING, pending.toBundle());
    }

    // ------------------------------------------------------------------ starting

    void startCsv(CsvExport.Dataset dataset, long startMillis, long endMillis) {
        launchCreate(ExportRequest.csv(dataset, startMillis, endMillis));
    }

    void startPdf(ReportPeriod period) {
        launchCreate(ExportRequest.pdf(period));
    }

    void startBackup() {
        launchCreate(ExportRequest.backup());
    }

    void startRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // file managers label .json differently, so do not filter by type
        AppLock.beginExternalFlow();
        try {
            openLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            showMessage(R.string.export_failed_title, activity.getString(R.string.no_file_picker));
        }
    }

    private void launchCreate(ExportRequest request) {
        pending = request;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(request.mimeType());
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName(request));
        AppLock.beginExternalFlow();
        try {
            createLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            pending = null;
            showMessage(R.string.export_failed_title, activity.getString(R.string.no_file_picker));
        }
    }

    static String suggestedName(ExportRequest r) {
        Date now = new Date();
        switch (r.kind) {
            case PDF:
                return "financial_report_" + (r.monthKey != null ? r.monthKey
                        : new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(r.startMillis)) + "_to_"
                        + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(r.endMillis))) + ".pdf";
            case BACKUP:
                return "expense_tracker_backup_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(now) + ".json";
            default:
                return "expense_tracker_" + r.dataset.fileStem + "_"
                        + new SimpleDateFormat("yyyyMMdd", Locale.US).format(now) + ".csv";
        }
    }

    // ------------------------------------------------------------------ export result

    private void onCreateResult(ActivityResult result) {
        final ExportRequest request = pending;
        pending = null;
        if (request == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null
                || result.getData().getData() == null) {
            return; // cancelled
        }
        final Uri uri = result.getData().getData();
        showProgress(R.string.working_export);
        final android.content.Context app = activity.getApplicationContext();
        IO.execute(() -> {
            ContentResolver resolver = app.getContentResolver();
            String error = null;
            String summary = "";
            try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("Could not open the destination file.");
                summary = write(app, request, out);
            } catch (Exception e) {
                error = describe(e);
                try {
                    DocumentsContract.deleteDocument(resolver, uri); // do not leave an empty / partial file behind
                } catch (Exception ignored) {
                    // best effort
                }
            }
            final String err = error;
            final String info = summary;
            main.post(() -> {
                hideProgress();
                if (err != null) showMessage(R.string.export_failed_title, err);
                else showSaved(request, uri, info);
            });
        });
    }

    /** Writes the requested file; returns a one-line summary of what was written. */
    private static String write(android.content.Context app, ExportRequest request, OutputStream out) throws Exception {
        switch (request.kind) {
            case PDF:
                ReportPdfExporter.write(app, request.period(), out);
                return PdfReportBuilder.periodLabel(request.period());
            case BACKUP: {
                BackupData d = BackupRepository.writeBackup(app, out);
                return app.getString(R.string.backup_summary, d.transactions.size(), d.totalRows());
            }
            default:
                return writeCsv(app, request, out);
        }
    }

    private static String writeCsv(android.content.Context app, ExportRequest request, OutputStream out) throws Exception {
        BackupDao dao = AppDatabase.getInstance(app).backupDao();
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        int rows;
        switch (request.dataset) {
            case BUDGETS: {
                List<Budget> list = dao.budgets();
                CsvExport.writeBudgets(w, list);
                rows = list.size();
                break;
            }
            case GOALS: {
                List<SavingsGoal> goals = dao.goals();
                CsvExport.writeGoals(w, goals, dao.contributions());
                rows = goals.size();
                break;
            }
            case CONTRIBUTIONS: {
                List<GoalContribution> list = dao.contributions();
                CsvExport.writeContributions(w, list, dao.goals());
                rows = list.size();
                break;
            }
            case RECURRING: {
                List<RecurringRule> list = dao.rules();
                CsvExport.writeRecurring(w, list);
                rows = list.size();
                break;
            }
            case BILLS: {
                List<Bill> list = dao.bills();
                CsvExport.writeBills(w, list);
                rows = list.size();
                break;
            }
            default: {
                List<Transaction> list = dao.transactions();
                CsvExport.writeTransactions(w, list, request.startMillis, request.endMillis);
                int n = 0;
                for (Transaction t : list) {
                    if (t.dateMillis >= request.startMillis && t.dateMillis < request.endMillis) n++;
                }
                rows = n;
            }
        }
        w.flush();
        return app.getString(R.string.csv_summary, rows);
    }

    private void showSaved(ExportRequest request, Uri uri, String summary) {
        String name = displayName(uri);
        String message = activity.getString(R.string.export_saved_message, name == null ? "" : name, summary);
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(R.string.export_saved_title)
                .setMessage(message)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.share, (d, w) -> share(request, uri));
        if (request.kind != ExportRequest.Kind.BACKUP) {
            b.setPositiveButton(R.string.open, (d, w) -> open(request, uri));
        }
        b.show();
    }

    private void open(ExportRequest request, Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, request.mimeType());
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        AppLock.beginExternalFlow();
        try {
            activity.startActivity(view);
        } catch (ActivityNotFoundException | SecurityException e) {
            showMessage(R.string.export_failed_title, activity.getString(R.string.no_viewer));
        }
    }

    private void share(ExportRequest request, Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(request.mimeType());
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        AppLock.beginExternalFlow();
        try {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)));
        } catch (ActivityNotFoundException | SecurityException e) {
            showMessage(R.string.export_failed_title, activity.getString(R.string.no_viewer));
        }
    }

    // ------------------------------------------------------------------ restore

    private void onOpenResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null
                || result.getData().getData() == null) {
            return;
        }
        final Uri uri = result.getData().getData();
        showProgress(R.string.working_restore_check);
        final android.content.Context app = activity.getApplicationContext();
        IO.execute(() -> {
            BackupData data = null;
            String error = null;
            try {
                data = BackupRepository.readBackup(app.getContentResolver(), uri);
            } catch (BackupException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = describe(e);
            }
            final BackupData parsed = data;
            final String err = error;
            main.post(() -> {
                hideProgress();
                if (err != null) showMessage(R.string.restore_failed_title, err);
                else confirmRestore(parsed);
            });
        });
    }

    private void confirmRestore(final BackupData data) {
        String when = data.createdAtMillis > 0
                ? new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(data.createdAtMillis))
                : activity.getString(R.string.unknown);
        String message = activity.getString(R.string.restore_confirm_message, when, data.transactions.size(),
                data.budgets.size(), data.goals.size(), data.contributions.size(), data.rules.size(), data.bills.size());
        new AlertDialog.Builder(activity)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restore, (d, w) -> runRestore(data))
                .show();
    }

    private void runRestore(final BackupData data) {
        showProgress(R.string.working_restore);
        final android.content.Context app = activity.getApplicationContext();
        IO.execute(() -> {
            String error = null;
            try {
                BackupRepository.restore(app, data);
            } catch (Exception e) {
                error = describe(e);
            }
            final String err = error;
            main.post(() -> {
                hideProgress();
                if (err != null) {
                    showMessage(R.string.restore_failed_title, activity.getString(R.string.restore_failed_message, err));
                } else {
                    BackgroundWork.runNow(app); // catch up recurring items / reminders for the restored rules
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.restore_done_title)
                            .setMessage(R.string.restore_done_message)
                            .setPositiveButton(R.string.ok, (d, w) -> activity.recreate())
                            .setOnCancelListener(d -> activity.recreate())
                            .show();
                }
            });
        });
    }

    // ------------------------------------------------------------------ helpers

    private String displayName(Uri uri) {
        try (Cursor c = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
            // the name is only informational
        }
        return null;
    }

    private String describe(Exception e) {
        if (e instanceof java.io.IOException) return activity.getString(R.string.err_io, String.valueOf(e.getMessage()));
        return String.valueOf(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private void showMessage(int titleRes, String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity).setTitle(titleRes).setMessage(message)
                .setPositiveButton(R.string.ok, null).show();
    }

    private void showProgress(int messageRes) {
        hideProgress();
        DialogProgressBinding pb = DialogProgressBinding.inflate(LayoutInflater.from(activity));
        pb.progressMessage.setText(messageRes);
        progress = new AlertDialog.Builder(activity).setView(pb.getRoot()).setCancelable(false).create();
        progress.show();
    }

    private void hideProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (IllegalArgumentException ignored) {
                // the window is already gone (screen rotated)
            }
            progress = null;
        }
    }
}

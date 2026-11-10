package com.example.expensetracker;

import com.example.expensetracker.databinding.ActivityCloudAccountBinding;
import com.example.expensetracker.databinding.DialogProgressBinding;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Stage 6: Firebase account (email + password) and cloud backup.
 *
 * <p>The account is optional: the app is fully usable without it. When signed in, the user can back the data up
 * to Cloud Firestore, restore it on this or another device, turn on a daily automatic backup, or delete the
 * cloud copy. Restoring reuses the Stage 5 machinery (validation, safety copy, all-or-nothing replace).
 */
public class CloudAccountActivity extends BaseActivity {
    private ActivityCloudAccountBinding binding;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppSettings settings;

    private View notConfiguredCard, accountCard, signedOutGroup, signedInGroup, backupCard;
    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private TextView signedInEmail, cloudStatus, cloudThisDevice;
    private SwitchMaterial switchAuto;
    private AlertDialog progress;
    private DialogProgressBinding progressViews; // Stage 7: message + bar of the open progress dialog
    private long dismissedJob = -1;              // a job whose dialog the user closed (it keeps running)
    private long shownJob = -1;                  // a finished job whose result was already shown
    private boolean bindingNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCloudAccountBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        settings = AppSettings.get(this);

        notConfiguredCard = binding.notConfiguredCard;
        accountCard = binding.accountCard;
        signedOutGroup = binding.signedOutGroup;
        signedInGroup = binding.signedInGroup;
        backupCard = binding.backupCard;
        emailLayout = binding.emailLayout;
        passwordLayout = binding.passwordLayout;
        emailInput = binding.emailInput;
        passwordInput = binding.passwordInput;
        signedInEmail = binding.signedInEmail;
        cloudStatus = binding.cloudStatusText;
        cloudThisDevice = binding.cloudThisDeviceText;
        switchAuto = binding.switchCloudAuto;

        binding.signInButton.setOnClickListener(v -> signIn());
        binding.createAccountButton.setOnClickListener(v -> createAccount());
        binding.forgotPasswordButton.setOnClickListener(v -> forgotPassword());
        binding.signOutButton.setOnClickListener(v -> confirmSignOut());
        binding.backupNowButton.setOnClickListener(v -> runUpload(false));
        binding.cloudRestoreButton.setOnClickListener(v -> startRestore());
        binding.deleteCloudButton.setOnClickListener(v -> confirmDelete());
        switchAuto.setOnCheckedChangeListener((b, checked) -> {
            if (bindingNow) return;
            settings.setCloudAutoBackup(checked);
            CloudSyncWorker.apply(this);
            if (checked) Toast.makeText(this, R.string.cloud_auto_on_toast, Toast.LENGTH_SHORT).show();
        });
        BackupJob.status().observe(this, this::onJobStatus); // Stage 7: follow the foreground service
    }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    @Override
    protected void onDestroy() {
        hideProgress();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ state

    private void bind() {
        boolean configured = CloudConfig.isConfigured(this);
        String email = CloudConfig.currentEmail(this); // null = nobody signed in
        boolean signedIn = email != null;

        notConfiguredCard.setVisibility(configured ? View.GONE : View.VISIBLE);
        accountCard.setVisibility(configured ? View.VISIBLE : View.GONE);
        signedOutGroup.setVisibility(configured && !signedIn ? View.VISIBLE : View.GONE);
        signedInGroup.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        backupCard.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (!signedIn) return;

        signedInEmail.setText(getString(R.string.cloud_signed_in_as, email));
        bindingNow = true;
        switchAuto.setChecked(settings.cloudAutoBackup());
        bindingNow = false;
        refreshStatus();
        refreshActivityLog();
    }

    /** Stage 7: the newest lines of the Realtime Database sync log. */
    private void refreshActivityLog() {
        binding.activityCard.setVisibility(View.VISIBLE);
        binding.cloudActivityText.setText(R.string.cloud_activity_loading);
        CloudLog.loadRecent(this, 8, (entries, error) -> {
            if (!alive()) return;
            if (error != null) {
                binding.cloudActivityText.setText(error);
            } else if (entries.isEmpty()) {
                binding.cloudActivityText.setText(R.string.cloud_activity_none);
            } else {
                StringBuilder sb = new StringBuilder();
                for (CloudLog.Entry e : entries) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(e.time > 0 ? format(e.time) : "?").append("  \u2022  ").append(e.action)
                            .append(": ").append(e.result);
                    if (e.rows > 0) sb.append(" (").append(e.rows).append(')');
                    if (!e.message.isEmpty()) sb.append("\n    ").append(e.message);
                }
                binding.cloudActivityText.setText(sb.toString());
            }
        });
    }

    /** Asks the cloud what it holds (off the main thread) and shows it. */
    private void refreshStatus() {
        final String uid = CloudConfig.currentUid(this);
        if (uid == null) return;
        cloudStatus.setText(R.string.cloud_status_checking);
        long mine = settings.lastCloudBackupMillis();
        cloudThisDevice.setText(mine > 0 ? getString(R.string.cloud_status_this_device, format(mine)) : "");
        final android.content.Context app = getApplicationContext();
        CloudSync.IO.execute(() -> {
            CloudSync.RemoteInfo info = null;
            String error = null;
            try {
                info = CloudSync.remoteInfo(app);
            } catch (CloudException e) {
                error = e.getMessage();
            }
            final CloudSync.RemoteInfo result = info;
            final String err = error;
            main.post(() -> {
                if (!alive()) return;
                if (err != null) {
                    cloudStatus.setText(getString(R.string.cloud_status_failed, err));
                } else if (result == null) {
                    cloudStatus.setText(R.string.cloud_status_none);
                } else {
                    String text = getString(R.string.cloud_status_remote, format(result.updatedAt), (int) result.rows,
                            result.device.isEmpty() ? getString(R.string.unknown) : result.device);
                    if (result.updatedAt != settings.cloudBase(uid)) {
                        text += "\n\n" + getString(R.string.cloud_status_other_device);
                    }
                    cloudStatus.setText(text);
                }
            });
        });
    }

    // ------------------------------------------------------------------ account

    private void signIn() {
        final String email = emailText();
        final String password = passwordText();
        if (!validate(email, password, false)) return;
        showProgress(R.string.cloud_working_signin);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            hideProgress();
            if (task.isSuccessful()) {
                onSignedIn();
            } else {
                showMessage(R.string.cloud_signin_failed_title, CloudErrors.describe(task.getException()));
            }
        });
    }

    private void createAccount() {
        final String email = emailText();
        final String password = passwordText();
        if (!validate(email, password, true)) return;
        showProgress(R.string.cloud_working_signup);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            hideProgress();
            if (task.isSuccessful()) {
                onSignedIn();
            } else {
                showMessage(R.string.cloud_signup_failed_title, CloudErrors.describe(task.getException()));
            }
        });
    }

    private void forgotPassword() {
        final String email = emailText();
        emailLayout.setError(null);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.cloud_err_email));
            return;
        }
        showProgress(R.string.cloud_working_reset);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            hideProgress();
            if (task.isSuccessful()) {
                showMessage(R.string.cloud_reset_sent_title, getString(R.string.cloud_reset_sent_message, email));
            } else {
                showMessage(R.string.cloud_reset_failed_title, CloudErrors.describe(task.getException()));
            }
        });
    }

    private boolean validate(String email, String password, boolean creating) {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        boolean ok = true;
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.cloud_err_email));
            ok = false;
        }
        if (password.length() < 6 && (creating || password.isEmpty())) {
            passwordLayout.setError(getString(R.string.cloud_err_password));
            ok = false;
        }
        return ok;
    }

    private void onSignedIn() {
        passwordInput.setText("");
        CloudLog.touchProfile(this);                                  // Stage 7: Realtime Database profile node
        CloudLog.log(this, CloudLog.ACTION_SIGN_IN, CloudLog.RESULT_OK, 0, "");
        Toast.makeText(this, R.string.cloud_welcome_toast, Toast.LENGTH_SHORT).show();
        bind();
        offerRestoreIfDeviceIsEmpty();
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_sign_out_title)
                .setMessage(R.string.cloud_sign_out_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cloud_sign_out, (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(this, R.string.cloud_signed_out_toast, Toast.LENGTH_SHORT).show();
                    bind();
                })
                .show();
    }

    /** New phone, signed in to an account that already has a backup: offer to restore it straight away. */
    private void offerRestoreIfDeviceIsEmpty() {
        final android.content.Context app = getApplicationContext();
        CloudSync.IO.execute(() -> {
            boolean offer = false;
            try {
                int localRows = BackupRepository.snapshot(AppDatabase.getInstance(app)).totalRows();
                CloudSync.RemoteInfo info = localRows == 0 ? CloudSync.remoteInfo(app) : null;
                offer = info != null && info.complete;
            } catch (CloudException | RuntimeException ignored) {
                // purely a convenience; the buttons are always available
            }
            if (!offer) return;
            main.post(() -> {
                if (!alive()) return;
                new AlertDialog.Builder(this)
                        .setTitle(R.string.cloud_found_title)
                        .setMessage(R.string.cloud_found_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.restore, (d, w) -> beginRestore())
                        .show();
            });
        });
    }

    // ------------------------------------------------------------------ backup / restore (foreground service)

    /** Starts the foreground service; progress and the result come back through {@link #onJobStatus}. */
    private void runUpload(final boolean force) {
        if (!BackupService.start(this, false, force)) {
            Toast.makeText(this, R.string.job_already_running, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRestore() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_restore_confirm_title)
                .setMessage(R.string.cloud_restore_confirm_generic)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restore, (d, w) -> beginRestore())
                .show();
    }

    private void beginRestore() {
        if (!BackupService.start(this, true, false)) {
            Toast.makeText(this, R.string.job_already_running, Toast.LENGTH_SHORT).show();
        }
    }

    /** Observes {@link BackupJob}: progress dialog while running, then one result dialog. */
    private void onJobStatus(BackupJob.Status st) {
        if (st == null) {
            hideProgress();
            return;
        }
        if (st.isRunning()) {
            if (st.id == dismissedJob || !alive()) return;
            showJobProgress(st);
            return;
        }
        hideProgress();
        if (st.id == shownJob || !alive()) return;
        shownJob = st.id;
        BackupJob.acknowledge(st.id);
        final boolean restore = st.kind == BackupJob.Kind.RESTORE;
        switch (st.phase) {
            case DONE:
                if (restore) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.restore_done_title)
                            .setMessage(st.message + "\n\n" + getString(R.string.restore_done_message))
                            .setPositiveButton(R.string.ok, (d, w) -> recreate())
                            .setOnCancelListener(d -> recreate())
                            .show();
                } else {
                    showMessage(R.string.cloud_backup_done_title, st.message);
                }
                break;
            case CONFLICT:
                confirmReplace(st.remote);
                break;
            default:
                showMessage(restore ? R.string.cloud_restore_failed_title : R.string.cloud_backup_failed_title,
                        st.message);
        }
        refreshStatus();
        refreshActivityLog();
    }

    private void confirmReplace(CloudSync.RemoteInfo remote) {
        String device = remote.device.isEmpty() ? getString(R.string.unknown) : remote.device;
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_conflict_title)
                .setMessage(getString(R.string.cloud_conflict_message, format(remote.updatedAt), device))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.cloud_conflict_restore, (d, w) -> startRestore())
                .setPositiveButton(R.string.cloud_conflict_replace, (d, w) -> runUpload(true))
                .show();
    }

    // ------------------------------------------------------------------ delete cloud data

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_delete_title)
                .setMessage(R.string.cloud_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> runDelete())
                .show();
    }

    private void runDelete() {
        showProgress(R.string.cloud_working_delete);
        final android.content.Context app = getApplicationContext();
        CloudSync.IO.execute(() -> {
            String error = null;
            try {
                CloudSync.deleteCloudData(app);
            } catch (CloudException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = String.valueOf(e.getMessage());
            }
            final String err = error;
            main.post(() -> {
                hideProgress();
                if (!alive()) return;
                if (err != null) {
                    showMessage(R.string.cloud_delete_failed_title, err);
                } else {
                    Toast.makeText(this, R.string.cloud_deleted_toast, Toast.LENGTH_SHORT).show();
                }
                refreshStatus();
            });
        });
    }

    // ------------------------------------------------------------------ helpers

    private String emailText() {
        return emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
    }

    private String passwordText() {
        return passwordInput.getText() == null ? "" : passwordInput.getText().toString();
    }

    private static String format(long millis) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void showMessage(int titleRes, String message) {
        if (!alive()) return;
        new AlertDialog.Builder(this).setTitle(titleRes).setMessage(message)
                .setPositiveButton(R.string.ok, null).show();
    }

    private void showProgress(int messageRes) {
        hideProgress();
        DialogProgressBinding views = DialogProgressBinding.inflate(getLayoutInflater());
        views.progressMessage.setText(messageRes);
        progressViews = views;
        progress = new AlertDialog.Builder(this).setView(views.getRoot()).setCancelable(false).create();
        progress.show();
    }

    /** The progress dialog of a running foreground job: message, bar and "n of m". The user may close it. */
    private void showJobProgress(BackupJob.Status st) {
        if (progress == null || progressViews == null) {
            hideProgress();
            DialogProgressBinding views = DialogProgressBinding.inflate(getLayoutInflater());
            progressViews = views;
            progress = new AlertDialog.Builder(this).setView(views.getRoot()).setCancelable(true)
                    .setOnCancelListener(d -> dismissedJob = st.id)
                    .setPositiveButton(R.string.job_run_in_background, (d, w) -> dismissedJob = st.id)
                    .create();
            progress.show();
        }
        progressViews.progressMessage.setText(st.kind == BackupJob.Kind.RESTORE
                ? R.string.working_restore : R.string.cloud_working_upload);
        progressViews.progressDetail.setVisibility(View.VISIBLE);
        progressViews.progressDetail.setText(BackupJob.stageText(this, st));
        int percent = st.percent();
        progressViews.progressBar.setVisibility(View.VISIBLE);
        if (percent < 0) {
            progressViews.progressBar.setIndeterminate(true);
        } else {
            progressViews.progressBar.setIndeterminate(false);
            progressViews.progressBar.setProgressCompat(percent, true);
        }
    }

    private void hideProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (IllegalArgumentException ignored) {
                // the window is already gone
            }
            progress = null;
            progressViews = null;
        }
    }
}

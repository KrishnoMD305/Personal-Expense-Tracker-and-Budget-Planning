package com.example.expensetracker;

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
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppSettings settings;

    private View notConfiguredCard, accountCard, signedOutGroup, signedInGroup, backupCard;
    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private TextView signedInEmail, cloudStatus, cloudThisDevice;
    private SwitchMaterial switchAuto;
    private AlertDialog progress;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_account);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        settings = AppSettings.get(this);

        notConfiguredCard = findViewById(R.id.notConfiguredCard);
        accountCard = findViewById(R.id.accountCard);
        signedOutGroup = findViewById(R.id.signedOutGroup);
        signedInGroup = findViewById(R.id.signedInGroup);
        backupCard = findViewById(R.id.backupCard);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        signedInEmail = findViewById(R.id.signedInEmail);
        cloudStatus = findViewById(R.id.cloudStatusText);
        cloudThisDevice = findViewById(R.id.cloudThisDeviceText);
        switchAuto = findViewById(R.id.switchCloudAuto);

        findViewById(R.id.signInButton).setOnClickListener(v -> signIn());
        findViewById(R.id.createAccountButton).setOnClickListener(v -> createAccount());
        findViewById(R.id.forgotPasswordButton).setOnClickListener(v -> forgotPassword());
        findViewById(R.id.signOutButton).setOnClickListener(v -> confirmSignOut());
        findViewById(R.id.backupNowButton).setOnClickListener(v -> runUpload(false));
        findViewById(R.id.cloudRestoreButton).setOnClickListener(v -> startRestore());
        findViewById(R.id.deleteCloudButton).setOnClickListener(v -> confirmDelete());
        switchAuto.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            settings.setCloudAutoBackup(checked);
            CloudSyncWorker.apply(this);
            if (checked) Toast.makeText(this, R.string.cloud_auto_on_toast, Toast.LENGTH_SHORT).show();
        });
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
        binding = true;
        switchAuto.setChecked(settings.cloudAutoBackup());
        binding = false;
        refreshStatus();
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
                        .setPositiveButton(R.string.restore, (d, w) -> startRestore())
                        .show();
            });
        });
    }

    // ------------------------------------------------------------------ backup

    private void runUpload(final boolean force) {
        showProgress(R.string.cloud_working_upload);
        final android.content.Context app = getApplicationContext();
        CloudSync.IO.execute(() -> {
            CloudSync.UploadResult result = null;
            String error = null;
            try {
                result = CloudSync.upload(app, force);
            } catch (CloudException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = String.valueOf(e.getMessage());
            }
            final CloudSync.UploadResult res = result;
            final String err = error;
            main.post(() -> {
                hideProgress();
                if (!alive()) return;
                if (err != null) {
                    showMessage(R.string.cloud_backup_failed_title, getString(R.string.cloud_backup_failed_message, err));
                    return;
                }
                switch (res.outcome) {
                    case UPLOADED:
                        showMessage(R.string.cloud_backup_done_title, getString(R.string.cloud_backup_done_message, res.rows));
                        break;
                    case UNCHANGED:
                        showMessage(R.string.cloud_backup_done_title, getString(R.string.cloud_backup_unchanged_message));
                        break;
                    default:
                        confirmReplace(res.remote);
                }
                refreshStatus();
            });
        });
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

    // ------------------------------------------------------------------ restore

    private void startRestore() {
        showProgress(R.string.cloud_working_download);
        final android.content.Context app = getApplicationContext();
        CloudSync.IO.execute(() -> {
            BackupData data = null;
            String error = null;
            try {
                data = CloudSync.download(app);
            } catch (CloudException | BackupException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = String.valueOf(e.getMessage());
            }
            final BackupData parsed = data;
            final String err = error;
            main.post(() -> {
                hideProgress();
                if (!alive()) return;
                if (err != null) showMessage(R.string.cloud_restore_failed_title, err);
                else confirmRestore(parsed);
            });
        });
    }

    private void confirmRestore(final BackupData data) {
        String message = getString(R.string.cloud_restore_confirm_message, data.createdAtMillis > 0
                        ? format(data.createdAtMillis) : getString(R.string.unknown),
                data.transactions.size(), data.budgets.size(), data.goals.size(), data.contributions.size(),
                data.rules.size(), data.bills.size());
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_restore_confirm_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restore, (d, w) -> runRestore(data))
                .show();
    }

    private void runRestore(final BackupData data) {
        showProgress(R.string.working_restore);
        final android.content.Context app = getApplicationContext();
        final String uid = CloudConfig.currentUid(this);
        CloudSync.IO.execute(() -> {
            String error = null;
            try {
                BackupRepository.restore(app, data); // safety copy first, then one all-or-nothing transaction
                if (uid != null) {
                    settings.setCloudBase(uid, data.createdAtMillis); // this device now matches the cloud copy
                    settings.setCloudFingerprint(uid, "");
                    settings.setLastCloudBackupMillis(data.createdAtMillis);
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String err = error;
            main.post(() -> {
                hideProgress();
                if (err != null) {
                    if (alive()) {
                        showMessage(R.string.restore_failed_title, getString(R.string.restore_failed_message, err));
                    }
                    return;
                }
                BackgroundWork.runNow(app); // catch up recurring items / reminders for the restored rules
                if (!alive()) return;
                new AlertDialog.Builder(this)
                        .setTitle(R.string.restore_done_title)
                        .setMessage(R.string.restore_done_message)
                        .setPositiveButton(R.string.ok, (d, w) -> recreate())
                        .setOnCancelListener(d -> recreate())
                        .show();
            });
        });
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
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null);
        ((TextView) v.findViewById(R.id.progressMessage)).setText(messageRes);
        progress = new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
        progress.show();
    }

    private void hideProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (IllegalArgumentException ignored) {
                // the window is already gone
            }
            progress = null;
        }
    }
}

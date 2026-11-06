package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Stage 5: theme, app lock, exports, backup / restore and the link to About. Stage 6 adds the Firebase cloud account entry. */
public class SettingsActivity extends BaseActivity {
    private AppSettings settings;
    private ExportController export;
    private SwitchMaterial switchLock, switchBiometric, switchHide;
    private View changePin;
    private TextView biometricNote, lastBackup, cloudSummary;
    private final MaterialCardView[] tiles = new MaterialCardView[ThemeManager.ALL.length];
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        settings = AppSettings.get(this);
        export = new ExportController(this, savedInstanceState);

        switchLock = findViewById(R.id.switchLock);
        switchBiometric = findViewById(R.id.switchBiometric);
        switchHide = findViewById(R.id.switchHide);
        changePin = findViewById(R.id.changePinButton);
        biometricNote = findViewById(R.id.biometricNote);
        lastBackup = findViewById(R.id.lastBackupText);
        cloudSummary = findViewById(R.id.cloudStatusSummary);

        buildThemeTiles();

        switchLock.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            if (checked) enableLock(); else disableLock();
        });
        changePin.setOnClickListener(v -> PinDialogs.change(this, pin -> {
            settings.savePin(pin);
            Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
        }));
        switchBiometric.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            if (checked) enableBiometric(); else settings.setBiometricEnabled(false);
        });
        switchHide.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            settings.setHideContent(checked);
            recreate();
        });

        findViewById(R.id.exportCsvButton).setOnClickListener(v -> chooseCsv());
        findViewById(R.id.exportPdfButton).setOnClickListener(v -> choosePdfPeriod());
        findViewById(R.id.backupButton).setOnClickListener(v -> export.startBackup());
        findViewById(R.id.restoreButton).setOnClickListener(v -> export.startRestore());
        findViewById(R.id.cloudCard).setOnClickListener(v -> startActivity(new Intent(this, CloudAccountActivity.class)));
        findViewById(R.id.aboutCard).setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindState();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        export.saveState(out);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ themes

    private void buildThemeTiles() {
        LinearLayout[] rows = {findViewById(R.id.themeRow1), findViewById(R.id.themeRow2)};
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < ThemeManager.ALL.length; i++) {
            final String id = ThemeManager.ALL[i];
            LinearLayout row = rows[i / 2];
            MaterialCardView tile = (MaterialCardView) inflater.inflate(R.layout.item_theme_tile, row, false);
            tile.findViewById(R.id.tilePreview).setBackgroundColor(ContextCompat.getColor(this, ThemeManager.previewBackground(id)));
            tile.findViewById(R.id.tileBar).setBackgroundColor(ContextCompat.getColor(this, ThemeManager.previewBar(id)));
            ((TextView) tile.findViewById(R.id.tileSample)).setTextColor(ContextCompat.getColor(this, ThemeManager.previewText(id)));
            ((TextView) tile.findViewById(R.id.tileName)).setText(ThemeManager.nameRes(id));
            tile.setOnClickListener(v -> selectTheme(id));
            row.addView(tile);
            tiles[i] = tile;
        }
        markSelectedTile();
    }

    private void markSelectedTile() {
        String current = ThemeManager.current(this);
        int stroke = (int) (3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < tiles.length; i++) {
            boolean selected = ThemeManager.ALL[i].equals(current);
            tiles[i].setStrokeWidth(selected ? stroke : 0);
            tiles[i].setStrokeColor(ThemeColors.get(this, R.attr.etAccent));
            String name = getString(ThemeManager.nameRes(ThemeManager.ALL[i]));
            tiles[i].setContentDescription(selected ? getString(R.string.theme_selected_cd, name) : name);
            ((TextView) tiles[i].findViewById(R.id.tileName)).setText(
                    selected ? getString(R.string.theme_selected_label, name) : name);
        }
    }

    private void selectTheme(String id) {
        if (id.equals(ThemeManager.current(this))) return;
        settings.setTheme(id);
        recreate(); // this screen is rebuilt with the new theme; screens behind it follow in their onResume
    }

    // ------------------------------------------------------------------ security

    private void bindState() {
        binding = true;
        boolean lock = settings.lockEnabled();
        switchLock.setChecked(lock);
        changePin.setVisibility(lock ? View.VISIBLE : View.GONE);
        switchHide.setVisibility(lock ? View.VISIBLE : View.GONE);
        switchHide.setChecked(settings.hideContent());
        if (lock) {
            boolean bio = biometricAvailable();
            switchBiometric.setVisibility(View.VISIBLE);
            switchBiometric.setEnabled(bio);
            switchBiometric.setChecked(bio && settings.biometricEnabled());
            biometricNote.setVisibility(bio ? View.GONE : View.VISIBLE);
            biometricNote.setText(R.string.settings_biometric_unavailable);
            if (!bio && settings.biometricEnabled()) settings.setBiometricEnabled(false);
        } else {
            switchBiometric.setVisibility(View.GONE);
            biometricNote.setVisibility(View.GONE);
        }
        long last = settings.lastBackupMillis();
        lastBackup.setText(last > 0
                ? getString(R.string.settings_last_backup, new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(last)))
                : getString(R.string.settings_no_backup));
        bindCloudSummary();
        binding = false;
        markSelectedTile();
    }

    /** Stage 6: one line under "Cloud account (Firebase)": not set up / signed out / signed in as ... */
    private void bindCloudSummary() {
        if (!CloudConfig.isConfigured(this)) {
            cloudSummary.setText(R.string.cloud_status_not_configured);
            return;
        }
        String email = CloudConfig.currentEmail(this);
        cloudSummary.setText(email == null ? getString(R.string.cloud_status_signed_out)
                : getString(R.string.cloud_status_signed_in, email));
    }

    private void setSwitchQuietly(SwitchMaterial s, boolean checked) {
        binding = true;
        s.setChecked(checked);
        binding = false;
    }

    private void enableLock() {
        PinDialogs.setNew(this, pin -> {
            settings.savePin(pin);
            AppLock.markUnlocked(); // the user is inside the app right now
            Toast.makeText(this, R.string.lock_enabled, Toast.LENGTH_SHORT).show();
            recreate();
        }, () -> setSwitchQuietly(switchLock, settings.lockEnabled()));
    }

    private void disableLock() {
        PinDialogs.confirmCurrent(this, pin -> {
            settings.clearLock();
            Toast.makeText(this, R.string.lock_disabled, Toast.LENGTH_SHORT).show();
            recreate();
        }, () -> setSwitchQuietly(switchLock, settings.lockEnabled()));
    }

    private boolean biometricAvailable() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void enableBiometric() {
        if (!biometricAvailable()) {
            setSwitchQuietly(switchBiometric, false);
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        settings.setBiometricEnabled(true);
                        Toast.makeText(SettingsActivity.this, R.string.biometric_enabled, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        setSwitchQuietly(switchBiometric, false);
                    }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_confirm_title))
                .setSubtitle(getString(R.string.biometric_confirm_subtitle))
                .setNegativeButtonText(getString(android.R.string.cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build());
    }

    // ------------------------------------------------------------------ exports

    private void chooseCsv() {
        final String[] labels = getResources().getStringArray(R.array.csv_choices);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_export_csv)
                .setItems(labels, (d, which) -> {
                    long today = MonthUtils.startOfDay(System.currentTimeMillis());
                    switch (which) {
                        case 0:
                            export.startCsv(CsvExport.Dataset.TRANSACTIONS, Long.MIN_VALUE, Long.MAX_VALUE);
                            break;
                        case 1: {
                            String m = MonthUtils.current();
                            export.startCsv(CsvExport.Dataset.TRANSACTIONS, MonthUtils.startMillis(m), MonthUtils.endMillis(m));
                            break;
                        }
                        case 2:
                            export.startCsv(CsvExport.Dataset.TRANSACTIONS, MonthUtils.addDays(today, -29),
                                    MonthUtils.addDays(today, 1));
                            break;
                        case 3:
                            export.startCsv(CsvExport.Dataset.BUDGETS, 0, 0);
                            break;
                        case 4:
                            export.startCsv(CsvExport.Dataset.GOALS, 0, 0);
                            break;
                        case 5:
                            export.startCsv(CsvExport.Dataset.CONTRIBUTIONS, 0, 0);
                            break;
                        case 6:
                            export.startCsv(CsvExport.Dataset.RECURRING, 0, 0);
                            break;
                        default:
                            export.startCsv(CsvExport.Dataset.BILLS, 0, 0);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void choosePdfPeriod() {
        final String[] labels = getResources().getStringArray(R.array.pdf_period_choices);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_export_pdf)
                .setItems(labels, (d, which) -> {
                    long today = MonthUtils.startOfDay(System.currentTimeMillis());
                    String month = MonthUtils.current();
                    switch (which) {
                        case 0:
                            export.startPdf(ReportPeriod.month(month));
                            break;
                        case 1:
                            export.startPdf(ReportPeriod.month(MonthUtils.shift(month, -1)));
                            break;
                        case 2:
                            export.startPdf(ReportPeriod.custom(MonthUtils.addDays(today, -89), today));
                            break;
                        case 3: {
                            Calendar c = Calendar.getInstance();
                            c.setTimeInMillis(today);
                            c.set(Calendar.MONTH, Calendar.JANUARY);
                            c.set(Calendar.DAY_OF_MONTH, 1);
                            export.startPdf(ReportPeriod.custom(MonthUtils.startOfDay(c.getTimeInMillis()), today));
                            break;
                        }
                        default:
                            startAllTimePdf(today);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** "All time" starts at the oldest transaction (looked up off the main thread). */
    private void startAllTimePdf(final long today) {
        final android.content.Context app = getApplicationContext();
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            Long first = AppDatabase.getInstance(app).backupDao().firstTransactionMillis();
            final ReportPeriod period;
            if (first == null || first > today) {
                period = ReportPeriod.month(MonthUtils.current());
            } else {
                period = ReportPeriod.custom(MonthUtils.startOfDay(first), today);
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) export.startPdf(period);
            });
        });
    }
}

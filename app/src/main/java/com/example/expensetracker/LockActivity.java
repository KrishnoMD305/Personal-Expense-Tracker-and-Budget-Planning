package com.example.expensetracker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.Executor;

/**
 * Stage 5: the lock screen. A 4-6 digit PIN always works; fingerprint / face is an optional shortcut
 * (only offered when it is switched on in Settings and the device still has a strong biometric enrolled).
 * Wrong PINs are counted persistently and cause a growing lock-out ({@link PinPolicy}).
 */
public class LockActivity extends AppCompatActivity {
    private TextInputEditText pinInput;
    private TextView message;
    private Button unlockButton, biometricButton;
    private AppSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean biometricShown;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshLockout();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        settings = AppSettings.get(this);
        if (settings.hideContent()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        pinInput = findViewById(R.id.pinInput);
        message = findViewById(R.id.lockMessage);
        unlockButton = findViewById(R.id.unlockButton);
        biometricButton = findViewById(R.id.biometricButton);

        unlockButton.setOnClickListener(v -> submit());
        biometricButton.setOnClickListener(v -> showBiometricPrompt());
        pinInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        pinInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Convenience: check automatically once the stored PIN length has been typed.
                int len = settings.pinLength();
                if (len > 0 && s.length() == len && isInputEnabled()) submit();
            }
        });

        // Back must never skip the lock: it sends the app to the background instead.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean bio = biometricAvailable();
        biometricButton.setVisibility(bio ? android.view.View.VISIBLE : android.view.View.GONE);
        refreshLockout();
        if (bio && !biometricShown && !isLockedOut()) {
            biometricShown = true;
            showBiometricPrompt();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLock.onLockScreenClosed();
    }

    // ------------------------------------------------------------------ PIN

    private boolean isLockedOut() {
        return PinPolicy.remainingLockoutMillis(settings.lockedUntilMillis(), System.currentTimeMillis()) > 0;
    }

    private boolean isInputEnabled() {
        return pinInput.isEnabled();
    }

    private void submit() {
        if (isLockedOut()) {
            refreshLockout();
            return;
        }
        String pin = pinInput.getText() == null ? "" : pinInput.getText().toString();
        if (!PinPolicy.isValid(pin)) {
            message.setText(R.string.lock_enter_pin);
            return;
        }
        if (settings.checkPin(pin)) {
            settings.recordSuccess();
            unlock();
        } else {
            settings.recordFailure(System.currentTimeMillis());
            pinInput.setText("");
            if (isLockedOut()) {
                refreshLockout();
            } else {
                int left = PinPolicy.FREE_ATTEMPTS - settings.failedAttempts();
                message.setText(getString(R.string.lock_wrong_pin, Math.max(left, 0)));
            }
        }
    }

    private void unlock() {
        AppLock.markUnlocked();
        finish();
        overridePendingTransition(0, 0);
    }

    /** Disables the input while locked out and shows a live countdown. */
    private void refreshLockout() {
        handler.removeCallbacks(tick);
        long left = PinPolicy.remainingLockoutMillis(settings.lockedUntilMillis(), System.currentTimeMillis());
        if (left > 0) {
            pinInput.setEnabled(false);
            unlockButton.setEnabled(false);
            message.setText(getString(R.string.lock_try_again, (int) ((left + 999) / 1000)));
            handler.postDelayed(tick, 1000);
        } else {
            pinInput.setEnabled(true);
            unlockButton.setEnabled(true);
            message.setText(R.string.lock_enter_pin); // also clears a finished "try again in N s" countdown
        }
    }

    // ------------------------------------------------------------------ biometrics

    private boolean biometricAvailable() {
        return settings.biometricEnabled()
                && BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricPrompt() {
        if (!biometricAvailable()) return;
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                settings.recordSuccess();
                unlock();
            }
            // Errors, cancel and "Use PIN" simply leave the PIN field in place.
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_biometric_title))
                .setSubtitle(getString(R.string.lock_biometric_subtitle))
                .setNegativeButtonText(getString(R.string.lock_use_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        prompt.authenticate(info);
    }
}

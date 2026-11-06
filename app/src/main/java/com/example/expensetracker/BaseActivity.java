package com.example.expensetracker;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Stage 5: base class of every normal screen. It
 * <ul>
 *   <li>applies the user's theme before the layout is inflated, and re-creates the screen when the
 *       theme was changed while it sat in the back stack (so the whole app always matches);</li>
 *   <li>shows the PIN / biometric lock screen when the lock is on and the app has to be unlocked;</li>
 *   <li>hides the screen from "recent apps" and screenshots while the lock is on (can be switched off).</li>
 * </ul>
 */
public abstract class BaseActivity extends AppCompatActivity {
    private String appliedTheme;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appliedTheme = ThemeManager.current(this);
        setTheme(ThemeManager.styleFor(appliedTheme));
        super.onCreate(savedInstanceState);
        applyPrivacyFlag();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeManager.current(this).equals(appliedTheme)) {
            recreate(); // the theme changed in Settings: rebuild this screen with the new theme
            return;
        }
        applyPrivacyFlag();
        AppLock.showIfNeeded(this);
    }

    private void applyPrivacyFlag() {
        AppSettings s = AppSettings.get(this);
        if (s.lockEnabled() && s.hideContent()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}

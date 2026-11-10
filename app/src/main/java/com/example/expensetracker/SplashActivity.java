package com.example.expensetracker;

import com.example.expensetracker.databinding.ActivitySplashBinding;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Stage 5: animated welcome screen. It is the launcher activity, shows for {@link SplashConfig#DISPLAY_MS}
 * (well under the 2-second limit) and then opens the Dashboard and <b>finishes itself</b>. Because it is
 * removed from the back stack, going back from the Dashboard or returning from any other screen never
 * shows it again; it only appears on a fresh launch of the app.
 */
public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AnimatorSet intro;
    private ObjectAnimator fadeOut;
    private boolean navigated;

    private View root, logo, title;
    private LinearProgressIndicator progress;

    private final Runnable beginFadeOut = new Runnable() {
        @Override
        public void run() {
            fadeOut = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f);
            fadeOut.setDuration(SplashConfig.FADE_OUT_MS);
            fadeOut.start();
        }
    };
    private final Runnable navigate = this::goToDashboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        root = binding.splashRoot;
        logo = binding.splashLogo;
        title = binding.splashTitle;
        progress = binding.splashProgress;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (navigated) return;
        startSequence();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // If the user leaves during the welcome screen, stop the clock; it restarts when they come back.
        cancelSequence();
    }

    private void startSequence() {
        cancelSequence();
        root.setAlpha(1f);
        logo.setAlpha(0f);
        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);
        title.setAlpha(0f);
        title.setTranslationY(dp(24));
        progress.setProgressCompat(0, false);

        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f);
        ObjectAnimator logoX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.6f, 1f);
        ObjectAnimator logoY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.6f, 1f);
        logoX.setInterpolator(new OvershootInterpolator(1.4f));
        logoY.setInterpolator(new OvershootInterpolator(1.4f));
        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f);
        ObjectAnimator titleUp = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, dp(24), 0f);
        titleUp.setInterpolator(new DecelerateInterpolator());
        titleAlpha.setStartDelay(SplashConfig.TEXT_DELAY_MS);
        titleUp.setStartDelay(SplashConfig.TEXT_DELAY_MS);
        titleAlpha.setDuration(SplashConfig.TEXT_MS);
        titleUp.setDuration(SplashConfig.TEXT_MS);
        for (ObjectAnimator a : new ObjectAnimator[]{logoAlpha, logoX, logoY}) {
            a.setDuration(SplashConfig.LOGO_MS);
        }
        // Progress line fills smoothly over the whole display time.
        ObjectAnimator bar = ObjectAnimator.ofInt(progress, "progress", 0, progress.getMax());
        bar.setDuration(SplashConfig.DISPLAY_MS - SplashConfig.FADE_OUT_MS);
        bar.setInterpolator(new DecelerateInterpolator(1.2f));

        intro = new AnimatorSet();
        intro.playTogether(logoAlpha, logoX, logoY, titleAlpha, titleUp, bar);
        intro.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                // nothing: cancelSequence() resets the views on the next start
            }
        });
        intro.start();

        handler.postDelayed(beginFadeOut, SplashConfig.DISPLAY_MS - SplashConfig.FADE_OUT_MS);
        handler.postDelayed(navigate, SplashConfig.DISPLAY_MS);
    }

    private void cancelSequence() {
        handler.removeCallbacks(beginFadeOut);
        handler.removeCallbacks(navigate);
        if (intro != null) {
            intro.cancel();
            intro = null;
        }
        if (fadeOut != null) {
            fadeOut.cancel();
            fadeOut = null;
        }
    }

    private void goToDashboard() {
        if (navigated || isFinishing()) return;
        navigated = true;
        startActivity(new Intent(this, MainActivity.class));
        finish(); // leaves the back stack: the welcome screen cannot be reached again
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

package com.example.expensetracker;

/** Timing of the welcome screen. Kept separate (and pure) so a unit test can guarantee the 2-second limit. */
final class SplashConfig {
    /** Hard requirement: the welcome screen never stays longer than this. */
    static final long MAX_DISPLAY_MS = 2000L;
    /** Time after which the app navigates to the Dashboard. */
    static final long DISPLAY_MS = 1800L;
    /** Length of the fade-out that ends the welcome screen (it is part of {@link #DISPLAY_MS}). */
    static final long FADE_OUT_MS = 220L;
    static final long LOGO_MS = 650L;
    static final long TEXT_DELAY_MS = 250L;
    static final long TEXT_MS = 700L;

    private SplashConfig() {}
}

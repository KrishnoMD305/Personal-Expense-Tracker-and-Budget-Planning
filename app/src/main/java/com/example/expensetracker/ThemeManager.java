package com.example.expensetracker;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

/**
 * Stage 5: the four selectable themes. The selection is stored in {@link AppSettings}; every screen
 * calls {@link #apply(Activity)} before it inflates its layout ({@link BaseActivity} does this), so the
 * whole app uses the same theme, and the choice survives restarts.
 */
final class ThemeManager {
    /** Stable ids that are written to SharedPreferences and to backup files. Never rename them. */
    static final String WHITE = "white";
    static final String BLUE = "blue";
    static final String RED = "red";
    static final String DARK = "dark";
    static final String DEFAULT = WHITE;
    static final String[] ALL = {WHITE, BLUE, RED, DARK};

    private ThemeManager() {}

    /** Returns {@code id} if it is a known theme, otherwise the default (guards against corrupt prefs/backups). */
    static String normalize(String id) {
        if (id != null) {
            for (String t : ALL) {
                if (t.equals(id)) return t;
            }
        }
        return DEFAULT;
    }

    static String current(Context context) {
        return normalize(AppSettings.get(context).theme());
    }

    static void apply(Activity activity) {
        activity.setTheme(styleFor(current(activity)));
    }

    @StyleRes
    static int styleFor(String id) {
        switch (normalize(id)) {
            case BLUE:
                return R.style.Theme_ExpenseTracker_Blue;
            case RED:
                return R.style.Theme_ExpenseTracker_Red;
            case DARK:
                return R.style.Theme_ExpenseTracker_Dark;
            default:
                return R.style.Theme_ExpenseTracker_White;
        }
    }

    @StringRes
    static int nameRes(String id) {
        switch (normalize(id)) {
            case BLUE:
                return R.string.theme_blue;
            case RED:
                return R.string.theme_red;
            case DARK:
                return R.string.theme_dark;
            default:
                return R.string.theme_white;
        }
    }

    /** Colours used to draw the little preview tile in Settings (independent of the active theme). */
    @ColorRes
    static int previewBackground(String id) {
        switch (normalize(id)) {
            case BLUE:
                return R.color.theme_blue_bg;
            case RED:
                return R.color.theme_red_bg;
            case DARK:
                return R.color.theme_dark_bg;
            default:
                return R.color.theme_white_bg;
        }
    }

    @ColorRes
    static int previewBar(String id) {
        switch (normalize(id)) {
            case BLUE:
                return R.color.theme_blue_primary;
            case RED:
                return R.color.theme_red_primary;
            case DARK:
                return R.color.theme_dark_hero;
            default:
                return R.color.theme_white_primary;
        }
    }

    @ColorRes
    static int previewText(String id) {
        switch (normalize(id)) {
            case BLUE:
                return R.color.theme_blue_t1;
            case RED:
                return R.color.theme_red_t1;
            case DARK:
                return R.color.theme_dark_t1;
            default:
                return R.color.theme_white_t1;
        }
    }

    /**
     * Heading colour used in exported PDF reports (printed on white paper, so it must be dark enough
     * whatever the theme). Returned as ARGB.
     */
    static int pdfAccent(String id) {
        switch (normalize(id)) {
            case BLUE:
                return 0xFF1565C0;
            case RED:
                return 0xFFC62828;
            case DARK:
                return 0xFF00695C;
            default:
                return 0xFF455A64;
        }
    }
}

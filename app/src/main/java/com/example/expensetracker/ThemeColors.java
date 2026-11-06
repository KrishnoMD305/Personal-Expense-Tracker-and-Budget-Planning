package com.example.expensetracker;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;

/** Resolves a theme attribute (e.g. {@code R.attr.etIncome}) to the colour of the active theme. */
final class ThemeColors {
    private ThemeColors() {}

    @ColorInt
    static int get(Context context, @AttrRes int attr) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        // The attribute points at a colour resource (or is missing): fall back safely instead of crashing.
        if (tv.resourceId != 0) {
            return context.getResources().getColor(tv.resourceId, context.getTheme());
        }
        return 0xFF808080;
    }
}

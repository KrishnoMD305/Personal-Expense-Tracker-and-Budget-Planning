package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import java.util.Calendar;

final class DateField {
    private final Context context;
    private final Button button;
    private final int emptyTextRes;
    private long millis;

    DateField(Context context, Button button, long initialMillis, int emptyTextRes, View clearView) {
        this.context = context;
        this.button = button;
        this.emptyTextRes = emptyTextRes;
        set(initialMillis);
        button.setOnClickListener(v -> pick());
        if (clearView != null) clearView.setOnClickListener(v -> set(0));
    }
    long get() {
        return millis;
    }
    void set(long newMillis) {
        millis = newMillis > 0 ? MonthUtils.startOfDay(newMillis) : 0;
        button.setText(millis > 0 ? PlanDates.format(millis) : context.getString(emptyTextRes));
    }
    private void pick() {
        Calendar c = Calendar.getInstance();
        if (millis > 0) c.setTimeInMillis(millis);
        new DatePickerDialog(context, (view, y, m, d) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.clear();
            chosen.set(y, m, d, 0, 0, 0);
            set(chosen.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}

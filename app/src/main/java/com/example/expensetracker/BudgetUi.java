package com.example.expensetracker;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

final class BudgetUi {
    private BudgetUi() {}
    static int colorFor(Context c, BudgetStatus.Level level) {
        switch (level) {
            case EXCEEDED:
                return ContextCompat.getColor(c, R.color.expense);
            case WARNING:
                return ContextCompat.getColor(c, R.color.warning);
            case NORMAL:
                return ContextCompat.getColor(c, R.color.income);
            default:
                return ContextCompat.getColor(c, R.color.progress_track);
        }
    }

    static void bindProgress(LinearProgressIndicator bar, TextView percent, BudgetStatus s) {
        Context c = bar.getContext();
        int color = colorFor(c, s.level);
        bar.setIndicatorColor(color);
        bar.setProgressCompat(s.progress, false);
        if (s.hasBudget()) {
            percent.setVisibility(View.VISIBLE);
            percent.setText(s.percentText + "%");
            percent.setTextColor(color);
        } else {
            percent.setVisibility(View.GONE);
        }
    }

    static void bindBanner(TextView banner, List<BudgetAlerts.Alert> alerts) {
        if (alerts.isEmpty()) {
            banner.setVisibility(View.GONE);
            return;
        }
        boolean exceeded = false;
        StringBuilder sb = new StringBuilder();
        for (BudgetAlerts.Alert a : alerts) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(a.level == BudgetStatus.Level.EXCEEDED ? "⚠ " : "• ").append(a.message);
            if (a.level == BudgetStatus.Level.EXCEEDED) exceeded = true;
        }
        Context c = banner.getContext();
        banner.setText(sb.toString());
        banner.setTextColor(Color.parseColor("#212121"));
        banner.setBackgroundColor(ContextCompat.getColor(c, exceeded ? R.color.expense_light : R.color.warning_light));
        banner.setVisibility(View.VISIBLE);
    }

    static String joinMessages(List<BudgetAlerts.Alert> alerts) {
        StringBuilder sb = new StringBuilder();
        for (BudgetAlerts.Alert a : alerts) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(a.message);
        }
        return sb.toString();
    }
}

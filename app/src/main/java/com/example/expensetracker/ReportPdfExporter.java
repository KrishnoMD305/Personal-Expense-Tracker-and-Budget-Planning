package com.example.expensetracker;

import android.content.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stage 5: loads the data for one period and writes the PDF financial report. Call from a background thread. */
final class ReportPdfExporter {
    private ReportPdfExporter() {}

    /** Builds the report model for {@code period} from the database. */
    static List<PdfReportModel.Item> buildItems(Context context, ReportPeriod period, long now) {
        AppDatabase db = AppDatabase.getInstance(context);
        List<Transaction> window = db.transactionDao().getBetween(ReportCalculator.dataWindowStart(period), period.endMillis);
        List<Budget> budgets = db.budgetDao().getBetweenMonths(period.firstMonthKey(), period.lastMonthKey());
        ReportData report = ReportCalculator.build(period, window, budgets, now);

        BackupDao dao = db.backupDao();
        PdfReportBuilder.Input in = new PdfReportBuilder.Input();
        in.report = report;
        in.transactions = window;
        in.nowMillis = now;
        in.bills = dao.bills();
        in.rules = dao.rules();
        Map<Long, Long> saved = new HashMap<>();
        for (GoalContribution c : dao.contributions()) {
            Long cur = saved.get(c.goalId);
            saved.put(c.goalId, (cur == null ? 0L : cur) + c.amountPaisa);
        }
        List<GoalWithSaved> goals = new ArrayList<>();
        for (SavingsGoal g : dao.goals()) {
            GoalWithSaved gw = new GoalWithSaved();
            gw.goal = g;
            Long s = saved.get(g.id);
            gw.savedPaisa = s == null ? 0L : s;
            goals.add(gw);
        }
        in.goals = goals;
        return PdfReportBuilder.build(in);
    }

    static void write(Context context, ReportPeriod period, OutputStream out) throws IOException {
        long now = System.currentTimeMillis();
        List<PdfReportModel.Item> items = buildItems(context, period, now);
        String generated = "Generated on " + new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(new Date(now));
        new PdfReportRenderer(ThemeManager.pdfAccent(ThemeManager.current(context)))
                .render(items, PdfReportBuilder.periodLabel(period), generated, out);
    }
}

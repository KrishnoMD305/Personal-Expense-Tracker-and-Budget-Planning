package com.example.expensetracker;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Glue between Room and {@link ReportCalculator}. For one {@link ReportPeriod} it observes the
 * transactions and budgets that matter, and re-computes the whole report whenever either table
 * changes (an edit, delete, undo or new budget). The heavy lifting runs on a background thread;
 * results arrive on the main thread.
 *
 * <p>To change the period, create a new LiveData with {@link #observe} and stop observing the
 * old one. A value of {@code null} means the report could not be built.
 */
public final class ReportsRepository {
    private static final String TAG = "ReportsRepository";
    private static final ExecutorService COMPUTE = Executors.newSingleThreadExecutor();

    private ReportsRepository() {}

    public static LiveData<ReportData> observe(Context context, final ReportPeriod period) {
        AppDatabase db = AppDatabase.getInstance(context);
        final LiveData<List<Transaction>> transactions = db.transactionDao()
                .observeBetween(ReportCalculator.dataWindowStart(period), period.endMillis);
        final LiveData<List<Budget>> budgets = db.budgetDao()
                .observeBetweenMonths(period.firstMonthKey(), period.lastMonthKey());

        final MediatorLiveData<ReportData> out = new MediatorLiveData<>();
        final AtomicLong latest = new AtomicLong();
        final Runnable combine = () -> {
            final List<Transaction> t = transactions.getValue();
            final List<Budget> b = budgets.getValue();
            if (t == null || b == null) return; // wait until both tables have reported
            final long ticket = latest.incrementAndGet();
            COMPUTE.execute(() -> {
                if (ticket != latest.get()) return; // a newer change already superseded this one
                ReportData result;
                try {
                    result = ReportCalculator.build(period, t, b, System.currentTimeMillis());
                } catch (RuntimeException e) {
                    Log.e(TAG, "Could not build report for " + period, e);
                    result = null;
                }
                if (ticket == latest.get()) out.postValue(result);
            });
        };
        out.addSource(transactions, v -> combine.run());
        out.addSource(budgets, v -> combine.run());
        return out;
    }
}

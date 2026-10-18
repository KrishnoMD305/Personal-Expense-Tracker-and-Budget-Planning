package com.example.expensetracker;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ReportsRepository {
    private static final String TAG = "ReportsRepository";
    private static final ExecutorService COMPUTE = Executors.newSingleThreadExecutor();
    private ReportsRepository() {}
    public static LiveData<ReportData> observe(Context context, final ReportPeriod period) {
        AppDatabase db = AppDatabase.getInstance(context);
        final LiveData<List<Transaction>> transactions = db.transactionDao().observeBetween(ReportCalculator.dataWindowStart(period), period.endMillis);
        final LiveData<List<Budget>> budgets = db.budgetDao().observeBetweenMonths(period.firstMonthKey(), period.lastMonthKey());

        final MediatorLiveData<ReportData> out = new MediatorLiveData<>();
        final AtomicLong latest = new AtomicLong();
        final Runnable combine = () -> {
            final List<Transaction> t = transactions.getValue();
            final List<Budget> b = budgets.getValue();
            if (t == null || b == null) return;
            final long ticket = latest.incrementAndGet();
            COMPUTE.execute(() -> {
                if (ticket != latest.get()) return;
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

package com.example.expensetracker;

import android.os.Handler;
import android.os.Looper;

public interface PlanCallback {
    void onResult(String errorOrNull);
    Handler MAIN = new Handler(Looper.getMainLooper());

    static void run(PlanCallback callback, java.util.concurrent.Callable<String> work, String failureMessage) {
        AppDatabase.WRITE_EXECUTOR.execute(() -> {
            String error;
            try {
                error = work.call();
            } catch (Exception e) {
                error = failureMessage;
            }
            final String result = error;
            if (callback != null) MAIN.post(() -> callback.onResult(result));
        });
    }
}

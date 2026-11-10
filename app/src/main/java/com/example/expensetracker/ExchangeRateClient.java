package com.example.expensetracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stage 7: the app's one plain REST call. {@code GET https://open.er-api.com/v6/latest/USD}, read with
 * {@link HttpURLConnection} (no extra library), parsed with {@code org.json} by {@link ExchangeRates}.
 *
 * <p>Runs on a background thread; the last good answer is cached in SharedPreferences so the dialog still shows
 * rates (marked as saved) when the device is offline or the service is down.
 */
final class ExchangeRateClient {
    static final String URL = "https://open.er-api.com/v6/latest/USD";
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BYTES = 512 * 1024; // the real answer is ~4 KB; refuse anything absurd
    private static final String PREFS = "exchange_rates";
    private static final String K_JSON = "json";
    private static final String K_FETCHED = "fetched_at";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ExchangeRateClient() {}

    /** The outcome shown to the user. */
    static final class Result {
        final ExchangeRates rates;   // null when nothing at all is available
        final boolean fromCache;     // true = the network call failed and these are saved rates
        final long fetchedMillis;    // when the numbers were downloaded
        final String error;          // why the live call failed (set whenever fromCache or rates == null)

        Result(ExchangeRates rates, boolean fromCache, long fetchedMillis, String error) {
            this.rates = rates;
            this.fromCache = fromCache;
            this.fetchedMillis = fetchedMillis;
            this.error = error;
        }
    }

    interface Callback {
        /** Called on the thread passed to {@link #load}'s handler, i.e. the main thread. */
        void onResult(Result result);
    }

    /** Fetches in the background and calls back on the main thread. */
    static void load(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        IO.execute(() -> {
            final Result r = fetch(app);
            main.post(() -> callback.onResult(r));
        });
    }

    /** Blocking version (background thread only). */
    static Result fetch(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String error;
        try {
            String body = httpGet(URL);
            ExchangeRates rates = ExchangeRates.parse(body); // validate BEFORE caching
            long now = System.currentTimeMillis();
            prefs.edit().putString(K_JSON, body).putLong(K_FETCHED, now).apply();
            return new Result(rates, false, now, null);
        } catch (IOException e) {
            error = "No connection to the exchange-rate service (" + String.valueOf(e.getMessage()) + ").";
        } catch (JSONException e) {
            error = "The exchange-rate service sent an answer this app cannot read (" + e.getMessage() + ").";
        }
        String cached = prefs.getString(K_JSON, null);
        if (cached != null) {
            try {
                return new Result(ExchangeRates.parse(cached), true, prefs.getLong(K_FETCHED, 0L), error);
            } catch (JSONException ignored) {
                // a damaged cache is treated as no cache
            }
        }
        return new Result(null, false, 0L, error);
    }

    private static String httpGet(String address) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(TIMEOUT_MILLIS);
            c.setReadTimeout(TIMEOUT_MILLIS);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > MAX_BYTES) throw new IOException("answer too large");
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}

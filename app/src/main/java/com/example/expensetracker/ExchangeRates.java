package com.example.expensetracker;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stage 7: the parsed answer of the exchange-rate REST API and the arithmetic on it. Pure Java ({@code org.json}
 * only), so it is unit-tested on the JVM. The HTTP call itself is in {@link ExchangeRateClient}.
 *
 * <p>API: {@code GET https://open.er-api.com/v6/latest/USD} (free, no key). Example answer:
 * <pre>
 * { "result": "success", "time_last_update_unix": 1762473000, "base_code": "USD",
 *   "rates": { "USD": 1, "BDT": 122.4, "EUR": 0.86, ... } }
 * </pre>
 */
final class ExchangeRates {
    /** Currencies shown in the dialog (the user's money is in taka, so every line is "1 X = ... BDT"). */
    static final String[] SHOWN = {"USD", "EUR", "GBP", "INR", "SAR", "AED", "JPY"};
    static final String HOME = "BDT";

    /** Units of each currency that one US dollar buys (the API's base is USD). */
    final Map<String, Double> perUsd;
    /** When the provider last updated the rates (epoch millis; 0 if unknown). */
    final long updatedMillis;

    ExchangeRates(Map<String, Double> perUsd, long updatedMillis) {
        this.perUsd = perUsd;
        this.updatedMillis = updatedMillis;
    }

    /** Parses the API answer. Throws when it is not a successful answer that contains the home currency. */
    static ExchangeRates parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) throw new JSONException("empty answer");
        JSONObject root = new JSONObject(json);
        String result = root.optString("result", "");
        if (!"success".equals(result)) {
            throw new JSONException("the service reported \"" + (result.isEmpty() ? "no result" : result) + "\"");
        }
        JSONObject rates = root.getJSONObject("rates");
        Map<String, Double> map = new LinkedHashMap<>();
        Iterator<String> keys = rates.keys();
        while (keys.hasNext()) {
            String code = keys.next();
            double v = rates.optDouble(code, Double.NaN);
            if (!Double.isNaN(v) && !Double.isInfinite(v) && v > 0) map.put(code, v);
        }
        if (!map.containsKey(HOME)) throw new JSONException("no rate for " + HOME);
        long updated = root.optLong("time_last_update_unix", 0L) * 1000L;
        return new ExchangeRates(map, updated);
    }

    boolean has(String code) {
        return perUsd.containsKey(code);
    }

    /** How many taka one unit of {@code code} costs, or NaN when the currency is unknown. */
    double homePer(String code) {
        Double home = perUsd.get(HOME);
        Double other = perUsd.get(code);
        if (home == null || other == null) return Double.NaN;
        return home / other;
    }

    /** A taka amount expressed in {@code code} (NaN when unknown). */
    double convertFromHome(double homeAmount, String code) {
        double per = homePer(code);
        return Double.isNaN(per) ? Double.NaN : homeAmount / per;
    }

    /** "122.40" - two decimals, always a dot (the screen shows money, not locale-formatted prose). */
    static String plain(double v) {
        return Double.isNaN(v) ? "-" : String.format(Locale.US, "%,.2f", v);
    }
}

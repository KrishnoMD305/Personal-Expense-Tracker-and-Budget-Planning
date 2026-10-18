package com.example.expensetracker;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class ReportFormat {
    public static final String NOT_AVAILABLE = "N/A";
    public static final String DASH = "\u2014"; // em dash
    private ReportFormat() {}
    public static String tenths(long tenths) {
        return BigDecimal.valueOf(tenths, 1).stripTrailingZeros().toPlainString();
    }
    public static String percent(Long tenths) {
        return tenths == null ? NOT_AVAILABLE : tenths(tenths) + "%";
    }
    public static String percentMagnitude(Long tenths) {
        return tenths == null ? NOT_AVAILABLE : tenths(Math.abs(tenths)) + "%";
    }
    public static String signedPercent(Long tenths) {
        if (tenths == null) return DASH;
        return (tenths > 0 ? "+" : "") + tenths(tenths) + "%";
    }
    public static String signedMoney(long paisa) {
        return (paisa > 0 ? "+" : "") + MoneyUtils.format(paisa);
    }
    public static String pointsChange(Long currentTenths, Long previousTenths) {
        if (currentTenths == null || previousTenths == null) return DASH;
        long d = currentTenths - previousTenths;
        return (d > 0 ? "+" : "") + tenths(d) + " pts";
    }

    public static String compactAmount(double taka) {
        double a = Math.abs(taka);
        String sign = taka < 0 ? "-" : "";
        DecimalFormat df = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
        if (a >= 999_950d) return sign + df.format(a / 1_000_000d) + "M";
        if (a >= 999.95d) return sign + df.format(a / 1_000d) + "k";
        return sign + df.format(a);
    }
}

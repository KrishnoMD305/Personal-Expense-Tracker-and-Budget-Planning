package com.example.expensetracker;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Text formatting for report figures. Pure Java; digits are always ASCII like {@link MoneyUtils}. */
public final class ReportFormat {
    /** Shown where a value cannot be computed (e.g. a savings rate when there was no income). */
    public static final String NOT_AVAILABLE = "N/A";
    public static final String DASH = "\u2014"; // em dash

    private ReportFormat() {}

    /** 185 -> "18.5", 200 -> "20", 0 -> "0", -25 -> "-2.5". */
    public static String tenths(long tenths) {
        return BigDecimal.valueOf(tenths, 1).stripTrailingZeros().toPlainString();
    }

    /** "18.5%", or "N/A" when the value is null. */
    public static String percent(Long tenths) {
        return tenths == null ? NOT_AVAILABLE : tenths(tenths) + "%";
    }

    /** Absolute value as a percentage: -125 -> "12.5%". Used in sentences that already say up/down. */
    public static String percentMagnitude(Long tenths) {
        return tenths == null ? NOT_AVAILABLE : tenths(Math.abs(tenths)) + "%";
    }

    /** "+12.5%", "-8%", "0%"; an em dash when there is nothing to compare with. */
    public static String signedPercent(Long tenths) {
        if (tenths == null) return DASH;
        return (tenths > 0 ? "+" : "") + tenths(tenths) + "%";
    }

    /** "+৳ 500.00", "-৳ 500.00", "৳ 0.00". */
    public static String signedMoney(long paisa) {
        return (paisa > 0 ? "+" : "") + MoneyUtils.format(paisa);
    }

    /** Difference of two rates in percentage points: "+2.5 pts"; an em dash if either is unknown. */
    public static String pointsChange(Long currentTenths, Long previousTenths) {
        if (currentTenths == null || previousTenths == null) return DASH;
        long d = currentTenths - previousTenths;
        return (d > 0 ? "+" : "") + tenths(d) + " pts";
    }

    /** Short axis label for an amount in taka: 950 -> "950", 12500 -> "12.5k", 2300000 -> "2.3M". */
    public static String compactAmount(double taka) {
        double a = Math.abs(taka);
        String sign = taka < 0 ? "-" : "";
        DecimalFormat df = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
        if (a >= 999_950d) return sign + df.format(a / 1_000_000d) + "M";
        if (a >= 999.95d) return sign + df.format(a / 1_000d) + "k";
        return sign + df.format(a);
    }
}

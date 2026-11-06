package com.example.expensetracker;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Exact money handling: amounts are parsed with BigDecimal and stored as whole paisa. */
public final class MoneyUtils {
    public static final String SYMBOL = "\u09F3"; // ৳
    public static final long MAX_PAISA = 99_999_999_999L; // 999,999,999.99 BDT

    private MoneyUtils() {}

    public static class InvalidAmountException extends Exception {
        public InvalidAmountException(String message) {
            super(message);
        }
    }

    /** Parses user input such as "1,250.50" into paisa. Throws with a user-facing message. */
    public static long parseToPaisa(String input) throws InvalidAmountException {
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidAmountException("Amount is required");
        }
        String cleaned = input.trim().replace(",", "");
        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("Enter a valid number");
        }
        if (value.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than 0");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Use at most 2 decimal places");
        }
        long paisa;
        try {
            paisa = value.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidAmountException("Amount is too large");
        }
        if (paisa > MAX_PAISA) {
            throw new InvalidAmountException("Amount is too large");
        }
        return paisa;
    }

    /** Formats paisa as "৳ 1,250.50" (negative: "-৳ 300.00"). */
    public static String format(long paisa) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String body = SYMBOL + " " + df.format(BigDecimal.valueOf(Math.abs(paisa), 2));
        return paisa < 0 ? "-" + body : body;
    }

    /** Stage 5: "1250.50" - exact, no symbol, no grouping (what spreadsheets want). Negative: "-300.00". */
    public static String plain(long paisa) {
        BigDecimal v = BigDecimal.valueOf(paisa, 2);
        return v.setScale(2).toPlainString();
    }

    /** Stage 5: "1,250.50" - exact, with thousands separators but no currency symbol (used in PDF tables). */
    public static String grouped(long paisa) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        String body = df.format(BigDecimal.valueOf(Math.abs(paisa), 2));
        return paisa < 0 ? "-" + body : body;
    }

    /** Stage 5: "BDT 1,250.50" - used in PDF reports, where the taka sign may be missing from a device font. */
    public static String formatBdt(long paisa) {
        return paisa < 0 ? "-BDT " + grouped(-paisa) : "BDT " + grouped(paisa);
    }
}

package com.example.expensetracker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Helpers for "yyyy-MM" month keys. All maths uses the device's current time zone. */
public final class MonthUtils {
    private MonthUtils() {}

    public static boolean isValidKey(String key) {
        if (key == null || key.length() != 7 || key.charAt(4) != '-') return false;
        for (int i = 0; i < 7; i++) {
            if (i != 4 && !(key.charAt(i) >= '0' && key.charAt(i) <= '9')) return false;
        }
        int month = Integer.parseInt(key.substring(5));
        return month >= 1 && month <= 12 && Integer.parseInt(key.substring(0, 4)) >= 1970;
    }

    public static String keyOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return key(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }

    public static String current() {
        return keyOf(System.currentTimeMillis());
    }

    private static String key(int year, int zeroBasedMonth) {
        return String.format(Locale.US, "%04d-%02d", year, zeroBasedMonth + 1);
    }

    private static Calendar startCalendar(String key) {
        if (!isValidKey(key)) throw new IllegalArgumentException("Invalid month key: " + key);
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(key.substring(0, 4)), Integer.parseInt(key.substring(5)) - 1, 1, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** First instant of the month (inclusive). */
    public static long startMillis(String key) {
        return startCalendar(key).getTimeInMillis();
    }

    /** First instant of the following month (exclusive end of this month). */
    public static long endMillis(String key) {
        Calendar c = startCalendar(key);
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }

    /** Returns the key {@code delta} months after (or before, if negative) {@code key}. */
    public static String shift(String key, int delta) {
        Calendar c = startCalendar(key);
        c.add(Calendar.MONTH, delta);
        return key(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }

    /** "October 2026" in the device language. */
    public static String label(String key) {
        SimpleDateFormat f = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        f.setTimeZone(TimeZone.getDefault());
        return f.format(startCalendar(key).getTime());
    }

    // ------------------------------------------------------------------ Stage 3 additions

    /** "Oct 26" in the device language; used for chart axes. */
    public static String shortLabel(String key) {
        SimpleDateFormat f = new SimpleDateFormat("MMM yy", Locale.getDefault());
        f.setTimeZone(TimeZone.getDefault());
        return f.format(startCalendar(key).getTime());
    }

    /** Number of months from {@code from} to {@code to} (negative if {@code to} is earlier). */
    public static int monthsBetween(String from, String to) {
        if (!isValidKey(from) || !isValidKey(to)) {
            throw new IllegalArgumentException("Invalid month key: " + from + " / " + to);
        }
        int a = Integer.parseInt(from.substring(0, 4)) * 12 + Integer.parseInt(from.substring(5));
        int b = Integer.parseInt(to.substring(0, 4)) * 12 + Integer.parseInt(to.substring(5));
        return b - a;
    }

    /** First instant (00:00 local time) of the day containing {@code millis}. */
    public static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Moves {@code millis} by whole calendar days (daylight-saving safe), keeping the local time of day. */
    public static long addDays(long millis, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }
}

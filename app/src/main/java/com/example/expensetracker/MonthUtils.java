package com.example.expensetracker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

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
    public static long startMillis(String key) {
        return startCalendar(key).getTimeInMillis();
    }
    public static long endMillis(String key) {
        Calendar c = startCalendar(key);
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
    }
    public static String shift(String key, int delta) {
        Calendar c = startCalendar(key);
        c.add(Calendar.MONTH, delta);
        return key(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }
    public static String label(String key) {
        SimpleDateFormat f = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        f.setTimeZone(TimeZone.getDefault());
        return f.format(startCalendar(key).getTime());
    }
}

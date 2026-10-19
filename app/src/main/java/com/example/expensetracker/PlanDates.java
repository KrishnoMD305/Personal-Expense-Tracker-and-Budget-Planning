package com.example.expensetracker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class PlanDates {
    private static final double DAY_MILLIS = 86_400_000.0;

    private PlanDates() {}

    public static long today() {
        return MonthUtils.startOfDay(System.currentTimeMillis());
    }
    public static int daysBetween(long fromDay, long toDay) {
        return (int) Math.round((toDay - fromDay) / DAY_MILLIS);
    }
    public static String format(long millis) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new java.util.Date(millis));
    }
    public static long nextHourOfDay(long now, int hour) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }
}

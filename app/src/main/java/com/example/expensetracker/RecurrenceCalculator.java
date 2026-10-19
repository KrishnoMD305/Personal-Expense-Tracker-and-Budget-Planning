package com.example.expensetracker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class RecurrenceCalculator {
    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";
    public static final String[] ALL = {DAILY, WEEKLY, MONTHLY};

    private RecurrenceCalculator() {}

    public static boolean isValidFrequency(String f) {
        return DAILY.equals(f) || WEEKLY.equals(f) || MONTHLY.equals(f);
    }

    public static long occurrence(long startMillis, String frequency, int k) {
        if (!isValidFrequency(frequency)) throw new IllegalArgumentException("Unknown frequency: " + frequency);
        if (k < 0) throw new IllegalArgumentException("k must be >= 0");
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(MonthUtils.startOfDay(startMillis));
        if (DAILY.equals(frequency)) c.add(Calendar.DAY_OF_MONTH, k);
        else if (WEEKLY.equals(frequency)) c.add(Calendar.DAY_OF_MONTH, 7 * k);
        else c.add(Calendar.MONTH, k);
        return c.getTimeInMillis();
    }

    public static List<Long> occurrencesBetween(long startMillis, long endMillis, String frequency,
                                                long from, long to, int limit) {
        List<Long> out = new ArrayList<>();
        if (limit <= 0 || to < from) return out;
        long start = MonthUtils.startOfDay(startMillis);
        long end = endMillis > 0 ? MonthUtils.startOfDay(endMillis) : Long.MAX_VALUE;
        long upper = Math.min(to, end);
        int k = firstIndexAtOrAfter(start, frequency, from);
        while (out.size() < limit) {
            long occ = occurrence(start, frequency, k++);
            if (occ > upper) break;
            if (occ >= from) out.add(occ);
        }
        return out;
    }

    public static long nextOccurrence(long startMillis, long endMillis, String frequency, long from) {
        List<Long> one = occurrencesBetween(startMillis, endMillis, frequency, from, Long.MAX_VALUE / 4, 1);
        return one.isEmpty() ? -1 : one.get(0);
    }

    private static int firstIndexAtOrAfter(long start, String frequency, long from) {
        if (from <= start) return 0;
        int guess;
        if (MONTHLY.equals(frequency)) {
            Calendar a = Calendar.getInstance();
            a.setTimeInMillis(start);
            Calendar b = Calendar.getInstance();
            b.setTimeInMillis(from);
            guess = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12
                    + (b.get(Calendar.MONTH) - a.get(Calendar.MONTH));
        } else {
            int days = PlanDates.daysBetween(start, MonthUtils.startOfDay(from));
            guess = DAILY.equals(frequency) ? days : days / 7;
        }
        int k = Math.max(0, guess - 2);
        while (k > 0 && occurrence(start, frequency, k) >= from) k--;
        return k;
    }
}

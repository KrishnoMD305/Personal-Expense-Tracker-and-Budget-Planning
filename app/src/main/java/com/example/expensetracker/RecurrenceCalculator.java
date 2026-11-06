package com.example.expensetracker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Pure schedule maths for recurring transactions.
 *
 * <p>The k-th occurrence is always computed from the start date (start + k days / weeks / months),
 * never from the previous occurrence, so a monthly rule that starts on the 31st gives
 * Jan 31, Feb 28/29, Mar 31, Apr 30 ... and never drifts to the 28th.
 */
public final class RecurrenceCalculator {
    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";
    /** Order used by the frequency spinner. */
    public static final String[] ALL = {DAILY, WEEKLY, MONTHLY};

    private RecurrenceCalculator() {}

    public static boolean isValidFrequency(String f) {
        return DAILY.equals(f) || WEEKLY.equals(f) || MONTHLY.equals(f);
    }

    /** Start of the day of the k-th occurrence (k = 0 is the start date itself). */
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

    /**
     * Occurrence days d with {@code from <= d <= to} that are also on/before {@code endMillis}
     * (0 = no end), in ascending order, at most {@code limit} of them.
     */
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

    /** First occurrence on/after {@code from} that is not past the end date; -1 if there is none. */
    public static long nextOccurrence(long startMillis, long endMillis, String frequency, long from) {
        List<Long> one = occurrencesBetween(startMillis, endMillis, frequency, from, Long.MAX_VALUE / 4, 1);
        return one.isEmpty() ? -1 : one.get(0);
    }

    /** A cheap lower bound for the first index k whose occurrence is >= from (never too high). */
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
        int k = Math.max(0, guess - 2); // back off a little, the loop walks forward to the exact index
        while (k > 0 && occurrence(start, frequency, k) >= from) k--;
        return k;
    }
}

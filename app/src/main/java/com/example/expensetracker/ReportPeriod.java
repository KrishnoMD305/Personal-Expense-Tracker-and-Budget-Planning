package com.example.expensetracker;

/**
 * The window a report covers: either one calendar month or a custom range of whole days.
 *
 * <p>The window is half-open, {@code [startMillis, endMillis)}, in the device time zone. Because
 * transactions are stored at local midnight, an inclusive end DATE chosen by the user simply
 * becomes the start of the following day here, so the last day is never lost and the next day
 * is never included. Pure Java (no Android dependencies).
 */
public final class ReportPeriod {
    public enum Type { MONTH, CUSTOM }

    /** How far the month navigator may move from today; keeps month keys valid. */
    public static final int MAX_MONTHS_AWAY = 600;

    private static final double DAY_MS = 24d * 60 * 60 * 1000;

    public final Type type;
    /** Inclusive start (local midnight). */
    public final long startMillis;
    /** Exclusive end (local midnight of the day after the last day). */
    public final long endMillis;
    /** "yyyy-MM" for {@link Type#MONTH}; null for custom ranges. */
    public final String monthKey;
    private final int dayCount;

    private ReportPeriod(Type type, long startMillis, long endMillis, String monthKey) {
        this.type = type;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.monthKey = monthKey;
        // Both ends are local midnights, so the difference is a whole number of days (give or take a
        // daylight-saving hour, which rounding absorbs).
        this.dayCount = (int) Math.max(1, Math.round((endMillis - startMillis) / DAY_MS));
    }

    /** One whole calendar month. */
    public static ReportPeriod month(String monthKey) {
        return new ReportPeriod(Type.MONTH, MonthUtils.startMillis(monthKey),
                MonthUtils.endMillis(monthKey), monthKey);
    }

    /**
     * A custom range from the day containing {@code firstDayMillis} to the day containing
     * {@code lastDayMillis}, both INCLUDED.
     *
     * @throws IllegalArgumentException if the last day is before the first day or the range starts
     *                                  before the supported 1970 minimum
     */
    public static ReportPeriod custom(long firstDayMillis, long lastDayMillis) {
        long start = MonthUtils.startOfDay(firstDayMillis);
        long lastDayStart = MonthUtils.startOfDay(lastDayMillis);
        if (lastDayStart < start) {
            throw new IllegalArgumentException("End date is before start date");
        }
        if (!MonthUtils.isValidKey(MonthUtils.keyOf(start))) {
            throw new IllegalArgumentException("Date is outside the supported range");
        }
        long end = MonthUtils.startOfDay(MonthUtils.addDays(lastDayStart, 1));
        return new ReportPeriod(Type.CUSTOM, start, end, null);
    }

    /** Number of calendar days covered (at least 1). */
    public int dayCount() {
        return dayCount;
    }

    /**
     * The period this one is compared with: the previous calendar month for a month, or for a
     * custom range the block of days of equal length immediately before it.
     */
    public ReportPeriod previous() {
        if (type == Type.MONTH) {
            return month(MonthUtils.shift(monthKey, -1));
        }
        long prevStart = MonthUtils.startOfDay(MonthUtils.addDays(startMillis, -dayCount));
        return new ReportPeriod(Type.CUSTOM, prevStart, startMillis, null);
    }

    /** Month key of the first day in the period. */
    public String firstMonthKey() {
        return MonthUtils.keyOf(startMillis);
    }

    /** Month key of the last day in the period. */
    public String lastMonthKey() {
        return MonthUtils.keyOf(endMillis - 1);
    }

    /**
     * True when the period starts on the 1st of a month and ends on the last day of a month, so that
     * monthly budgets (which are per calendar month) can be compared with it exactly.
     */
    public boolean isWholeCalendarMonths() {
        if (type == Type.MONTH) return true;
        String first = firstMonthKey();
        String last = lastMonthKey();
        if (!MonthUtils.isValidKey(first) || !MonthUtils.isValidKey(last)) return false;
        return MonthUtils.startMillis(first) == startMillis && MonthUtils.endMillis(last) == endMillis;
    }

    /** True if {@code millis} lies inside this period. */
    public boolean contains(long millis) {
        return millis >= startMillis && millis < endMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportPeriod)) return false;
        ReportPeriod p = (ReportPeriod) o;
        return type == p.type && startMillis == p.startMillis && endMillis == p.endMillis;
    }

    @Override
    public int hashCode() {
        long h = startMillis * 31 + endMillis;
        return type.hashCode() * 31 + (int) (h ^ (h >>> 32));
    }

    @Override
    public String toString() {
        return "ReportPeriod{" + type + ", " + startMillis + " .. " + endMillis + "}";
    }
}

package com.example.expensetracker;

public final class ReportPeriod {
    public enum Type { MONTH, CUSTOM }
    public static final int MAX_MONTHS_AWAY = 600;

    private static final double DAY_MS = 24d * 60 * 60 * 1000;

    public final Type type;
    public final long startMillis;
    public final long endMillis;
    public final String monthKey;
    private final int dayCount;

    private ReportPeriod(Type type, long startMillis, long endMillis, String monthKey) {
        this.type = type;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.monthKey = monthKey;
        this.dayCount = (int) Math.max(1, Math.round((endMillis - startMillis) / DAY_MS));
    }

    public static ReportPeriod month(String monthKey) {
        return new ReportPeriod(Type.MONTH, MonthUtils.startMillis(monthKey),
                MonthUtils.endMillis(monthKey), monthKey);
    }

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

    public int dayCount() {
        return dayCount;
    }

    public ReportPeriod previous() {
        if (type == Type.MONTH) {
            return month(MonthUtils.shift(monthKey, -1));
        }
        long prevStart = MonthUtils.startOfDay(MonthUtils.addDays(startMillis, -dayCount));
        return new ReportPeriod(Type.CUSTOM, prevStart, startMillis, null);
    }

    public String firstMonthKey() {
        return MonthUtils.keyOf(startMillis);
    }

    public String lastMonthKey() {
        return MonthUtils.keyOf(endMillis - 1);
    }

    public boolean isWholeCalendarMonths() {
        if (type == Type.MONTH) return true;
        String first = firstMonthKey();
        String last = lastMonthKey();
        if (!MonthUtils.isValidKey(first) || !MonthUtils.isValidKey(last)) return false;
        return MonthUtils.startMillis(first) == startMillis && MonthUtils.endMillis(last) == endMillis;
    }

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

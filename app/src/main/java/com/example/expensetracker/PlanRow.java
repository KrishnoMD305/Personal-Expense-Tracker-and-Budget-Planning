package com.example.expensetracker;
import java.util.Objects;

public final class PlanRow {
    public static final int HEADER = 0;
    public static final int ITEM = 1;
    public static final int NO_PROGRESS = -1;

    public final int type;
    public final String key;
    public final String title;
    public final String trailing;
    public final int trailingColor;
    public final String line1;
    public final String line2;
    public final int progress;
    public final int progressColor;
    public final boolean dim;
    public final Object tag;

    private PlanRow(int type, String key, String title, String trailing, int trailingColor, String line1,
                    String line2, int progress, int progressColor, boolean dim, Object tag) {
        this.type = type;
        this.key = key;
        this.title = title;
        this.trailing = trailing;
        this.trailingColor = trailingColor;
        this.line1 = line1;
        this.line2 = line2;
        this.progress = progress;
        this.progressColor = progressColor;
        this.dim = dim;
        this.tag = tag;
    }

    public static PlanRow header(String key, String title) {
        return new PlanRow(HEADER, key, title, null, 0, null, null, NO_PROGRESS, 0, false, null);
    }

    public static PlanRow item(String key, String title, String trailing, int trailingColor, String line1,
                               String line2, int progress, int progressColor, boolean dim, Object tag) {
        return new PlanRow(ITEM, key, title, trailing, trailingColor, line1, line2, progress, progressColor, dim, tag);
    }

    boolean sameContent(PlanRow o) {
        return type == o.type && Objects.equals(title, o.title) && Objects.equals(trailing, o.trailing)
                && trailingColor == o.trailingColor && Objects.equals(line1, o.line1)
                && Objects.equals(line2, o.line2) && progress == o.progress
                && progressColor == o.progressColor && dim == o.dim;
    }
}

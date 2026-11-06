package com.example.expensetracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 5: layout model of the PDF report - a flat list of {@link Item}s (title, headings, text lines, table
 * rows ...) with fixed heights - and the paginator that splits it into pages. It contains no Android code, so the
 * page-breaking rules (headings are never stranded at the bottom of a page, a table that continues on the next
 * page repeats its header) are unit-tested on the JVM. {@link PdfReportRenderer} only draws what is decided here.
 */
final class PdfReportModel {
    static final float PAGE_WIDTH = 595f;   // A4 in PostScript points
    static final float PAGE_HEIGHT = 842f;
    static final float MARGIN_X = 40f;
    static final float TOP = 58f;           // below the running header
    static final float BOTTOM = 50f;        // above the footer
    static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X;
    static final float CONTENT_HEIGHT = PAGE_HEIGHT - TOP - BOTTOM;

    enum Kind { TITLE, SUBTITLE, HEADING, TEXT, BULLET, TABLE_HEADER, TABLE_ROW, SPACE }

    static final class Item {
        final Kind kind;
        /** Cell texts (one entry for non-table items). */
        final String[] cells;
        /** Relative column widths (table items). */
        final float[] weights;
        /** Right-aligned columns (table items). */
        final boolean[] right;
        /** 0..1 width of a small bar drawn under a table row, or -1 for none. */
        final float bar;
        final float height;
        /** Keep this item on the same page as the one after it (headings, table headers). */
        final boolean keepWithNext;
        /** For TABLE_ROW: the header to repeat when the table continues on a new page. */
        Item header;
        /** True if the row is bold (totals). */
        final boolean bold;

        Item(Kind kind, String[] cells, float[] weights, boolean[] right, float bar, float height,
             boolean keepWithNext, boolean bold) {
            this.kind = kind;
            this.cells = cells;
            this.weights = weights;
            this.right = right;
            this.bar = bar;
            this.height = height;
            this.keepWithNext = keepWithNext;
            this.bold = bold;
        }

        String text() {
            return cells.length == 0 ? "" : cells[0];
        }
    }

    // ------------------------------------------------------------------ builders

    static Item title(String text) {
        return new Item(Kind.TITLE, new String[]{text}, null, null, -1f, 30f, false, true);
    }

    static Item subtitle(String text) {
        return new Item(Kind.SUBTITLE, new String[]{text}, null, null, -1f, 16f, false, false);
    }

    static Item heading(String text) {
        return new Item(Kind.HEADING, new String[]{text}, null, null, -1f, 30f, true, true);
    }

    static Item text(String text) {
        return new Item(Kind.TEXT, new String[]{text}, null, null, -1f, 14f, false, false);
    }

    static Item bullet(String text) {
        return new Item(Kind.BULLET, new String[]{text}, null, null, -1f, 14f, false, false);
    }

    static Item space(float height) {
        return new Item(Kind.SPACE, new String[0], null, null, -1f, height, false, false);
    }

    static Item tableHeader(String[] cells, float[] weights, boolean[] right) {
        return new Item(Kind.TABLE_HEADER, cells, weights, right, -1f, 18f, true, true);
    }

    static Item tableRow(Item header, String[] cells, float bar, boolean bold) {
        Item row = new Item(Kind.TABLE_ROW, cells, header.weights, header.right, bar, bar >= 0 ? 22f : 16f,
                false, bold);
        row.header = header;
        return row;
    }

    /**
     * Wraps {@code text} into lines of at most {@code maxChars} characters (breaking at spaces; a single word
     * longer than the limit is hard-split) and returns one TEXT/BULLET item per line. The first line of a bullet
     * carries the bullet; continuation lines are indented with spaces.
     */
    static List<Item> wrapped(String text, int maxChars, boolean bullet) {
        List<Item> out = new ArrayList<>();
        List<String> lines = wrapLines(text, bullet ? maxChars - 2 : maxChars);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (bullet) {
                out.add(i == 0 ? bullet(line) : text("   " + line));
            } else {
                out.add(text(line));
            }
        }
        return out;
    }

    static List<String> wrapLines(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        StringBuilder cur = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            while (word.length() > maxChars) { // a word longer than a whole line: hard split
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                lines.add(word.substring(0, maxChars));
                word = word.substring(maxChars);
            }
            if (cur.length() == 0) {
                cur.append(word);
            } else if (cur.length() + 1 + word.length() <= maxChars) {
                cur.append(' ').append(word);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ------------------------------------------------------------------ pagination

    /**
     * Splits {@code items} into pages of {@code pageHeight} points of content. Rules:
     * <ul>
     *   <li>an item with {@code keepWithNext} moves to the next page unless the whole chain that follows it
     *       (heading, table header, first row) fits with it, so a heading or a table header is never left alone
     *       at the bottom of a page;</li>
     *   <li>blank spacers at the top of a page are dropped;</li>
     *   <li>when a table continues on a new page, its header row is repeated first.</li>
     * </ul>
     * Every item ends up on exactly one page (the repeated headers are additional copies).
     */
    static List<List<Item>> paginate(List<Item> items, float pageHeight) {
        List<List<Item>> pages = new ArrayList<>();
        List<Item> page = new ArrayList<>();
        float used = 0f;
        Item headerOnPage = null;

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.kind == Kind.SPACE && page.isEmpty()) continue;

            // Space needed to keep a chain "heading -> table header -> first row" together.
            float needed = item.height;
            int j = i;
            while (items.get(j).keepWithNext && j + 1 < items.size()) {
                j++;
                needed += items.get(j).height;
            }
            if (!page.isEmpty() && used + needed > pageHeight) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0f;
                headerOnPage = null;
                if (item.kind == Kind.SPACE) continue;
            }
            if (item.kind == Kind.TABLE_ROW && item.header != null && headerOnPage != item.header
                    && page.isEmpty()) {
                // The table continues on a fresh page: repeat its header.
                page.add(item.header);
                used += item.header.height;
                headerOnPage = item.header;
            }
            if (item.kind == Kind.TABLE_HEADER) headerOnPage = item;
            if (item.kind == Kind.HEADING) headerOnPage = null;
            page.add(item);
            used += item.height;
        }
        if (!page.isEmpty()) pages.add(page);
        return pages;
    }
}

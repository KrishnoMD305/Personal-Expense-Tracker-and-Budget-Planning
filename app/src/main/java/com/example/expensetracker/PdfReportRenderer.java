package com.example.expensetracker;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Stage 5: draws a {@link PdfReportModel} onto A4 pages with {@link PdfDocument} (no permissions, no extra
 * library). Layout decisions (what goes on which page) were already made by {@link PdfReportModel#paginate};
 * this class only draws, truncating over-long table cells with an ellipsis so nothing can overflow its column.
 */
final class PdfReportRenderer {
    private static final int INK = 0xFF212121;
    private static final int MUTED = 0xFF616161;
    private static final int RULE = 0xFFBDBDBD;
    private static final int ZEBRA = 0xFFF5F5F5;
    private static final int BAR_TRACK = 0xFFE0E0E0;

    private final int accent;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    PdfReportRenderer(int accentColor) {
        this.accent = accentColor;
        text.setColor(INK);
        text.setTextSize(9.5f);
        text.setTypeface(Typeface.DEFAULT);
        bold.setColor(INK);
        bold.setTextSize(9.5f);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        line.setStrokeWidth(0.7f);
    }

    /** Renders the report and writes the finished PDF to {@code out}. Does not close the stream. */
    void render(List<PdfReportModel.Item> items, String periodLabel, String generatedOn, OutputStream out)
            throws IOException {
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(items, PdfReportModel.CONTENT_HEIGHT);
        if (pages.isEmpty()) pages.add(new java.util.ArrayList<PdfReportModel.Item>());
        PdfDocument doc = new PdfDocument();
        try {
            for (int i = 0; i < pages.size(); i++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                        (int) PdfReportModel.PAGE_WIDTH, (int) PdfReportModel.PAGE_HEIGHT, i + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                try {
                    drawPage(page.getCanvas(), pages.get(i), i + 1, pages.size(), periodLabel, generatedOn);
                } finally {
                    doc.finishPage(page);
                }
            }
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }

    private void drawPage(Canvas c, List<PdfReportModel.Item> items, int number, int total, String period,
                          String generatedOn) {
        drawRunningHeader(c, period);
        float y = PdfReportModel.TOP;
        int zebra = 0;
        for (PdfReportModel.Item item : items) {
            switch (item.kind) {
                case TITLE:
                    bold.setTextSize(22f);
                    bold.setColor(accent);
                    c.drawText(fit(item.text(), bold, PdfReportModel.CONTENT_WIDTH), PdfReportModel.MARGIN_X, y + 22f, bold);
                    bold.setTextSize(9.5f);
                    bold.setColor(INK);
                    break;
                case SUBTITLE:
                    text.setColor(MUTED);
                    text.setTextSize(10.5f);
                    c.drawText(fit(item.text(), text, PdfReportModel.CONTENT_WIDTH), PdfReportModel.MARGIN_X, y + 11f, text);
                    text.setColor(INK);
                    text.setTextSize(9.5f);
                    break;
                case HEADING:
                    bold.setTextSize(13f);
                    bold.setColor(accent);
                    c.drawText(fit(item.text(), bold, PdfReportModel.CONTENT_WIDTH), PdfReportModel.MARGIN_X, y + 17f, bold);
                    bold.setTextSize(9.5f);
                    bold.setColor(INK);
                    line.setColor(accent);
                    c.drawLine(PdfReportModel.MARGIN_X, y + 23f, PdfReportModel.MARGIN_X + PdfReportModel.CONTENT_WIDTH,
                            y + 23f, line);
                    zebra = 0;
                    break;
                case TEXT:
                    drawLine(c, item.text(), y, text);
                    break;
                case BULLET:
                    drawLine(c, "\u2022 " + item.text(), y, text);
                    break;
                case TABLE_HEADER:
                    fill.setColor(accent);
                    fill.setAlpha(30);
                    c.drawRect(PdfReportModel.MARGIN_X, y, PdfReportModel.MARGIN_X + PdfReportModel.CONTENT_WIDTH,
                            y + item.height, fill);
                    fill.setAlpha(255);
                    drawCells(c, item, y + 12.5f, bold);
                    zebra = 0;
                    break;
                case TABLE_ROW:
                    if (zebra++ % 2 == 1) {
                        fill.setColor(ZEBRA);
                        c.drawRect(PdfReportModel.MARGIN_X, y, PdfReportModel.MARGIN_X + PdfReportModel.CONTENT_WIDTH,
                                y + item.height, fill);
                    }
                    drawCells(c, item, y + 11.5f, item.bold ? bold : text);
                    if (item.bar >= 0f) drawBar(c, y + item.height - 6f, item.bar);
                    break;
                default:
                    break; // SPACE
            }
            y += item.height;
        }
        drawFooter(c, number, total, generatedOn);
    }

    private void drawRunningHeader(Canvas c, String period) {
        text.setColor(MUTED);
        text.setTextSize(8.5f);
        c.drawText("Personal Expense Tracker and Budget Planning", PdfReportModel.MARGIN_X, 34f, text);
        String right = fit(period, text, 200f);
        c.drawText(right, PdfReportModel.PAGE_WIDTH - PdfReportModel.MARGIN_X - text.measureText(right), 34f, text);
        line.setColor(RULE);
        c.drawLine(PdfReportModel.MARGIN_X, 40f, PdfReportModel.PAGE_WIDTH - PdfReportModel.MARGIN_X, 40f, line);
        text.setColor(INK);
        text.setTextSize(9.5f);
    }

    private void drawFooter(Canvas c, int number, int total, String generatedOn) {
        float y = PdfReportModel.PAGE_HEIGHT - 26f;
        line.setColor(RULE);
        c.drawLine(PdfReportModel.MARGIN_X, y - 12f, PdfReportModel.PAGE_WIDTH - PdfReportModel.MARGIN_X, y - 12f, line);
        text.setColor(MUTED);
        text.setTextSize(8.5f);
        c.drawText(generatedOn, PdfReportModel.MARGIN_X, y, text);
        String page = "Page " + number + " of " + total;
        c.drawText(page, PdfReportModel.PAGE_WIDTH - PdfReportModel.MARGIN_X - text.measureText(page), y, text);
        text.setColor(INK);
        text.setTextSize(9.5f);
    }

    /** A line of prose: if it is somehow wider than the page, squeeze it instead of letting it run off. */
    private void drawLine(Canvas c, String s, float y, Paint p) {
        float width = p.measureText(s);
        float old = p.getTextScaleX();
        if (width > PdfReportModel.CONTENT_WIDTH) p.setTextScaleX(Math.max(0.6f, PdfReportModel.CONTENT_WIDTH / width));
        c.drawText(fit(s, p, PdfReportModel.CONTENT_WIDTH), PdfReportModel.MARGIN_X, y + 10.5f, p);
        p.setTextScaleX(old);
    }

    private void drawCells(Canvas c, PdfReportModel.Item item, float baseline, Paint p) {
        float total = 0f;
        for (float w : item.weights) total += w;
        float x = PdfReportModel.MARGIN_X;
        float pad = 4f;
        for (int i = 0; i < item.cells.length; i++) {
            float colWidth = PdfReportModel.CONTENT_WIDTH * item.weights[i] / total;
            String s = fit(item.cells[i] == null ? "" : PdfReportBuilder.clean(item.cells[i]), p, colWidth - 2 * pad);
            if (item.right[i]) {
                c.drawText(s, x + colWidth - pad - p.measureText(s), baseline, p);
            } else {
                c.drawText(s, x + pad, baseline, p);
            }
            x += colWidth;
        }
    }

    private void drawBar(Canvas c, float y, float fraction) {
        float width = PdfReportModel.CONTENT_WIDTH - 8f;
        float left = PdfReportModel.MARGIN_X + 4f;
        fill.setColor(BAR_TRACK);
        c.drawRect(left, y, left + width, y + 3f, fill);
        fill.setColor(accent);
        c.drawRect(left, y, left + width * Math.max(0f, Math.min(1f, fraction)), y + 3f, fill);
    }

    /** Cuts {@code s} so it fits in {@code maxWidth}, adding an ellipsis when something was removed. */
    static String fit(String s, Paint p, float maxWidth) {
        if (s == null || s.isEmpty()) return "";
        if (p.measureText(s) <= maxWidth) return s;
        String ellipsis = "\u2026";
        float room = maxWidth - p.measureText(ellipsis);
        if (room <= 0) return "";
        int count = p.breakText(s, true, room, null);
        return count <= 0 ? "" : s.substring(0, count).trim() + ellipsis;
    }
}

package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PdfReportModelTest {
    private static final boolean[] RIGHT = {false, true};
    private static final float[] W = {1f, 1f};

    private static List<PdfReportModel.Item> table(int rows) {
        List<PdfReportModel.Item> items = new ArrayList<>();
        items.add(PdfReportModel.heading("H"));
        PdfReportModel.Item h = PdfReportModel.tableHeader(new String[]{"A", "B"}, W, RIGHT);
        items.add(h);
        for (int i = 0; i < rows; i++) items.add(PdfReportModel.tableRow(h, new String[]{"r" + i, "1"}, -1f, false));
        return items;
    }

    @Test
    public void smallContentFitsOnOnePage() {
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(table(5), PdfReportModel.CONTENT_HEIGHT);
        assertEquals(1, pages.size());
    }

    @Test
    public void noPageOverflowsAndEveryRowAppearsOnce() {
        List<PdfReportModel.Item> items = table(200);
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(items, PdfReportModel.CONTENT_HEIGHT);
        assertTrue(pages.size() > 1);
        int rows = 0;
        for (List<PdfReportModel.Item> page : pages) {
            float used = 0;
            for (PdfReportModel.Item i : page) {
                used += i.height;
                if (i.kind == PdfReportModel.Kind.TABLE_ROW) rows++;
            }
            assertTrue("page too tall: " + used, used <= PdfReportModel.CONTENT_HEIGHT + 0.01f);
        }
        assertEquals(200, rows);
    }

    @Test
    public void continuedTableRepeatsItsHeader() {
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(table(200), PdfReportModel.CONTENT_HEIGHT);
        for (int p = 1; p < pages.size(); p++) {
            assertEquals(PdfReportModel.Kind.TABLE_HEADER, pages.get(p).get(0).kind);
        }
    }

    @Test
    public void headingIsNeverLastOnAPage() {
        // Fill a page so that only ~40pt are left, then add heading + header + row: they must move together.
        List<PdfReportModel.Item> items = new ArrayList<>();
        float fill = PdfReportModel.CONTENT_HEIGHT - 40f;
        while (fill > 14f) {
            items.add(PdfReportModel.text("filler"));
            fill -= 14f;
        }
        items.addAll(table(3));
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(items, PdfReportModel.CONTENT_HEIGHT);
        for (List<PdfReportModel.Item> page : pages) {
            PdfReportModel.Item last = page.get(page.size() - 1);
            assertFalse(last.kind == PdfReportModel.Kind.HEADING);
            assertFalse(last.kind == PdfReportModel.Kind.TABLE_HEADER);
        }
    }

    @Test
    public void spacerAtTopOfPageIsDropped() {
        List<PdfReportModel.Item> items = new ArrayList<>();
        float fill = PdfReportModel.CONTENT_HEIGHT;
        while (fill >= 14f) {
            items.add(PdfReportModel.text("x"));
            fill -= 14f;
        }
        items.add(PdfReportModel.space(8));
        items.add(PdfReportModel.text("next page"));
        List<List<PdfReportModel.Item>> pages = PdfReportModel.paginate(items, PdfReportModel.CONTENT_HEIGHT);
        assertEquals(2, pages.size());
        assertEquals(PdfReportModel.Kind.TEXT, pages.get(1).get(0).kind);
    }

    @Test
    public void emptyInputGivesNoPages() {
        assertEquals(0, PdfReportModel.paginate(new ArrayList<PdfReportModel.Item>(), PdfReportModel.CONTENT_HEIGHT).size());
    }

    @Test
    public void wrapBreaksAtSpacesAndSplitsLongWords() {
        List<String> lines = PdfReportModel.wrapLines("aaa bbb ccc ddd", 7);
        assertEquals(2, lines.size());
        assertEquals("aaa bbb", lines.get(0));
        assertEquals("ccc ddd", lines.get(1));
        List<String> longWord = PdfReportModel.wrapLines("abcdefghij", 4);
        assertEquals(3, longWord.size());
        assertEquals("ij", longWord.get(2));
        for (String l : PdfReportModel.wrapLines("The quick brown fox jumps over the lazy dog again and again", 20)) {
            assertTrue(l.length() <= 20);
        }
    }

    @Test
    public void wrappedBulletMarksOnlyFirstLine() {
        List<PdfReportModel.Item> items = PdfReportModel.wrapped("one two three four five six", 12, true);
        assertEquals(PdfReportModel.Kind.BULLET, items.get(0).kind);
        for (int i = 1; i < items.size(); i++) assertEquals(PdfReportModel.Kind.TEXT, items.get(i).kind);
    }

    @Test
    public void cleanReplacesTakaSign() {
        assertEquals("Spent BDT 1,250.50 on food", PdfReportBuilder.clean("Spent \u09F3 1,250.50 on food"));
        assertEquals("BDT 5", PdfReportBuilder.clean("\u09F35"));
    }
}

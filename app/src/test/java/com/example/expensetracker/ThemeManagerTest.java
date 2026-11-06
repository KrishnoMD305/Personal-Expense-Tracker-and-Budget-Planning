package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThemeManagerTest {
    @Test
    public void fourThemesWithStableIds() {
        assertEquals(4, ThemeManager.ALL.length);
        assertEquals("white", ThemeManager.WHITE);
        assertEquals("blue", ThemeManager.BLUE);
        assertEquals("red", ThemeManager.RED);
        assertEquals("dark", ThemeManager.DARK);
    }

    @Test
    public void unknownOrMissingThemeFallsBackToDefault() {
        assertEquals(ThemeManager.DEFAULT, ThemeManager.normalize(null));
        assertEquals(ThemeManager.DEFAULT, ThemeManager.normalize(""));
        assertEquals(ThemeManager.DEFAULT, ThemeManager.normalize("neon"));
        assertEquals(ThemeManager.DEFAULT, ThemeManager.normalize("BLUE")); // ids are case-sensitive
        for (String id : ThemeManager.ALL) assertEquals(id, ThemeManager.normalize(id));
    }

    @Test
    public void pdfAccentIsOpaqueAndDarkEnoughForWhitePaper() {
        for (String id : ThemeManager.ALL) {
            int c = ThemeManager.pdfAccent(id);
            assertEquals(0xFF, (c >>> 24) & 0xFF);
            int lum = (((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
            assertTrue(id + " accent too light for print: " + lum, lum < 140);
        }
    }
}

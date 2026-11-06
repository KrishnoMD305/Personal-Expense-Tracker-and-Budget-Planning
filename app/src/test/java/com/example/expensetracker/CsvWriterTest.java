package com.example.expensetracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CsvWriterTest {
    private static String row(String... text) throws Exception {
        StringBuilder sb = new StringBuilder();
        CsvWriter w = new CsvWriter(sb);
        for (String t : text) w.text(t);
        w.endRow();
        return sb.toString();
    }

    @Test
    public void plainCellsAreNotQuoted() throws Exception {
        assertEquals("a,b,c\r\n", row("a", "b", "c"));
    }

    @Test
    public void commasQuotesAndNewlinesAreQuoted() throws Exception {
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",\"line1\nline2\"\r\n", row("a,b", "say \"hi\"", "line1\nline2"));
    }

    @Test
    public void emptyAndNullCellsAreEmpty() throws Exception {
        assertEquals(",,x\r\n", row("", null, "x"));
    }

    @Test
    public void formulaStartsAreNeutralised() throws Exception {
        assertEquals("'=1+1,'+5,'-2,'@SUM(A1)\r\n", row("=1+1", "+5", "-2", "@SUM(A1)"));
    }

    @Test
    public void numbersAreNeverAltered() throws Exception {
        StringBuilder sb = new StringBuilder();
        CsvWriter w = new CsvWriter(sb);
        w.number("-300.00").number("1250.50").endRow();
        assertEquals("-300.00,1250.50\r\n", sb.toString());
    }

    @Test
    public void bengaliTextPassesThrough() throws Exception {
        assertEquals("\u09AC\u09BE\u099C\u09BE\u09B0,x\r\n", row("\u09AC\u09BE\u099C\u09BE\u09B0", "x"));
    }
}

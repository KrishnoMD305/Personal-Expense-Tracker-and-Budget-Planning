package com.example.expensetracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyFormatStage5Test {
    @Test
    public void plainHasNoSymbolOrGrouping() {
        assertEquals("1250.50", MoneyUtils.plain(125050));
        assertEquals("0.05", MoneyUtils.plain(5));
        assertEquals("0.00", MoneyUtils.plain(0));
        assertEquals("-300.00", MoneyUtils.plain(-30000));
        assertEquals("999999999.99", MoneyUtils.plain(MoneyUtils.MAX_PAISA));
    }

    @Test
    public void groupedAndBdt() {
        assertEquals("1,250.50", MoneyUtils.grouped(125050));
        assertEquals("-1,250.50", MoneyUtils.grouped(-125050));
        assertEquals("BDT 1,250.50", MoneyUtils.formatBdt(125050));
        assertEquals("-BDT 300.00", MoneyUtils.formatBdt(-30000));
        assertEquals("BDT 0.00", MoneyUtils.formatBdt(0));
    }
}

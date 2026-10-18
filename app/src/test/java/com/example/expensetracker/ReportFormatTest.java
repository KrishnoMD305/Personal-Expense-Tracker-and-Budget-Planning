package com.example.expensetracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReportFormatTest {
    @Test
    public void tenthsDropsTrailingZeros() {
        assertEquals("18.5", ReportFormat.tenths(185));
        assertEquals("20", ReportFormat.tenths(200));
        assertEquals("0", ReportFormat.tenths(0));
        assertEquals("-2.5", ReportFormat.tenths(-25));
        assertEquals("0.1", ReportFormat.tenths(1));
        assertEquals("100", ReportFormat.tenths(1000));
    }

    @Test
    public void percentHandlesMissingValues() {
        assertEquals("66.7%", ReportFormat.percent(667L));
        assertEquals("N/A", ReportFormat.percent(null));
        assertEquals("12.5%", ReportFormat.percentMagnitude(-125L));
        assertEquals("N/A", ReportFormat.percentMagnitude(null));
    }

    @Test
    public void signedPercentAlwaysShowsDirection() {
        assertEquals("+12.5%", ReportFormat.signedPercent(125L));
        assertEquals("-8%", ReportFormat.signedPercent(-80L));
        assertEquals("0%", ReportFormat.signedPercent(0L));
        assertEquals("\u2014", ReportFormat.signedPercent(null));
    }

    @Test
    public void signedMoney() {
        assertEquals("+\u09F3 500.00", ReportFormat.signedMoney(50000));
        assertEquals("-\u09F3 500.00", ReportFormat.signedMoney(-50000));
        assertEquals("\u09F3 0.00", ReportFormat.signedMoney(0));
    }

    @Test
    public void pointsChangeBetweenRates() {
        assertEquals("+2.5 pts", ReportFormat.pointsChange(300L, 275L));
        assertEquals("-10 pts", ReportFormat.pointsChange(0L, 100L));
        assertEquals("0 pts", ReportFormat.pointsChange(200L, 200L));
        assertEquals("\u2014", ReportFormat.pointsChange(null, 100L));
        assertEquals("\u2014", ReportFormat.pointsChange(100L, null));
    }

    @Test
    public void compactAmountForChartAxes() {
        assertEquals("0", ReportFormat.compactAmount(0));
        assertEquals("950", ReportFormat.compactAmount(950));
        assertEquals("999.9", ReportFormat.compactAmount(999.9));
        assertEquals("1k", ReportFormat.compactAmount(999.96));   // not "1000"
        assertEquals("12.5k", ReportFormat.compactAmount(12500));
        assertEquals("2.3M", ReportFormat.compactAmount(2300000));
        assertEquals("1M", ReportFormat.compactAmount(999960));   // not "1000k"
        assertEquals("-1.5k", ReportFormat.compactAmount(-1500));
    }
}

package com.example.expensetracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyUtilsTest {
    @Test
    public void formatsPositiveAndNegative() {
        assertEquals("৳ 1,250.50", MoneyUtils.format(125050));
        assertEquals("৳ 0.05", MoneyUtils.format(5));
        assertEquals("-৳ 300.00", MoneyUtils.format(-30000));
    }
}

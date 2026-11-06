package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinPolicyTest {
    @Test
    public void validPinsAreFourToSixDigits() {
        assertTrue(PinPolicy.isValid("1234"));
        assertTrue(PinPolicy.isValid("123456"));
        assertTrue(PinPolicy.isValid("0000"));
    }

    @Test
    public void invalidPinsAreRejected() {
        assertFalse(PinPolicy.isValid(null));
        assertFalse(PinPolicy.isValid(""));
        assertFalse(PinPolicy.isValid("123"));
        assertFalse(PinPolicy.isValid("1234567"));
        assertFalse(PinPolicy.isValid("12a4"));
        assertFalse(PinPolicy.isValid("12 4"));
        assertFalse(PinPolicy.isValid("\u0661\u0662\u0663\u0664")); // Arabic-Indic digits are not accepted
    }

    @Test
    public void noLockoutForFirstFourFailures() {
        for (int i = 0; i < PinPolicy.FREE_ATTEMPTS; i++) assertEquals(0L, PinPolicy.lockoutMillis(i));
    }

    @Test
    public void lockoutStartsAtThirtySecondsAndDoublesUpToFiveMinutes() {
        assertEquals(30_000L, PinPolicy.lockoutMillis(5));
        assertEquals(60_000L, PinPolicy.lockoutMillis(6));
        assertEquals(120_000L, PinPolicy.lockoutMillis(7));
        assertEquals(240_000L, PinPolicy.lockoutMillis(8));
        assertEquals(300_000L, PinPolicy.lockoutMillis(9));
        assertEquals(300_000L, PinPolicy.lockoutMillis(500));
    }

    @Test
    public void remainingLockoutNeverNegative() {
        assertEquals(5_000L, PinPolicy.remainingLockoutMillis(15_000L, 10_000L));
        assertEquals(0L, PinPolicy.remainingLockoutMillis(10_000L, 15_000L));
    }
}

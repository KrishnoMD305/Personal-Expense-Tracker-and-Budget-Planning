package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Stage 7: receipt file / cloud path rules (they guard against path tricks in backup files). */
public class ReceiptNamesTest {
    @Test
    public void newNamesAreValidAndDistinct() {
        String a = ReceiptNames.newName(1762500000000L, 0xA3F1);
        assertEquals("receipt_1762500000000_a3f1.jpg", a);
        assertTrue(ReceiptNames.isValid(a));
        assertFalse(a.equals(ReceiptNames.newName(1762500000000L, 0x0001)));
        assertTrue(ReceiptNames.isValid(ReceiptNames.newName(0, 0x10000 + 5))); // random is masked to 16 bits
    }

    @Test
    public void rejectsNamesThatCouldEscapeTheFolder() {
        assertFalse(ReceiptNames.isValid("../databases/expense_tracker.db"));
        assertFalse(ReceiptNames.isValid("receipt_1_abcd.jpg/../x"));
        assertFalse(ReceiptNames.isValid("/etc/passwd"));
        assertFalse(ReceiptNames.isValid("receipt_1_abcd.png"));
        assertFalse(ReceiptNames.isValid("receipt_x_abcd.jpg"));
        assertFalse(ReceiptNames.isValid(""));
        assertFalse(ReceiptNames.isValid(null));
        assertTrue(ReceiptNames.isValidOrEmpty(""));
        assertFalse(ReceiptNames.isValidOrEmpty("a/b.jpg"));
    }

    @Test
    public void cloudPathsAreOwnedByTheUser() {
        String file = "receipt_1_abcd.jpg";
        String path = ReceiptNames.cloudPath("uid123", file);
        assertEquals("users/uid123/receipts/receipt_1_abcd.jpg", path);
        assertTrue(ReceiptNames.isValidCloudPathOrEmpty(path));
        assertTrue(ReceiptNames.isValidCloudPathOrEmpty(""));
        assertFalse(ReceiptNames.isValidCloudPathOrEmpty("users/../receipts/x.jpg"));
        assertFalse(ReceiptNames.isValidCloudPathOrEmpty("other/uid/receipts/receipt_1_abcd.jpg"));
        assertTrue(ReceiptNames.isUploadedFor("uid123", file, path));
        assertFalse("another account's upload does not count", ReceiptNames.isUploadedFor("uid999", file, path));
        assertFalse(ReceiptNames.isUploadedFor("uid123", file, ""));
        assertFalse(ReceiptNames.isUploadedFor(null, file, path));
    }

    @Test
    public void sampleSizeShrinksByPowersOfTwo() {
        assertEquals(1, ReceiptNames.sampleSize(1200, 800, 1600));
        assertEquals(1, ReceiptNames.sampleSize(3199, 2000, 1600));
        assertEquals(2, ReceiptNames.sampleSize(3200, 2000, 1600));
        assertEquals(4, ReceiptNames.sampleSize(4000, 6400, 1600));
        assertEquals(1, ReceiptNames.sampleSize(0, 0, 1600));
    }
}

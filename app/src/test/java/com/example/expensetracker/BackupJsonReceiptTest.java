package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

/** Stage 7: receipt columns survive backup / restore, old files still load, tampered names are rejected. */
public class BackupJsonReceiptTest {
    private static BackupData withBill(String file, String cloud) {
        BackupData d = new BackupData();
        d.dbVersion = AppDatabase.VERSION;
        Bill b = new Bill();
        b.id = 1; b.name = "Electricity"; b.amountPaisa = 150000; b.category = "Utilities";
        b.dueDateMillis = 1760000000000L; b.createdAt = 1;
        b.receiptFile = file; b.receiptCloudPath = cloud;
        d.bills.add(b);
        return d;
    }

    @Test
    public void receiptRoundTrips() throws Exception {
        String file = "receipt_1762500000000_a3f1.jpg";
        String cloud = "users/uid123/receipts/" + file;
        BackupData back = BackupJson.parse(BackupJson.toJson(withBill(file, cloud)));
        assertEquals(file, back.bills.get(0).receiptFile);
        assertEquals(cloud, back.bills.get(0).receiptCloudPath);
    }

    @Test
    public void filesWrittenBeforeStage7StillLoad() throws Exception {
        JSONObject root = new JSONObject(BackupJson.toJson(withBill("", "")));
        JSONObject bill = root.getJSONObject("data").getJSONArray("bills").getJSONObject(0);
        bill.remove("receiptFile");
        bill.remove("receiptCloudPath");
        BackupData back = BackupJson.parse(root.toString());
        assertEquals("", back.bills.get(0).receiptFile);
        assertEquals("", back.bills.get(0).receiptCloudPath);
    }

    @Test
    public void tamperedReceiptNamesAreRejected() throws Exception {
        String[] evil = {"../../databases/expense_tracker.db", "a/b.jpg", "receipt_1_abcd.png"};
        for (String name : evil) {
            JSONObject root = new JSONObject(BackupJson.toJson(withBill("", "")));
            root.getJSONObject("data").getJSONArray("bills").getJSONObject(0).put("receiptFile", name);
            try {
                BackupJson.parse(root.toString());
                fail("accepted " + name);
            } catch (BackupException expected) {
                assertTrue(expected.getMessage().contains("receipt"));
            }
        }
    }

    @Test
    public void cloudPathOutsideUsersFolderIsRejected() throws Exception {
        JSONObject root = new JSONObject(BackupJson.toJson(withBill("receipt_1_abcd.jpg", "")));
        root.getJSONObject("data").getJSONArray("bills").getJSONObject(0)
                .put("receiptCloudPath", "admin/secret.jpg");
        try {
            BackupJson.parse(root.toString());
            fail("accepted");
        } catch (BackupException expected) {
            // ok
        }
    }
}

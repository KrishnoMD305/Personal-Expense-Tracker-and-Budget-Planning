package com.example.expensetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Stage 6: the mapping between the backup document and Cloud Firestore documents (pure Java, no Firebase needed). */
public class CloudDocsTest {
    private static String json() throws Exception {
        return BackupJson.toJson(BackupJsonTest.sample());
    }

    @Test
    public void splitMakesOneRowPerRecordWithItsNumericId() throws Exception {
        Map<String, List<Map<String, Object>>> rows = CloudDocs.split(json());
        assertEquals(CloudDocs.COLLECTIONS.length, rows.size());
        assertEquals(2, rows.get("transactions").size());
        assertEquals(1, rows.get("budgets").size());
        assertEquals(1, rows.get("savingsGoals").size());
        assertEquals(1, rows.get("goalContributions").size());
        assertEquals(1, rows.get("recurringRules").size());
        assertEquals(1, rows.get("recurringPostings").size());
        assertEquals(1, rows.get("bills").size());
        assertEquals("7", CloudDocs.docId(rows.get("transactions").get(0)));
        assertEquals("9", CloudDocs.docId(rows.get("transactions").get(1)));
    }

    @Test
    public void rowValuesUseOnlyTypesFirestoreStoresAndReturns() throws Exception {
        for (List<Map<String, Object>> list : CloudDocs.split(json()).values()) {
            for (Map<String, Object> row : list) {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    Object v = e.getValue();
                    boolean ok = v == null || v instanceof Long || v instanceof String || v instanceof Boolean;
                    assertTrue(e.getKey() + " has type " + (v == null ? "null" : v.getClass()), ok);
                }
            }
        }
        // missing category / note are kept as real nulls (not the string "null")
        Map<String, Object> income = CloudDocs.split(json()).get("transactions").get(1);
        assertTrue(income.containsKey("category"));
        assertNull(income.get("category"));
        assertNull(income.get("note"));
    }

    @Test
    public void cloudRoundTripIsLosslessAndGoesThroughBackupValidation() throws Exception {
        String json = json();
        Map<String, List<Map<String, Object>>> rows = CloudDocs.split(json);
        Map<String, Object> meta = CloudDocs.meta(json, CloudDocs.STATUS_COMPLETE, 1760000999000L, "Pixel 7");

        BackupData in = BackupJsonTest.sample();
        BackupData out = BackupJson.parse(CloudDocs.assemble(meta, rows));

        assertEquals(in.totalRows(), out.totalRows());
        assertEquals("dark", out.theme);
        assertEquals(1760000999000L, out.createdAtMillis); // the version stamp travels as createdAt
        assertEquals(AppDatabase.VERSION, out.dbVersion);
        Transaction t = out.transactions.get(0);
        assertEquals(7, t.id);
        assertEquals(125050, t.amountPaisa);
        assertEquals(in.transactions.get(0).note, t.note);
        assertNull(out.transactions.get(1).category);
        assertNull(out.transactions.get(1).note);
        assertTrue(out.goals.get(0).archived);
        assertEquals(4, out.contributions.get(0).goalId);
        assertEquals(7, out.bills.get(0).paymentTransactionId);
    }

    @Test
    public void rowOrderFromTheCloudDoesNotMatter() throws Exception {
        String json = json();
        Map<String, List<Map<String, Object>>> rows = CloudDocs.split(json);
        for (List<Map<String, Object>> list : rows.values()) Collections.reverse(list); // Firestore sorts by document id text
        BackupData out = BackupJson.parse(CloudDocs.assemble(
                CloudDocs.meta(json, CloudDocs.STATUS_COMPLETE, 5L, "x"), rows));
        assertEquals(7, out.transactions.get(0).id); // sorted back by numeric id
        assertEquals(9, out.transactions.get(1).id);
    }

    @Test
    public void aHalfUploadedCloudCopyIsRejected() throws Exception {
        String json = json();
        Map<String, List<Map<String, Object>>> rows = CloudDocs.split(json);
        rows.get("transactions").remove(1); // a row never arrived
        try {
            BackupJson.parse(CloudDocs.assemble(CloudDocs.meta(json, CloudDocs.STATUS_COMPLETE, 5L, "x"), rows));
            fail("expected the count check to reject the copy");
        } catch (BackupException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("damaged"));
        }
    }

    @Test
    public void aCorruptedRowIsRejected() throws Exception {
        String json = json();
        Map<String, List<Map<String, Object>>> rows = CloudDocs.split(json);
        rows.get("transactions").get(0).put("amountPaisa", -5L);
        try {
            BackupJson.parse(CloudDocs.assemble(CloudDocs.meta(json, CloudDocs.STATUS_COMPLETE, 5L, "x"), rows));
            fail("expected validation to reject the amount");
        } catch (BackupException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("amount"));
        }
    }

    @Test
    public void metaCarriesStatusStampCountsAndDevice() throws Exception {
        Map<String, Object> meta = CloudDocs.meta(json(), CloudDocs.STATUS_UPLOADING, 42L, "Pixel 7");
        assertEquals("uploading", meta.get("status"));
        assertEquals(42L, meta.get("updatedAt"));
        assertEquals("Pixel 7", meta.get("device"));
        assertEquals(BackupJson.APP_MARKER, meta.get("app"));
        assertEquals(8L, meta.get("totalRows"));
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) meta.get("counts");
        assertEquals(2L, counts.get("transactions"));
        assertEquals(1L, counts.get("bills"));
    }

    @Test
    public void fingerprintIgnoresTimestampsButNoticesAnyChange() throws Exception {
        BackupData a = BackupJsonTest.sample();
        BackupData b = BackupJsonTest.sample();
        b.createdAtMillis = a.createdAtMillis + 123456; // a later snapshot of identical data
        String fa = CloudDocs.fingerprint(CloudDocs.split(BackupJson.toJson(a)), a.theme);
        String fb = CloudDocs.fingerprint(CloudDocs.split(BackupJson.toJson(b)), b.theme);
        assertEquals(fa, fb);
        assertEquals(64, fa.length());

        b.transactions.get(0).amountPaisa += 1;
        assertNotEquals(fa, CloudDocs.fingerprint(CloudDocs.split(BackupJson.toJson(b)), b.theme));

        BackupData c = BackupJsonTest.sample();
        assertNotEquals(fa, CloudDocs.fingerprint(CloudDocs.split(BackupJson.toJson(c)), "blue"));

        BackupData d = BackupJsonTest.sample();
        d.transactions.remove(1);
        assertNotEquals(fa, CloudDocs.fingerprint(CloudDocs.split(BackupJson.toJson(d)), d.theme));
    }

    @Test
    public void emptyDatabaseRoundTrips() throws Exception {
        BackupData empty = new BackupData();
        empty.dbVersion = AppDatabase.VERSION;
        String json = BackupJson.toJson(empty);
        BackupData out = BackupJson.parse(CloudDocs.assemble(
                CloudDocs.meta(json, CloudDocs.STATUS_COMPLETE, 1L, ""), CloudDocs.split(json)));
        assertEquals(0, out.totalRows());
        assertFalse(CloudDocs.fingerprint(CloudDocs.split(json), "").isEmpty());
    }
}

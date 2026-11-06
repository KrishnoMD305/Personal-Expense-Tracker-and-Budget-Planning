package com.example.expensetracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stage 6: converts between the validated backup document ({@link BackupJson}) and the plain maps stored in
 * Cloud Firestore. It uses only {@code org.json} and {@code java.util}, so it is unit-tested on the JVM.
 *
 * <p>Firestore layout (one user's data, {@code users/{uid}}):
 * <pre>
 *   users/{uid}                      meta document: status, counts, updatedAt, theme, ...
 *   users/{uid}/transactions/{id}    one document per row, field names = backup field names
 *   users/{uid}/budgets/{id}
 *   users/{uid}/savingsGoals/{id}
 *   users/{uid}/goalContributions/{id}
 *   users/{uid}/recurringRules/{id}
 *   users/{uid}/recurringPostings/{id}
 *   users/{uid}/bills/{id}
 * </pre>
 * On download the rows are put back together into a backup document and handed to {@link BackupJson#parse}, so
 * data coming from the cloud goes through exactly the same strict validation as a backup file.
 */
final class CloudDocs {
    /** Sub-collection names. They are also the keys of the "data" object of the backup document. */
    static final String[] COLLECTIONS = {
            "transactions", "budgets", "savingsGoals", "goalContributions",
            "recurringRules", "recurringPostings", "bills"};

    static final String STATUS_UPLOADING = "uploading";
    static final String STATUS_COMPLETE = "complete";

    private CloudDocs() {}

    /** Splits a backup document into rows per collection (each row is a map of field name to value). */
    static Map<String, List<Map<String, Object>>> split(String backupJson) throws JSONException {
        JSONObject data = new JSONObject(backupJson).getJSONObject("data");
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (String col : COLLECTIONS) {
            JSONArray array = data.getJSONArray(col);
            List<Map<String, Object>> rows = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Map<String, Object> row = new HashMap<>();
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    row.put(k, fromJson(o.get(k)));
                }
                rows.add(row);
            }
            out.put(col, rows);
        }
        return out;
    }

    /** The document id of a row: its numeric id as text. */
    static String docId(Map<String, Object> row) {
        return String.valueOf(row.get("id"));
    }

    /**
     * The meta document written to {@code users/{uid}}.
     *
     * @param updatedAt value stored as {@code updatedAt}; the version stamp of the whole cloud copy
     */
    static Map<String, Object> meta(String backupJson, String status, long updatedAt, String device)
            throws JSONException {
        JSONObject root = new JSONObject(backupJson);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("app", root.getString("app"));
        meta.put("backupFormat", root.getLong("backupFormat"));
        meta.put("dbVersion", root.getLong("dbVersion"));
        meta.put("appVersion", root.optString("appVersion", ""));
        JSONObject settings = root.optJSONObject("settings");
        meta.put("theme", settings == null ? "" : settings.optString("theme", ""));
        meta.put("status", status);
        meta.put("updatedAt", updatedAt);
        meta.put("device", device == null ? "" : device);

        JSONObject counts = root.getJSONObject("counts");
        Map<String, Object> countMap = new LinkedHashMap<>();
        long total = 0;
        for (String col : COLLECTIONS) {
            long n = counts.getLong(col);
            countMap.put(col, n);
            total += n;
        }
        meta.put("counts", countMap);
        meta.put("totalRows", total);
        return meta;
    }

    /**
     * Rebuilds a backup document from the meta document and the downloaded rows. The result is NOT trusted: the
     * caller passes it to {@link BackupJson#parse}, which validates every field and checks the row counts against
     * the meta document (so a half-uploaded cloud copy is rejected).
     */
    static String assemble(Map<String, Object> meta, Map<String, List<Map<String, Object>>> rows)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", String.valueOf(meta.get("app")));
        root.put("backupFormat", number(meta.get("backupFormat")));
        root.put("dbVersion", number(meta.get("dbVersion")));
        Object appVersion = meta.get("appVersion");
        root.put("appVersion", appVersion == null ? "" : String.valueOf(appVersion));
        root.put("createdAt", number(meta.get("updatedAt")));

        JSONObject settings = new JSONObject();
        Object theme = meta.get("theme");
        settings.put("theme", theme == null ? "" : String.valueOf(theme));
        root.put("settings", settings);

        JSONObject counts = new JSONObject();
        Object cm = meta.get("counts");
        if (cm instanceof Map) {
            Map<?, ?> countMap = (Map<?, ?>) cm;
            for (String col : COLLECTIONS) {
                if (countMap.containsKey(col)) counts.put(col, number(countMap.get(col)));
            }
        }
        root.put("counts", counts);

        JSONObject data = new JSONObject();
        for (String col : COLLECTIONS) {
            List<Map<String, Object>> list = rows.get(col);
            List<Map<String, Object>> sorted = new ArrayList<>(list == null ? Collections.<Map<String, Object>>emptyList() : list);
            Collections.sort(sorted, BY_ID);
            JSONArray array = new JSONArray();
            for (Map<String, Object> row : sorted) {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    o.put(e.getKey(), e.getValue() == null ? JSONObject.NULL : e.getValue());
                }
                array.put(o);
            }
            data.put(col, array);
        }
        root.put("data", data);
        return root.toString();
    }

    /**
     * A fingerprint of everything that is uploaded (all rows and the theme, not the timestamp). Equal fingerprints
     * mean "nothing changed since the last upload", which lets automatic backups skip the network entirely.
     */
    static String fingerprint(Map<String, List<Map<String, Object>>> rows, String theme) {
        StringBuilder sb = new StringBuilder();
        sb.append("theme=").append(theme == null ? "" : theme).append('\n');
        for (String col : COLLECTIONS) {
            sb.append(col).append('[');
            List<Map<String, Object>> list = rows.get(col);
            if (list != null) {
                for (Map<String, Object> row : list) {
                    for (Map.Entry<String, Object> e : new TreeMap<>(row).entrySet()) {
                        sb.append(e.getKey()).append('=').append(String.valueOf(e.getValue())).append(';');
                    }
                    sb.append('|');
                }
            }
            sb.append("]\n");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 exists on every Java / Android platform
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final Comparator<Map<String, Object>> BY_ID = new Comparator<Map<String, Object>>() {
        @Override
        public int compare(Map<String, Object> a, Map<String, Object> b) {
            return Long.compare(idOf(a), idOf(b));
        }
    };

    private static long idOf(Map<String, Object> row) {
        Object id = row.get("id");
        return id instanceof Number ? ((Number) id).longValue() : 0L;
    }

    /** JSON null becomes Java null; whole numbers always become Long so equality with Firestore data works. */
    private static Object fromJson(Object v) {
        if (v == null || v == JSONObject.NULL) return null;
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) return ((Number) v).longValue();
        return v;
    }

    private static long number(Object v) throws JSONException {
        if (v instanceof Number) return ((Number) v).longValue();
        throw new JSONException("The cloud data is missing a number (got " + v + ").");
    }
}

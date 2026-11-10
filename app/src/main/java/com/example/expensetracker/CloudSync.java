package com.example.expensetracker;

import android.content.Context;
import android.os.Build;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stage 6: cloud backup and restore through Cloud Firestore. Room stays the single source of truth on the
 * device (the app works fully offline); the cloud holds a mirror of every table under {@code users/{uid}}.
 *
 * <p>Safety rules:
 * <ul>
 *   <li><b>Never blocks the UI.</b> Every method blocks on network tasks, so call them from {@link #IO} or a
 *       WorkManager worker only.</li>
 *   <li><b>The device's data is never changed by an upload.</b> A download only returns a validated
 *       {@link BackupData}; replacing local data is done afterwards by {@link BackupRepository#restore}, which
 *       writes a safety copy and is all-or-nothing.</li>
 *   <li><b>No silent overwrite of another device's data.</b> Each upload records a version stamp
 *       ({@code updatedAt}). A non-forced upload is refused ({@link Outcome#CONFLICT}) when the cloud copy was
 *       written by someone else since this device last uploaded or restored.</li>
 *   <li><b>Half-uploaded copies are rejected.</b> The meta document says {@code uploading} until the last row
 *       is written; downloads accept only {@code complete} copies whose row counts match.</li>
 *   <li>Only changed rows are written, and nothing is sent when the data has not changed (see
 *       {@link CloudDocs#fingerprint}) - this keeps the app inside Firebase's free quota.</li>
 * </ul>
 */
final class CloudSync {
    /** Single background thread for all cloud work started from screens. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    enum Outcome { UPLOADED, UNCHANGED, CONFLICT }

    /** Stage 7: what a progress callback is about. */
    static final int STAGE_CHECK = 1;      // talking to the server / comparing
    static final int STAGE_UPLOAD = 2;     // writing rows to Firestore (done / total rows)
    static final int STAGE_DOWNLOAD = 3;   // reading collections from Firestore (done / total collections)
    static final int STAGE_RESTORE = 4;    // replacing the local database
    static final int STAGE_RECEIPTS = 5;   // receipt photos in Firebase Storage (done / total photos)

    /** Stage 7: progress reports for the foreground backup / restore service. Called on the worker thread. */
    interface ProgressListener {
        void onProgress(int stage, int done, int total);
    }

    /** What an upload did. */
    static final class UploadResult {
        final Outcome outcome;
        final int rows;
        final long updatedAt;
        /** Set when {@link #outcome} is CONFLICT: the cloud copy that is in the way. */
        final RemoteInfo remote;

        UploadResult(Outcome outcome, int rows, long updatedAt, RemoteInfo remote) {
            this.outcome = outcome;
            this.rows = rows;
            this.updatedAt = updatedAt;
            this.remote = remote;
        }
    }

    /** A summary of the cloud copy (the meta document). */
    static final class RemoteInfo {
        final long updatedAt;
        final String device;
        final long rows;
        final boolean complete;

        RemoteInfo(long updatedAt, String device, long rows, boolean complete) {
            this.updatedAt = updatedAt;
            this.device = device;
            this.rows = rows;
            this.complete = complete;
        }
    }

    private static final String USERS = "users";
    private static final long TIMEOUT_SECONDS = 60;
    /** Firestore allows 500 writes per batch; stay well below. */
    private static final int BATCH_SIZE = 400;

    private static final Object LOCK = new Object();
    private static boolean settingsApplied;

    private CloudSync() {}

    // ------------------------------------------------------------------ public operations

    /** Reads the meta document. Returns null when this account has no cloud copy yet. Also proves connectivity. */
    static RemoteInfo remoteInfo(Context context) throws CloudException {
        synchronized (LOCK) {
            String uid = requireUid(context);
            DocumentSnapshot snap = await(userDoc(uid).get(Source.SERVER));
            return snap.exists() ? readInfo(snap) : null;
        }
    }

    /**
     * Uploads the device's data (changed rows only).
     *
     * @param force true = replace the cloud copy even if another device wrote it (the user confirmed)
     */
    static UploadResult upload(Context context, boolean force) throws CloudException {
        return upload(context, force, null);
    }

    /** Same as {@link #upload(Context, boolean)} but reports progress (rows written so far) to {@code progress}. */
    static UploadResult upload(Context context, boolean force, ProgressListener progress) throws CloudException {
        synchronized (LOCK) {
            final Context app = context.getApplicationContext();
            final String uid = requireUid(app);
            final AppSettings settings = AppSettings.get(app);
            final DocumentReference userDoc = userDoc(uid);

            // 1) Probe: proves the network works BEFORE anything is written, and detects a conflicting copy.
            report(progress, STAGE_CHECK, 0, 0);
            DocumentSnapshot metaSnap = await(userDoc.get(Source.SERVER));
            RemoteInfo remote = metaSnap.exists() ? readInfo(metaSnap) : null;
            long base = settings.cloudBase(uid);
            if (!force && remote != null && remote.updatedAt != base) {
                return new UploadResult(Outcome.CONFLICT, 0, 0, remote);
            }

            // 2) Snapshot of the local database, split into one map per row.
            BackupData data = BackupRepository.snapshot(app);
            String json;
            Map<String, List<Map<String, Object>>> rows;
            try {
                json = BackupJson.toJson(data);
                rows = CloudDocs.split(json);
            } catch (JSONException e) {
                throw new CloudException("Could not prepare your data for upload: " + e.getMessage(), false, e);
            }
            String fingerprint = CloudDocs.fingerprint(rows, data.theme);
            if (!force && remote != null && remote.complete && remote.updatedAt == base
                    && fingerprint.equals(settings.cloudFingerprint(uid))) {
                return new UploadResult(Outcome.UNCHANGED, data.totalRows(), remote.updatedAt, remote);
            }

            long now = System.currentTimeMillis();
            if (remote != null && now <= remote.updatedAt) now = remote.updatedAt + 1; // stamps must increase
            final String device = deviceName();

            try {
                // 3) Mark the cloud copy as "being replaced" (its old stamp is kept, so a crash here is harmless).
                await(userDoc.set(CloudDocs.meta(json, CloudDocs.STATUS_UPLOADING,
                        remote == null ? 0L : remote.updatedAt, device)));

                // 4) Write changed rows, delete rows that no longer exist on the device.
                FirebaseFirestore db = firestore();
                final int[] done = {0};
                final int total = data.totalRows();
                report(progress, STAGE_UPLOAD, 0, total);
                for (String col : CloudDocs.COLLECTIONS) {
                    syncCollection(db, userDoc.collection(col), rows.get(col), progress, done, total);
                }

                // 5) Only now declare the copy complete, with its new version stamp.
                await(userDoc.set(CloudDocs.meta(json, CloudDocs.STATUS_COMPLETE, now, device)));
            } catch (JSONException e) {
                throw new CloudException("Could not prepare your data for upload: " + e.getMessage(), false, e);
            }

            settings.setCloudBase(uid, now);
            settings.setCloudFingerprint(uid, fingerprint);
            settings.setLastCloudBackupMillis(now);
            return new UploadResult(Outcome.UPLOADED, data.totalRows(), now, remote);
        }
    }

    /**
     * Downloads the cloud copy and validates it exactly like a backup file. Nothing on the device is changed.
     *
     * @throws CloudException  network / permission problems, or no usable cloud copy
     * @throws BackupException the cloud copy is incomplete or damaged
     */
    static BackupData download(Context context) throws CloudException, BackupException {
        return download(context, null);
    }

    /** Same as {@link #download(Context)} but reports progress (collections read so far). */
    static BackupData download(Context context, ProgressListener progress) throws CloudException, BackupException {
        synchronized (LOCK) {
            String uid = requireUid(context);
            DocumentReference userDoc = userDoc(uid);
            DocumentSnapshot metaSnap = await(userDoc.get(Source.SERVER));
            if (!metaSnap.exists()) {
                throw new CloudException("There is no cloud backup for this account yet. "
                        + "Use \"Back up now\" on the device that has your data first.", false);
            }
            Map<String, Object> meta = metaSnap.getData();
            if (meta == null || !CloudDocs.STATUS_COMPLETE.equals(meta.get("status"))) {
                throw new CloudException("The cloud backup is not complete (an upload was interrupted). "
                        + "Open the app on the device that has your data and use \"Back up now\".", false);
            }
            Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
            int collectionsRead = 0;
            for (String col : CloudDocs.COLLECTIONS) {
                report(progress, STAGE_DOWNLOAD, collectionsRead++, CloudDocs.COLLECTIONS.length);
                QuerySnapshot snap = await(userDoc.collection(col).get(Source.SERVER));
                List<Map<String, Object>> list = new ArrayList<>(snap.size());
                for (QueryDocumentSnapshot doc : snap) {
                    Map<String, Object> row = new HashMap<>(doc.getData());
                    if (!row.containsKey("id")) {
                        try {
                            row.put("id", Long.parseLong(doc.getId()));
                        } catch (NumberFormatException e) {
                            throw new BackupException("The cloud backup is damaged (" + col + " has a document named \""
                                    + doc.getId() + "\").");
                        }
                    }
                    list.add(row);
                }
                rows.put(col, list);
            }
            report(progress, STAGE_DOWNLOAD, CloudDocs.COLLECTIONS.length, CloudDocs.COLLECTIONS.length);
            try {
                return BackupJson.parse(CloudDocs.assemble(meta, rows));
            } catch (JSONException e) {
                throw new BackupException("The cloud backup is damaged (" + e.getMessage() + ").", e);
            }
        }
    }

    /** Deletes this account's whole cloud copy. The data on the device is not touched. */
    static void deleteCloudData(Context context) throws CloudException {
        synchronized (LOCK) {
            String uid = requireUid(context);
            DocumentReference userDoc = userDoc(uid);
            FirebaseFirestore db = firestore();
            // Meta first: if this is interrupted, the copy simply counts as "no backup" instead of "half a backup".
            await(userDoc.delete());
            for (String col : CloudDocs.COLLECTIONS) {
                QuerySnapshot snap = await(userDoc.collection(col).get(Source.SERVER));
                WriteBatch batch = db.batch();
                int pending = 0;
                for (QueryDocumentSnapshot doc : snap) {
                    batch.delete(doc.getReference());
                    if (++pending >= BATCH_SIZE) {
                        await(batch.commit());
                        batch = db.batch();
                        pending = 0;
                    }
                }
                if (pending > 0) await(batch.commit());
            }
            AppSettings.get(context).clearCloudState(uid);
        }
    }

    // ------------------------------------------------------------------ internals

    private static void report(ProgressListener l, int stage, int done, int total) {
        if (l != null) l.onProgress(stage, done, total);
    }

    private static void syncCollection(FirebaseFirestore db, CollectionReference col,
                                       List<Map<String, Object>> local, ProgressListener progress,
                                       int[] done, int total) throws CloudException {
        QuerySnapshot remoteSnap = await(col.get(Source.SERVER));
        Map<String, Map<String, Object>> remote = new HashMap<>();
        for (QueryDocumentSnapshot doc : remoteSnap) remote.put(doc.getId(), doc.getData());

        Set<String> localIds = new HashSet<>();
        WriteBatch batch = db.batch();
        int pending = 0;
        if (local != null) {
            for (Map<String, Object> row : local) {
                String id = CloudDocs.docId(row);
                localIds.add(id);
                done[0]++;
                if (row.equals(remote.get(id))) continue; // already up to date in the cloud
                batch.set(col.document(id), row);
                if (++pending >= BATCH_SIZE) {
                    await(batch.commit());
                    batch = db.batch();
                    pending = 0;
                    report(progress, STAGE_UPLOAD, done[0], total);
                }
            }
        }
        for (String id : remote.keySet()) {
            if (localIds.contains(id)) continue;
            batch.delete(col.document(id)); // deleted on the device -> deleted in the cloud
            if (++pending >= BATCH_SIZE) {
                await(batch.commit());
                batch = db.batch();
                pending = 0;
            }
        }
        if (pending > 0) await(batch.commit());
        report(progress, STAGE_UPLOAD, done[0], total);
    }

    private static RemoteInfo readInfo(DocumentSnapshot snap) {
        Long updatedAt = snap.getLong("updatedAt");
        Long total = snap.getLong("totalRows");
        String device = snap.getString("device");
        return new RemoteInfo(updatedAt == null ? 0L : updatedAt, device == null ? "" : device,
                total == null ? 0L : total, CloudDocs.STATUS_COMPLETE.equals(snap.getString("status")));
    }

    private static DocumentReference userDoc(String uid) {
        return firestore().collection(USERS).document(uid);
    }

    private static String requireUid(Context context) throws CloudException {
        if (!CloudConfig.isConfigured(context)) {
            throw new CloudException("Firebase is not set up in this build. Add google-services.json "
                    + "(see FIREBASE_SETUP_GUIDE.md).", false);
        }
        String uid = CloudConfig.currentUid(context);
        if (uid == null) throw new CloudException("You are not signed in.", false);
        return uid;
    }

    /**
     * The Firestore instance. The offline write queue is switched off: with it on, a write made without internet
     * would stay queued and be sent later, long after the user saw "failed". Here a failed upload is simply failed.
     */
    static FirebaseFirestore firestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        synchronized (CloudSync.class) {
            if (!settingsApplied) {
                settingsApplied = true;
                try {
                    db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());
                } catch (IllegalStateException ignored) {
                    // Firestore was already started with its defaults; the probe in upload() still protects us.
                }
            }
        }
        return db;
    }

    private static String deviceName() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        if (model.isEmpty()) return maker;
        return model.toLowerCase(java.util.Locale.ROOT).startsWith(maker.toLowerCase(java.util.Locale.ROOT))
                ? model : (maker + " " + model).trim();
    }

    static <T> T await(Task<T> task) throws CloudException {
        try {
            return Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw CloudErrors.wrap(e.getCause() != null ? e.getCause() : e);
        } catch (TimeoutException e) {
            throw new CloudException("The cloud did not answer in time. Check your internet connection and try again.", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CloudException("The operation was interrupted. Please try again.", true, e);
        }
    }
}

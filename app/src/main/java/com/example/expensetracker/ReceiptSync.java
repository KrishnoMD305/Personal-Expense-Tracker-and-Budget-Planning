package com.example.expensetracker;

import android.content.Context;
import android.net.Uri;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stage 7: receipt photos in Firebase Storage, under {@code users/{uid}/receipts/{file}}.
 *
 * <p>The photo is always stored on the device first ({@link ReceiptStore}); uploading is a copy for backup and for
 * other devices. A bill remembers the Storage path in {@link Bill#receiptCloudPath} once the copy exists, so each
 * photo is uploaded once. A failed photo never fails the data backup - it is reported and retried next time.
 * Every method blocks: call from a worker thread only.
 */
final class ReceiptSync {
    private static final long UPLOAD_TIMEOUT_SECONDS = 120;
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 90;

    private ReceiptSync() {}

    /** What a batch of photo transfers did. */
    static final class Result {
        int done;
        int failed;
        /** First error message, or null. */
        String error;
    }

    /** True when Firebase is set up and somebody is signed in. */
    static boolean available(Context context) {
        return CloudConfig.currentUid(context) != null;
    }

    /** Uploads every receipt that is on this device but not yet in this account's Storage. Never throws. */
    static Result uploadPending(Context context, CloudSync.ProgressListener progress) {
        Result result = new Result();
        final Context app = context.getApplicationContext();
        final String uid = CloudConfig.currentUid(app);
        if (uid == null) return result;
        try {
            AppDatabase db = AppDatabase.getInstance(app);
            List<Bill> pending = new ArrayList<>();
            for (Bill b : db.billDao().getWithReceipt()) {
                if (!ReceiptNames.isValid(b.receiptFile)) continue;
                if (ReceiptNames.isUploadedFor(uid, b.receiptFile, b.receiptCloudPath)) continue;
                if (!ReceiptStore.exists(app, b.receiptFile)) continue; // the photo is not on this device
                pending.add(b);
            }
            int index = 0;
            boolean storageUsable = true; // flips to false once Cloud Storage turns out not to work for this project
            for (Bill b : pending) {
                if (progress != null) progress.onProgress(CloudSync.STAGE_RECEIPTS, index, pending.size());
                String path = ReceiptNames.cloudPath(uid, b.receiptFile);
                File local = ReceiptStore.file(app, b.receiptFile);
                CloudException failure = null;
                boolean stored = false;
                if (storageUsable) {
                    try {
                        upload(app, path, local);
                        stored = true;
                    } catch (CloudException e) {
                        failure = e;
                        if (isStorageUnavailable(e)) storageUsable = false;
                    }
                }
                if (!stored && (!storageUsable)) {
                    // Cloud Storage is missing / not enabled (Firebase answers "404 not found", which used to be
                    // shown as "the receipt may have been deleted"). Keep the photo in Firestore instead.
                    try {
                        uploadToFirestore(uid, b.receiptFile, local);
                        stored = true;
                        failure = null;
                    } catch (CloudException e) {
                        failure = e;
                    }
                }
                if (stored) {
                    db.billDao().setReceiptCloudPath(b.id, b.receiptFile, path);
                    result.done++;
                } else {
                    result.failed++;
                    if (result.error == null && failure != null) result.error = failure.getMessage();
                }
                index++;
            }
            if (progress != null && !pending.isEmpty()) {
                progress.onProgress(CloudSync.STAGE_RECEIPTS, pending.size(), pending.size());
            }
        } catch (RuntimeException e) {
            result.failed++;
            if (result.error == null) result.error = CloudErrors.describe(e);
        }
        return result;
    }

    /** After a restore: fetches photos that bills point to but that are not on this device. Never throws. */
    static Result downloadMissing(Context context, CloudSync.ProgressListener progress) {
        Result result = new Result();
        final Context app = context.getApplicationContext();
        final String uid = CloudConfig.currentUid(app);
        if (uid == null) return result;
        try {
            List<Bill> missing = new ArrayList<>();
            for (Bill b : AppDatabase.getInstance(app).billDao().getWithReceipt()) {
                if (ReceiptNames.isUploadedFor(uid, b.receiptFile, b.receiptCloudPath)
                        && !ReceiptStore.exists(app, b.receiptFile)) {
                    missing.add(b);
                }
            }
            int index = 0;
            for (Bill b : missing) {
                if (progress != null) progress.onProgress(CloudSync.STAGE_RECEIPTS, index, missing.size());
                try {
                    downloadAny(app, uid, b, ReceiptStore.file(app, b.receiptFile));
                    result.done++;
                } catch (CloudException e) {
                    result.failed++;
                    if (result.error == null) result.error = e.getMessage();
                }
                index++;
            }
            if (progress != null && !missing.isEmpty()) {
                progress.onProgress(CloudSync.STAGE_RECEIPTS, missing.size(), missing.size());
            }
        } catch (RuntimeException e) {
            result.failed++;
            if (result.error == null) result.error = CloudErrors.describe(e);
        }
        return result;
    }

    /** Makes sure one receipt is on the device (downloads it if it is only in the cloud). */
    static void ensureLocal(Context context, Bill bill) throws CloudException {
        final Context app = context.getApplicationContext();
        if (!ReceiptNames.isValid(bill.receiptFile)) throw new CloudException("This bill has no receipt.", false);
        if (ReceiptStore.exists(app, bill.receiptFile)) return;
        String uid = CloudConfig.currentUid(app);
        if (uid == null || !ReceiptNames.isUploadedFor(uid, bill.receiptFile, bill.receiptCloudPath)) {
            throw new CloudException("The receipt photo is not on this device. Sign in to the account it was "
                    + "backed up with to download it.", false);
        }
        downloadAny(app, uid, bill, ReceiptStore.file(app, bill.receiptFile));
    }

    /** Best-effort removal of an uploaded photo (the user replaced or deleted it). Never throws. */
    static void deleteRemote(Context context, String cloudPath) {
        if (!ReceiptNames.isValidCloudPathOrEmpty(cloudPath) || cloudPath.isEmpty()) return;
        String uid = CloudConfig.currentUid(context);
        if (uid == null || !cloudPath.startsWith("users/" + uid + "/")) return; // never touch another account's files
        try {
            FirebaseStorage.getInstance().getReference().child(cloudPath).delete();
        } catch (RuntimeException ignored) {
            // an orphaned photo in Storage is harmless
        }
        try {
            String file = cloudPath.substring(cloudPath.lastIndexOf('/') + 1);
            receiptDoc(uid, file).delete(); // the Firestore copy (used when Cloud Storage is not available)
        } catch (RuntimeException ignored) {
            // harmless as well
        }
    }

    // ------------------------------------------------------------------ Firestore fallback

    /** Firestore documents are limited to 1 MiB; keep the photo well below that. */
    private static final int FALLBACK_MAX_BYTES = 700 * 1024;

    private static DocumentReference receiptDoc(String uid, String file) {
        return CloudSync.firestore().collection("users").document(uid)
                .collection("receipts").document(file);
    }

    /** True when the failure means "Cloud Storage cannot be used at all for this project" (not a one-off glitch). */
    static boolean isStorageUnavailable(CloudException e) {
        Throwable c = e.getCause();
        if (c instanceof StorageException) {
            int code = ((StorageException) c).getErrorCode();
            return code == StorageException.ERROR_OBJECT_NOT_FOUND
                    || code == StorageException.ERROR_BUCKET_NOT_FOUND
                    || code == StorageException.ERROR_PROJECT_NOT_FOUND
                    || code == StorageException.ERROR_NOT_AUTHORIZED;
        }
        String m = e.getMessage();
        return m != null && m.startsWith("Cloud Storage is not set up");
    }

    private static byte[] readForFallback(File file) throws CloudException {
        if (file == null || !file.isFile()) throw new CloudException("The receipt photo is missing.", false);
        try {
            byte[] bytes = readAll(file);
            if (bytes.length <= FALLBACK_MAX_BYTES) return bytes;
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp == null) throw new CloudException("The receipt photo could not be read.", false);
            try {
                int quality = 75;
                float scale = 1f;
                while (true) {
                    Bitmap work = scale == 1f ? bmp : Bitmap.createScaledBitmap(bmp,
                            Math.max(1, Math.round(bmp.getWidth() * scale)),
                            Math.max(1, Math.round(bmp.getHeight() * scale)), true);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    work.compress(Bitmap.CompressFormat.JPEG, quality, out);
                    if (work != bmp) work.recycle();
                    if (out.size() <= FALLBACK_MAX_BYTES || (quality <= 40 && scale < 0.3f)) return out.toByteArray();
                    if (quality > 40) quality -= 10; else scale *= 0.8f;
                }
            } finally {
                bmp.recycle();
            }
        } catch (IOException e) {
            throw new CloudException("The receipt photo could not be read.", false, e);
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void uploadToFirestore(String uid, String fileName, File file) throws CloudException {
        byte[] bytes = readForFallback(file);
        Map<String, Object> doc = new HashMap<>();
        doc.put("data", Blob.fromBytes(bytes));
        doc.put("size", (long) bytes.length);
        doc.put("updatedAt", System.currentTimeMillis());
        try {
            CloudSync.await(receiptDoc(uid, fileName).set(doc));
        } catch (RuntimeException e) {
            throw CloudErrors.wrap(e);
        }
    }

    /** Downloads from Cloud Storage; if the photo is not there, from the Firestore copy. */
    private static void downloadAny(Context app, String uid, Bill bill, File target) throws CloudException {
        try {
            download(app, bill.receiptCloudPath, target);
            return;
        } catch (CloudException storageError) {
            if (target == null) throw storageError;
            try {
                DocumentSnapshot snap = CloudSync.await(receiptDoc(uid, bill.receiptFile).get());
                Blob blob = snap.exists() ? snap.getBlob("data") : null;
                if (blob == null) throw storageError;
                File temp = new File(target.getParentFile(), target.getName() + ".dl");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    out.write(blob.toBytes());
                    out.getFD().sync();
                } catch (IOException e) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                    throw new CloudException("The receipt could not be saved.", false, e);
                }
                if (!temp.renameTo(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                    throw new CloudException("The receipt could not be saved.", false);
                }
            } catch (RuntimeException e) {
                throw storageError;
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private static void upload(Context app, String path, File file) throws CloudException {
        if (file == null || !file.isFile()) throw new CloudException("The receipt photo is missing.", false);
        try {
            StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);
            StorageMetadata meta = new StorageMetadata.Builder().setContentType("image/jpeg").build();
            StorageTask<?> task = ref.putFile(Uri.fromFile(file), meta);
            awaitTransfer(task, UPLOAD_TIMEOUT_SECONDS);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // e.g. no storage bucket in google-services.json
            throw new CloudException("Cloud Storage is not set up for this Firebase project (" + e.getMessage()
                    + "). See FIREBASE_SETUP_GUIDE.md.", false, e);
        }
    }

    private static void download(Context app, String cloudPath, File target) throws CloudException {
        if (target == null) throw new CloudException("Invalid receipt name.", false);
        File temp = new File(target.getParentFile(), target.getName() + ".dl");
        try {
            StorageReference ref = FirebaseStorage.getInstance().getReference().child(cloudPath);
            awaitTransfer(ref.getFile(temp), DOWNLOAD_TIMEOUT_SECONDS);
            if (!temp.renameTo(target)) throw new CloudException("The receipt could not be saved.", false);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new CloudException("Cloud Storage is not set up for this Firebase project (" + e.getMessage()
                    + "). See FIREBASE_SETUP_GUIDE.md.", false, e);
        } finally {
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    /** Waits for a Storage transfer; on timeout the transfer is cancelled so it cannot finish unseen later. */
    private static void awaitTransfer(Task<?> task, long seconds) throws CloudException {
        try {
            Tasks.await(task, seconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw CloudErrors.wrap(e.getCause() != null ? e.getCause() : e);
        } catch (TimeoutException e) {
            if (task instanceof StorageTask) ((StorageTask<?>) task).cancel();
            throw new CloudException("The receipt transfer took too long. Check your internet connection and try again.", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CloudException("The operation was interrupted. Please try again.", true, e);
        }
    }
}

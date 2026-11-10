package com.example.expensetracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

/**
 * Stage 7: receipt photos stored on the device, in {@code files/receipts/} (private to the app).
 *
 * <p>A picture chosen with the system photo picker, or taken with the camera, is <b>copied</b> here: the picker's
 * {@code content://} link is only valid for a short time and the original may be deleted. The copy is scaled down
 * to {@link ReceiptNames#MAX_SIDE_PX}, rotated upright and re-encoded as JPEG, which also removes location data
 * (EXIF) from the stored file. All methods block - call them from a background thread.
 */
final class ReceiptStore {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReceiptStore() {}

    static File dir(Context context) {
        File dir = new File(context.getFilesDir(), ReceiptNames.LOCAL_DIR);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            // handled by the caller's write: opening a file in a missing folder throws IOException
        }
        return dir;
    }

    /** The file for a stored receipt, or null when {@code name} is not a well-formed receipt name. */
    static File file(Context context, String name) {
        return ReceiptNames.isValid(name) ? new File(dir(context), name) : null;
    }

    static boolean exists(Context context, String name) {
        File f = file(context, name);
        return f != null && f.isFile() && f.length() > 0;
    }

    /** A content:// link that other apps (the image viewer) may read for a moment. */
    static Uri shareUri(Context context, String name) {
        File f = file(context, name);
        if (f == null) throw new IllegalArgumentException("Invalid receipt name");
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f);
    }

    /** Where the camera app writes the photo before {@link #importFrom} copies it into the store. */
    static File captureTarget(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "receipt_capture");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new IOException("Cannot create " + dir);
        return new File(dir, "capture.jpg");
    }

    static Uri captureUri(Context context, File target) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", target);
    }

    /** Copies, scales, rotates and saves the picture behind {@code source}. Returns the new receipt file name. */
    static String importFrom(Context context, Uri source) throws IOException {
        // 1) Size only (no pixels in memory yet).
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("The picture could not be opened.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This is not a picture.");

        // 2) Decode a reduced copy.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = ReceiptNames.sampleSize(bounds.outWidth, bounds.outHeight, ReceiptNames.MAX_SIDE_PX);
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("The picture could not be opened.");
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new IOException("This is not a picture.");

        // 3) Upright, using the camera's orientation tag.
        int rotation = 0;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                rotation = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                        : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                        : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            }
        } catch (IOException | RuntimeException ignored) {
            // no readable orientation: keep the picture as it is
        }
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (turned != bitmap) bitmap.recycle();
            bitmap = turned;
        }

        // 4) Save under a fresh name; write to a temp file first so a crash never leaves a half picture.
        String name = ReceiptNames.newName(System.currentTimeMillis(), RANDOM.nextInt(0x10000));
        File target = new File(dir(context), name);
        File temp = new File(dir(context), name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, ReceiptNames.JPEG_QUALITY, out)) {
                throw new IOException("The picture could not be saved.");
            }
            out.getFD().sync();
        } finally {
            bitmap.recycle();
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("The picture could not be saved.");
        }
        return name;
    }

    /** A small bitmap for the editor preview, or null when the file is missing or unreadable. */
    static Bitmap thumbnail(Context context, String name, int maxSidePx) {
        File f = file(context, name);
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = ReceiptNames.sampleSize(bounds.outWidth, bounds.outHeight, maxSidePx);
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }

    /** Deletes a stored receipt (and ignores a missing file or an invalid name). */
    static void delete(Context context, String name) {
        File f = file(context, name);
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}

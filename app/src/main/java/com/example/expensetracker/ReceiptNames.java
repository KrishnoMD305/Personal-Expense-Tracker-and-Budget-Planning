package com.example.expensetracker;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stage 7: naming and validation rules for receipt photos. Pure Java (unit-tested on the JVM).
 *
 * <p>A bill stores only the receipt's <em>file name</em>. The file lives in {@code files/receipts/} and, once
 * uploaded, in Firebase Storage at {@code users/{uid}/receipts/{file name}}. Because a backup file or a cloud
 * document is data that could have been edited by hand, every name read from one is validated here before it is
 * ever turned into a path - a name like {@code ../../databases/expense_tracker.db} is rejected.
 */
final class ReceiptNames {
    /** Sub-folder of {@code getFilesDir()} that holds the photos. */
    static final String LOCAL_DIR = "receipts";
    /** Longest side, in pixels, a stored receipt is scaled down to. */
    static final int MAX_SIDE_PX = 1600;
    /** JPEG quality of a stored receipt. */
    static final int JPEG_QUALITY = 85;

    private static final Pattern FILE = Pattern.compile("^receipt_[0-9]{1,16}_[0-9a-f]{4}\\.jpg$");
    private static final Pattern CLOUD = Pattern.compile("^users/[A-Za-z0-9_\\-]{1,128}/receipts/receipt_[0-9]{1,16}_[0-9a-f]{4}\\.jpg$");

    private ReceiptNames() {}

    /** A fresh, unique file name such as {@code receipt_1762500000000_a3f1.jpg}. */
    static String newName(long nowMillis, int random) {
        return String.format(Locale.US, "receipt_%d_%04x.jpg", Math.max(0, nowMillis), random & 0xFFFF);
    }

    /** True for "no receipt" (empty) or a well-formed file name. */
    static boolean isValidOrEmpty(String file) {
        return file != null && (file.isEmpty() || FILE.matcher(file).matches());
    }

    /** True only for a well-formed, non-empty file name. */
    static boolean isValid(String file) {
        return file != null && FILE.matcher(file).matches();
    }

    static boolean isValidCloudPathOrEmpty(String path) {
        return path != null && (path.isEmpty() || CLOUD.matcher(path).matches());
    }

    /** Where the receipt {@code file} of user {@code uid} lives in Firebase Storage. */
    static String cloudPath(String uid, String file) {
        return "users/" + uid + "/receipts/" + file;
    }

    /** True when the bill's receipt is already in the cloud <em>for this account</em>. */
    static boolean isUploadedFor(String uid, String file, String storedCloudPath) {
        return isValid(file) && uid != null && cloudPath(uid, file).equals(storedCloudPath);
    }

    /** Power-of-two {@code inSampleSize} that brings the longer side to at most {@code maxSide} (never below 1). */
    static int sampleSize(int width, int height, int maxSide) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (maxSide > 0 && longest / (sample * 2) >= maxSide) sample *= 2;
        return sample;
    }
}

package com.example.expensetracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stage 5: small persistent settings. Two files are used on purpose:
 * <ul>
 *   <li>{@code app_settings}: theme, "hide content" and last export/backup times - safe to back up;</li>
 *   <li>{@code security_prefs}: PIN hash, lock state, biometric flag, failed-attempt counters - excluded from
 *       Android auto-backup (see {@code res/xml/backup_rules.xml}) and from the app's own backup file.</li>
 * </ul>
 */
final class AppSettings {
    private static final String FILE = "app_settings";
    private static final String SECURITY_FILE = "security_prefs";

    private static final String K_THEME = "theme";
    private static final String K_HIDE_CONTENT = "hide_content";
    private static final String K_LAST_BACKUP = "last_backup_millis";
    // Stage 6 (Firebase cloud backup). The per-account keys end with the Firebase user id.
    private static final String K_CLOUD_AUTO = "cloud_auto_backup";
    private static final String K_LAST_CLOUD = "last_cloud_backup_millis";
    private static final String K_CLOUD_BASE = "cloud_base_";
    private static final String K_CLOUD_FP = "cloud_fp_";

    private static final String K_LOCK_ENABLED = "lock_enabled";
    private static final String K_PIN_HASH = "pin_hash";
    private static final String K_PIN_SALT = "pin_salt";
    private static final String K_PIN_ITER = "pin_iterations";
    private static final String K_PIN_LEN = "pin_length";
    private static final String K_BIOMETRIC = "biometric_enabled";
    private static final String K_FAILED = "failed_attempts";
    private static final String K_LOCKED_UNTIL = "locked_until_millis";

    private static volatile AppSettings instance;

    private final SharedPreferences prefs;
    private final SharedPreferences security;

    private AppSettings(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        security = app.getSharedPreferences(SECURITY_FILE, Context.MODE_PRIVATE);
    }

    static AppSettings get(Context context) {
        if (instance == null) {
            synchronized (AppSettings.class) {
                if (instance == null) instance = new AppSettings(context);
            }
        }
        return instance;
    }

    // ---- appearance
    String theme() {
        return ThemeManager.normalize(prefs.getString(K_THEME, ThemeManager.DEFAULT));
    }

    void setTheme(String id) {
        prefs.edit().putString(K_THEME, ThemeManager.normalize(id)).apply();
    }

    // ---- privacy
    boolean hideContent() {
        return prefs.getBoolean(K_HIDE_CONTENT, true);
    }

    void setHideContent(boolean hide) {
        prefs.edit().putBoolean(K_HIDE_CONTENT, hide).apply();
    }

    // ---- backup bookkeeping
    long lastBackupMillis() {
        return prefs.getLong(K_LAST_BACKUP, 0L);
    }

    void setLastBackupMillis(long millis) {
        prefs.edit().putLong(K_LAST_BACKUP, millis).apply();
    }

    // ---- Stage 6: cloud backup bookkeeping
    boolean cloudAutoBackup() {
        return prefs.getBoolean(K_CLOUD_AUTO, false);
    }

    void setCloudAutoBackup(boolean enabled) {
        prefs.edit().putBoolean(K_CLOUD_AUTO, enabled).apply();
    }

    long lastCloudBackupMillis() {
        return prefs.getLong(K_LAST_CLOUD, 0L);
    }

    void setLastCloudBackupMillis(long millis) {
        prefs.edit().putLong(K_LAST_CLOUD, millis).apply();
    }

    /** The cloud copy's {@code updatedAt} that this device last uploaded or restored (0 = never). */
    long cloudBase(String uid) {
        return prefs.getLong(K_CLOUD_BASE + uid, 0L);
    }

    void setCloudBase(String uid, long updatedAt) {
        prefs.edit().putLong(K_CLOUD_BASE + uid, updatedAt).apply();
    }

    /** Fingerprint of the data of the last successful upload ("" = none). */
    String cloudFingerprint(String uid) {
        return prefs.getString(K_CLOUD_FP + uid, "");
    }

    void setCloudFingerprint(String uid, String fingerprint) {
        prefs.edit().putString(K_CLOUD_FP + uid, fingerprint == null ? "" : fingerprint).apply();
    }

    void clearCloudState(String uid) {
        prefs.edit().remove(K_CLOUD_BASE + uid).remove(K_CLOUD_FP + uid).apply();
    }

    // ---- app lock
    boolean lockEnabled() {
        return security.getBoolean(K_LOCK_ENABLED, false) && security.getString(K_PIN_HASH, null) != null;
    }

    boolean biometricEnabled() {
        return lockEnabled() && security.getBoolean(K_BIOMETRIC, false);
    }

    void setBiometricEnabled(boolean enabled) {
        security.edit().putBoolean(K_BIOMETRIC, enabled).apply();
    }

    int pinLength() {
        return security.getInt(K_PIN_LEN, 0);
    }

    /** Stores a new PIN (salted, iterated hash - the PIN itself is never stored) and turns the lock on. */
    void savePin(String pin) {
        byte[] salt = PinHasher.newSalt();
        int iterations = PinHasher.DEFAULT_ITERATIONS;
        String hash = PinHasher.hash(pin, salt, iterations);
        security.edit()
                .putBoolean(K_LOCK_ENABLED, true)
                .putString(K_PIN_HASH, hash)
                .putString(K_PIN_SALT, PinHasher.toHex(salt))
                .putInt(K_PIN_ITER, iterations)
                .putInt(K_PIN_LEN, pin.length())
                .putInt(K_FAILED, 0)
                .putLong(K_LOCKED_UNTIL, 0L)
                .apply();
    }

    boolean checkPin(String pin) {
        String hash = security.getString(K_PIN_HASH, null);
        String salt = security.getString(K_PIN_SALT, null);
        if (hash == null || salt == null || pin == null) return false;
        return PinHasher.verify(pin, PinHasher.fromHex(salt), security.getInt(K_PIN_ITER, PinHasher.DEFAULT_ITERATIONS), hash);
    }

    /** Turns the lock off and forgets everything about the PIN. */
    void clearLock() {
        security.edit().clear().apply();
    }

    // ---- brute-force protection (persisted, so restarting the app does not reset it)
    int failedAttempts() {
        return security.getInt(K_FAILED, 0);
    }

    long lockedUntilMillis() {
        return security.getLong(K_LOCKED_UNTIL, 0L);
    }

    void recordFailure(long now) {
        int failed = failedAttempts() + 1;
        long until = lockedUntilMillis();
        long wait = PinPolicy.lockoutMillis(failed);
        if (wait > 0) until = now + wait;
        security.edit().putInt(K_FAILED, failed).putLong(K_LOCKED_UNTIL, until).apply();
    }

    void recordSuccess() {
        security.edit().putInt(K_FAILED, 0).putLong(K_LOCKED_UNTIL, 0L).apply();
    }
}

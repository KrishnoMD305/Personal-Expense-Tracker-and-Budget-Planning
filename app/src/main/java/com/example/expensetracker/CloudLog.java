package com.example.expensetracker;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stage 7: Firebase <b>Realtime Database</b>. Cloud Firestore still holds the backup itself; this small node holds
 * two light-weight things that suit a realtime database:
 * <pre>
 *   users/{uid}/profile              email, device, appVersion, lastSeen (server time), lastBackup
 *   users/{uid}/syncLog/{pushId}     time (server time), action, result, rows, device, message
 * </pre>
 * Every call is best-effort: a failure here (Realtime Database not created yet, no internet, rules not published)
 * must never disturb a backup, so errors are swallowed and the Cloud account screen just shows "no activity".
 */
final class CloudLog {
    static final String ACTION_BACKUP = "backup";
    static final String ACTION_AUTO_BACKUP = "auto-backup";
    static final String ACTION_RESTORE = "restore";
    static final String ACTION_SIGN_IN = "sign-in";
    static final String ACTION_RECEIPT = "receipt";

    static final String RESULT_OK = "ok";
    static final String RESULT_UNCHANGED = "unchanged";
    static final String RESULT_FAILED = "failed";
    static final String RESULT_CONFLICT = "conflict";
    static final String RESULT_DEFERRED = "deferred";

    private static final int MAX_MESSAGE = 200;
    private static final long LOAD_TIMEOUT_MILLIS = 8000;

    private CloudLog() {}

    /** One line of the sync log. */
    static final class Entry {
        final long time;
        final String action;
        final String result;
        final long rows;
        final String device;
        final String message;

        Entry(long time, String action, String result, long rows, String device, String message) {
            this.time = time;
            this.action = action;
            this.result = result;
            this.rows = rows;
            this.device = device;
            this.message = message;
        }
    }

    interface Callback {
        /** Main thread. {@code error} is null on success; entries are newest first. */
        void onLoaded(List<Entry> entries, String error);
    }

    /** Creates / refreshes {@code users/{uid}/profile}. */
    static void touchProfile(Context context) {
        try {
            FirebaseUser user = CloudConfig.currentUser(context);
            DatabaseReference root = userRef(user);
            if (root == null) return;
            Map<String, Object> profile = new HashMap<>();
            profile.put("email", user.getEmail() == null ? "" : user.getEmail());
            profile.put("device", deviceName());
            profile.put("appVersion", BuildConfigInfo.versionName(context));
            profile.put("lastBackup", AppSettings.get(context).lastCloudBackupMillis());
            profile.put("lastSeen", ServerValue.TIMESTAMP);
            root.child("profile").updateChildren(profile);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    /** Appends one entry to {@code users/{uid}/syncLog}. */
    static void log(Context context, String action, String result, long rows, String message) {
        try {
            DatabaseReference root = userRef(CloudConfig.currentUser(context));
            if (root == null) return;
            Map<String, Object> entry = new HashMap<>();
            entry.put("time", ServerValue.TIMESTAMP);
            entry.put("action", action);
            entry.put("result", result);
            entry.put("rows", Math.max(0, rows));
            entry.put("device", deviceName());
            entry.put("message", message == null ? "" : trim(message));
            root.child("syncLog").push().setValue(entry);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    /** Reads the newest {@code limit} entries; always calls back exactly once, on the main thread. */
    static void loadRecent(Context context, int limit, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean delivered = new AtomicBoolean(false);
        final Runnable timeout = () -> {
            if (delivered.compareAndSet(false, true)) {
                callback.onLoaded(Collections.<Entry>emptyList(),
                        "The activity log did not answer. Are you online, and is Realtime Database set up?");
            }
        };
        try {
            DatabaseReference root = userRef(CloudConfig.currentUser(context));
            if (root == null) {
                delivered.set(true);
                callback.onLoaded(Collections.<Entry>emptyList(), null);
                return;
            }
            main.postDelayed(timeout, LOAD_TIMEOUT_MILLIS);
            root.child("syncLog").orderByChild("time").limitToLast(limit)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (!delivered.compareAndSet(false, true)) return;
                            main.removeCallbacks(timeout);
                            List<Entry> list = new ArrayList<>();
                            for (DataSnapshot c : snapshot.getChildren()) {
                                Long time = c.child("time").getValue(Long.class);
                                Long rows = c.child("rows").getValue(Long.class);
                                list.add(new Entry(time == null ? 0 : time,
                                        str(c, "action"), str(c, "result"), rows == null ? 0 : rows,
                                        str(c, "device"), str(c, "message")));
                            }
                            Collections.sort(list, (a, b) -> Long.compare(b.time, a.time));
                            callback.onLoaded(list, null);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            if (!delivered.compareAndSet(false, true)) return;
                            main.removeCallbacks(timeout);
                            callback.onLoaded(Collections.<Entry>emptyList(), describe(error));
                        }
                    });
        } catch (RuntimeException e) {
            main.removeCallbacks(timeout);
            if (delivered.compareAndSet(false, true)) {
                callback.onLoaded(Collections.<Entry>emptyList(),
                        "Realtime Database is not set up for this Firebase project (see FIREBASE_SETUP_GUIDE.md).");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static DatabaseReference userRef(FirebaseUser user) {
        if (user == null) return null;
        return FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
    }

    private static String str(DataSnapshot s, String key) {
        String v = s.child(key).getValue(String.class);
        return v == null ? "" : v;
    }

    private static String describe(DatabaseError error) {
        if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
            return "Realtime Database refused the request. Publish the rules from database.rules.json "
                    + "(see FIREBASE_SETUP_GUIDE.md).";
        }
        return "Realtime Database error: " + error.getMessage();
    }

    private static String trim(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() <= MAX_MESSAGE ? t : t.substring(0, MAX_MESSAGE - 1) + "\u2026";
    }

    private static String deviceName() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        if (model.isEmpty()) return maker;
        return model.toLowerCase(java.util.Locale.ROOT).startsWith(maker.toLowerCase(java.util.Locale.ROOT))
                ? model : (maker + " " + model).trim();
    }
}

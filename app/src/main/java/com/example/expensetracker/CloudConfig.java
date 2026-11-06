package com.example.expensetracker;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Stage 6: the only place that asks "is Firebase set up?" and "who is signed in?".
 *
 * <p>Firebase is configured by {@code app/google-services.json} (downloaded from the Firebase console). When that
 * file is missing the Google services Gradle plugin is not applied, Firebase never starts, and every method here
 * simply reports "not configured" / "nobody" instead of crashing - the rest of the app keeps working offline.
 */
final class CloudConfig {
    private CloudConfig() {}

    static boolean isConfigured(Context context) {
        try {
            return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The signed-in Firebase user, or null (also null when Firebase is not configured). */
    static FirebaseUser currentUser(Context context) {
        if (!isConfigured(context)) return null;
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String currentUid(Context context) {
        FirebaseUser u = currentUser(context);
        return u == null ? null : u.getUid();
    }

    static String currentEmail(Context context) {
        FirebaseUser u = currentUser(context);
        if (u == null) return null;
        String email = u.getEmail();
        return email == null ? "" : email;
    }
}

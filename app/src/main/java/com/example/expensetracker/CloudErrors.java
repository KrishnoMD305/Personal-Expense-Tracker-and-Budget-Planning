package com.example.expensetracker;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Stage 6: turns Firebase exceptions into messages a person can act on. */
final class CloudErrors {
    private CloudErrors() {}

    static String describe(Throwable t) {
        if (t == null) return "Something went wrong. Please try again.";
        if (t instanceof FirebaseNetworkException) {
            return "No internet connection. Check your network and try again.";
        }
        if (t instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        if (t instanceof FirebaseAuthWeakPasswordException) { // must come before InvalidCredentials (its parent)
            return "The password is too weak. Use at least 6 characters.";
        }
        if (t instanceof FirebaseAuthUserCollisionException) {
            return "An account with this email already exists. Sign in instead.";
        }
        if (t instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) t).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code)) return "This account has been disabled.";
            return "Email or password is incorrect.";
        }
        if (t instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) t).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) return "That email address is not valid.";
            return "Email or password is incorrect.";
        }
        if (t instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) t).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code) || "ERROR_CONFIGURATION_NOT_FOUND".equals(code)) {
                return "Email/password sign-in is not enabled for this Firebase project. "
                        + "Enable it in the Firebase console (Authentication > Sign-in method).";
            }
            return "Sign-in problem (" + code + "). " + String.valueOf(t.getMessage());
        }
        if (t instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) t).getCode()) {
                case PERMISSION_DENIED:
                case NOT_FOUND:
                    return "Cloud Firestore refused the request. Make sure the Firestore database has been created "
                            + "and the security rules from firestore.rules are published (see FIREBASE_SETUP_GUIDE.md).";
                case UNAUTHENTICATED:
                    return "Your sign-in has expired. Please sign out and sign in again.";
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return "Could not reach the cloud. Check your internet connection and try again.";
                case RESOURCE_EXHAUSTED:
                    return "The Firebase free quota for today has been used up. Try again tomorrow.";
                default:
                    return "Cloud error (" + ((FirebaseFirestoreException) t).getCode() + "): " + t.getMessage();
            }
        }
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }

    /** True when the same operation may succeed later without the user doing anything. */
    static boolean isRetryable(Throwable t) {
        if (t instanceof FirebaseNetworkException) return true;
        if (t instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code c = ((FirebaseFirestoreException) t).getCode();
            return c == FirebaseFirestoreException.Code.UNAVAILABLE
                    || c == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED;
        }
        return false;
    }

    static CloudException wrap(Throwable t) {
        return new CloudException(describe(t), isRetryable(t), t);
    }
}

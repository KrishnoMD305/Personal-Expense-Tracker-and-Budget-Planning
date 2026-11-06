package com.example.expensetracker;

/** Pure rules for PINs and for the wrong-PIN lock-out (no Android dependencies, unit-tested). */
final class PinPolicy {
    static final int MIN_LENGTH = 4;
    static final int MAX_LENGTH = 6;
    /** After this many wrong PINs in a row the lock screen stops accepting input for a while. */
    static final int FREE_ATTEMPTS = 5;
    static final long BASE_LOCKOUT_MS = 30_000L;
    static final long MAX_LOCKOUT_MS = 5 * 60_000L;

    private PinPolicy() {}

    /** True for 4-6 ASCII digits. */
    static boolean isValid(String pin) {
        if (pin == null) return false;
        int n = pin.length();
        if (n < MIN_LENGTH || n > MAX_LENGTH) return false;
        for (int i = 0; i < n; i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * How long to refuse input after {@code failedAttempts} consecutive wrong PINs: nothing for the first
     * {@link #FREE_ATTEMPTS}-1 failures; then 30 s, doubling with every further failure, capped at 5 minutes.
     * Returns 0 when no lock-out applies.
     */
    static long lockoutMillis(int failedAttempts) {
        if (failedAttempts < FREE_ATTEMPTS) return 0L;
        int doublings = Math.min(failedAttempts - FREE_ATTEMPTS, 10);
        long wait = BASE_LOCKOUT_MS << doublings;
        return Math.min(wait, MAX_LOCKOUT_MS);
    }

    /** Milliseconds left until input is accepted again (0 when not locked out). */
    static long remainingLockoutMillis(long lockedUntilMillis, long now) {
        return Math.max(0L, lockedUntilMillis - now);
    }
}

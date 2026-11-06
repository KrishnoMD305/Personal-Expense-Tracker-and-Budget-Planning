package com.example.expensetracker;

/** Stage 6: a cloud operation that failed. The message is written for the user; {@code retryable} = try again later. */
final class CloudException extends Exception {
    final boolean retryable;

    CloudException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    CloudException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }
}

package com.example.expensetracker;

/** Stage 5: a backup file that cannot be used. The message is written for the user. */
final class BackupException extends Exception {
    BackupException(String message) {
        super(message);
    }

    BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.example.expensetracker;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage 7: the single "cloud backup / restore job" that {@link BackupService} runs, and its observable state.
 *
 * <p>The service and the Cloud account screen live in the same process, so this object is how they talk: the
 * service publishes {@link Status} values (progress, then a final result), the screen observes {@link #status()}
 * and shows a progress dialog / result dialog. At most one job runs at a time.
 */
final class BackupJob {
    enum Kind { BACKUP, RESTORE }

    enum Phase { RUNNING, DONE, FAILED, CONFLICT }

    /** An immutable snapshot of the job. */
    static final class Status {
        final long id;
        final Kind kind;
        final Phase phase;
        /** One of the {@code CloudSync.STAGE_*} constants (0 = not started). */
        final int stage;
        final int done;
        final int total;
        /** Final message for DONE / FAILED (already worded for the user); null while running. */
        final String message;
        /** The cloud copy that is in the way, for CONFLICT. */
        final CloudSync.RemoteInfo remote;

        Status(long id, Kind kind, Phase phase, int stage, int done, int total, String message,
               CloudSync.RemoteInfo remote) {
            this.id = id;
            this.kind = kind;
            this.phase = phase;
            this.stage = stage;
            this.done = done;
            this.total = total;
            this.message = message;
            this.remote = remote;
        }

        Status progress(int stage, int done, int total) {
            return new Status(id, kind, Phase.RUNNING, stage, done, total, null, null);
        }

        Status finished(Phase phase, String message, CloudSync.RemoteInfo remote) {
            return new Status(id, kind, phase, stage, done, total, message, remote);
        }

        boolean isRunning() {
            return phase == Phase.RUNNING;
        }

        /** 0..100, or -1 when the amount of work is not known (indeterminate). */
        int percent() {
            return total <= 0 ? -1 : (int) Math.min(100, Math.max(0, done * 100L / total));
        }
    }

    private static final MutableLiveData<Status> STATUS = new MutableLiveData<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicLong IDS = new AtomicLong(0);
    private static volatile Status current;

    private BackupJob() {}

    static LiveData<Status> status() {
        return STATUS;
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    /** The latest published state, readable from any thread (LiveData.getValue() lags behind postValue). */
    static Status current() {
        return current;
    }

    /** Claims the single job slot. Returns null when a job is already running. */
    static Status begin(Kind kind) {
        if (!RUNNING.compareAndSet(false, true)) return null;
        Status s = new Status(IDS.incrementAndGet(), kind, Phase.RUNNING, 0, 0, 0, null, null);
        publish(s);
        return s;
    }

    /** Publishes a new state (any thread). */
    static void publish(Status s) {
        current = s;
        STATUS.postValue(s);
    }

    /** Releases the job slot. Call after the final {@link #publish}. */
    static void release() {
        RUNNING.set(false);
    }

    /** The screen has shown the final result; forget it so it is not shown again (main thread). */
    static void acknowledge(long id) {
        Status c = current;
        if (c != null && c.id == id && !c.isRunning()) {
            current = null;
            STATUS.setValue(null);
        }
    }

    /** The line that says what the job is doing right now (shared by the notification and the dialog). */
    static String stageText(Context context, Status s) {
        switch (s.stage) {
            case CloudSync.STAGE_CHECK:
                return context.getString(R.string.job_stage_check);
            case CloudSync.STAGE_UPLOAD:
                return context.getString(R.string.job_stage_upload, s.done, s.total);
            case CloudSync.STAGE_DOWNLOAD:
                return context.getString(R.string.job_stage_download, s.done, s.total);
            case CloudSync.STAGE_RESTORE:
                return context.getString(R.string.job_stage_restore);
            case CloudSync.STAGE_RECEIPTS:
                return context.getString(R.string.job_stage_receipts, s.done, s.total);
            default:
                return context.getString(s.kind == Kind.RESTORE ? R.string.job_starting_restore
                        : R.string.job_starting_backup);
        }
    }
}

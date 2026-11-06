package com.example.expensetracker;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Stage 5: the PIN dialogs used by Settings (set up, change, confirm-to-disable). */
final class PinDialogs {
    interface Result {
        /** {@code pin} is the new PIN for set/change; null for a plain confirmation. */
        void done(String pin);
    }

    private PinDialogs() {}

    /** Choose a new PIN (enter twice). {@code onCancel} runs if the user backs out (so a switch can be reverted). */
    static void setNew(BaseActivity a, Result ok, Runnable onCancel) {
        show(a, a.getString(R.string.pin_set_title), a.getString(R.string.pin_set_message), false, true, ok, onCancel);
    }

    /** Ask for the current PIN (used before turning the lock off). */
    static void confirmCurrent(BaseActivity a, Result ok, Runnable onCancel) {
        show(a, a.getString(R.string.pin_confirm_title), a.getString(R.string.pin_confirm_message), true, false, ok, onCancel);
    }

    /** Current PIN + new PIN twice. */
    static void change(BaseActivity a, Result ok) {
        show(a, a.getString(R.string.pin_change_title), a.getString(R.string.pin_change_message), true, true, ok, null);
    }

    private static void show(final BaseActivity a, String title, String message, final boolean askCurrent,
                             final boolean askNew, final Result ok, final Runnable onCancel) {
        final AppSettings settings = AppSettings.get(a);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_pin, null);
        ((TextView) v.findViewById(R.id.pinDialogMessage)).setText(message);
        final TextInputLayout curL = v.findViewById(R.id.currentPinLayout);
        final TextInputLayout newL = v.findViewById(R.id.newPinLayout);
        final TextInputLayout conL = v.findViewById(R.id.confirmPinLayout);
        final TextInputEditText cur = v.findViewById(R.id.currentPin);
        final TextInputEditText nw = v.findViewById(R.id.newPin);
        final TextInputEditText con = v.findViewById(R.id.confirmPin);
        curL.setVisibility(askCurrent ? View.VISIBLE : View.GONE);
        newL.setVisibility(askNew ? View.VISIBLE : View.GONE);
        conL.setVisibility(askNew ? View.VISIBLE : View.GONE);

        final boolean[] finished = {false};
        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(v)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ok, null) // replaced below so errors keep the dialog open
                .create();
        dialog.setOnDismissListener(d -> {
            if (!finished[0] && onCancel != null) onCancel.run();
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            curL.setError(null);
            newL.setError(null);
            conL.setError(null);
            if (askCurrent) {
                long wait = PinPolicy.remainingLockoutMillis(settings.lockedUntilMillis(), System.currentTimeMillis());
                if (wait > 0) {
                    curL.setError(a.getString(R.string.lock_try_again, (int) ((wait + 999) / 1000)));
                    return;
                }
                String entered = text(cur);
                if (!settings.checkPin(entered)) {
                    settings.recordFailure(System.currentTimeMillis());
                    curL.setError(a.getString(R.string.pin_wrong));
                    return;
                }
                settings.recordSuccess();
            }
            String pin = null;
            if (askNew) {
                pin = text(nw);
                if (!PinPolicy.isValid(pin)) {
                    newL.setError(a.getString(R.string.pin_invalid));
                    return;
                }
                if (!pin.equals(text(con))) {
                    conL.setError(a.getString(R.string.pin_mismatch));
                    return;
                }
            }
            finished[0] = true;
            dialog.dismiss();
            ok.done(pin);
        }));
        dialog.show();
    }

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString();
    }
}

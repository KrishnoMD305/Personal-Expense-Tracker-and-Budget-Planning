package com.example.expensetracker;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.math.BigDecimal;

final class GoalDialogs {
    private GoalDialogs() {}

    static void showGoalEditor(AppCompatActivity activity, SavingsGoal existing) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_goal, null);
        TextInputLayout nameLayout = content.findViewById(R.id.goalNameLayout);
        TextInputEditText nameInput = content.findViewById(R.id.goalNameInput);
        TextInputLayout targetLayout = content.findViewById(R.id.goalTargetLayout);
        TextInputEditText targetInput = content.findViewById(R.id.goalTargetInput);
        TextView formError = content.findViewById(R.id.goalFormError);
        DateField deadline = new DateField(activity, content.findViewById(R.id.goalDeadlineButton),
                existing == null ? 0 : existing.deadlineMillis, R.string.goal_no_deadline,
                content.findViewById(R.id.goalDeadlineClear));
        final long originalDeadline = existing == null ? 0 : existing.deadlineMillis;
        if (existing != null) {
            nameInput.setText(existing.name);
            targetInput.setText(BigDecimal.valueOf(existing.targetPaisa, 2).toPlainString());
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? R.string.goal_new : R.string.goal_edit)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null) // real handler below keeps the dialog open on errors
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(v -> {
            nameLayout.setError(null);
            targetLayout.setError(null);
            formError.setVisibility(View.GONE);
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString();
            String nameErr = PlanValidator.validateName(name);
            if (nameErr != null) {
                nameLayout.setError(nameErr);
                return;
            }
            long target;
            try {
                target = MoneyUtils.parseToPaisa(targetInput.getText() == null ? "" : targetInput.getText().toString());
            } catch (MoneyUtils.InvalidAmountException e) {
                targetLayout.setError(e.getMessage());
                return;
            }
            String dateErr = PlanValidator.validateDeadline(deadline.get(), PlanDates.today(),
                    existing == null || deadline.get() != originalDeadline);
            if (dateErr != null) {
                showFormError(formError, dateErr);
                return;
            }
            ok.setEnabled(false);
            GoalRepository.save(activity, existing == null ? 0 : existing.id, name, target, deadline.get(), err -> {
                if (err == null) {
                    dialog.dismiss();
                    Toast.makeText(activity, R.string.goal_saved, Toast.LENGTH_SHORT).show();
                } else {
                    ok.setEnabled(true);
                    showFormError(formError, err);
                }
            });
        });
        nameInput.requestFocus();
    }

    static void showContribution(AppCompatActivity activity, long goalId) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_contribution, null);
        TextInputLayout amountLayout = content.findViewById(R.id.contribAmountLayout);
        TextInputEditText amountInput = content.findViewById(R.id.contribAmountInput);
        TextInputEditText noteInput = content.findViewById(R.id.contribNoteInput);
        DateField date = new DateField(activity, content.findViewById(R.id.contribDateButton),
                PlanDates.today(), R.string.date, null);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.goal_add_contribution)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(v -> {
            amountLayout.setError(null);
            long paisa;
            try {
                paisa = MoneyUtils.parseToPaisa(amountInput.getText() == null ? "" : amountInput.getText().toString());
            } catch (MoneyUtils.InvalidAmountException e) {
                amountLayout.setError(e.getMessage());
                return;
            }
            ok.setEnabled(false);
            String note = noteInput.getText() == null ? "" : noteInput.getText().toString();
            GoalRepository.addContribution(activity, goalId, paisa, date.get(), note, err -> {
                if (err == null) {
                    dialog.dismiss();
                    Toast.makeText(activity, R.string.goal_contribution_added, Toast.LENGTH_SHORT).show();
                } else {
                    ok.setEnabled(true);
                    amountLayout.setError(err);
                }
            });
        });
        amountInput.requestFocus();
    }

    private static void showFormError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(View.VISIBLE);
    }
}
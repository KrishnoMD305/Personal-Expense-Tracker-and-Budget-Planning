package com.example.expensetracker;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class GoalDetailActivity extends BaseActivity {
    public static final String EXTRA_GOAL_ID = "goal_id";

    private long goalId;
    private GoalWithSaved current;
    private PlanRowAdapter adapter;
    private TextView nameView, percentView, line1, line2, line3, emptyView;
    private LinearProgressIndicator progress;
    private Button archiveButton;
    private boolean everLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_detail);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        goalId = getIntent().getLongExtra(EXTRA_GOAL_ID, 0);
        if (goalId == 0) {
            finish();
            return;
        }
        nameView = findViewById(R.id.goalName);
        percentView = findViewById(R.id.goalPercent);
        line1 = findViewById(R.id.goalLine1);
        line2 = findViewById(R.id.goalLine2);
        line3 = findViewById(R.id.goalLine3);
        emptyView = findViewById(R.id.contributionEmpty);
        progress = findViewById(R.id.goalProgress);
        archiveButton = findViewById(R.id.archiveGoalButton);

        RecyclerView list = findViewById(R.id.contributionList);
        adapter = new PlanRowAdapter(row -> confirmDeleteContribution((GoalContribution) row.tag));
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.addContributionButton).setOnClickListener(v -> {
            if (current != null) GoalDialogs.showContribution(this, goalId);
        });
        findViewById(R.id.editGoalButton).setOnClickListener(v -> {
            if (current != null) GoalDialogs.showGoalEditor(this, current.goal);
        });
        archiveButton.setOnClickListener(v -> toggleArchive());
        findViewById(R.id.deleteGoalButton).setOnClickListener(v -> confirmDeleteGoal());

        GoalRepository.observeOne(this, goalId).observe(this, g -> {
            if (g == null) {
                if (everLoaded) finish(); // deleted
                else {
                    Toast.makeText(this, R.string.goal_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }
            everLoaded = true;
            current = g;
            renderHeader();
        });
        GoalRepository.observeContributions(this, goalId).observe(this, this::renderHistory);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current != null) renderHeader();
    }

    private void renderHeader() {
        GoalStatus s = GoalCalculator.status(current, PlanDates.today());
        int color = PlanText.goalColor(this, s);
        setTitle(current.goal.name);
        nameView.setText(current.goal.name);
        percentView.setText(s.isCompleted() ? getString(R.string.goal_completed) : s.percentText + "%");
        percentView.setTextColor(color);
        progress.setIndicatorColor(color);
        progress.setProgressCompat(s.progress, false);
        line1.setText(getString(R.string.goal_saved_of, MoneyUtils.format(s.savedPaisa), MoneyUtils.format(s.targetPaisa)));
        if (s.isCompleted()) {
            line2.setText(s.excessPaisa > 0
                    ? getString(R.string.goal_reached_over, MoneyUtils.format(s.excessPaisa))
                    : getString(R.string.goal_reached));
        } else {
            line2.setText(getString(R.string.goal_remaining, MoneyUtils.format(s.remainingPaisa)));
        }
        line3.setText(PlanText.goalDeadline(this, s, current.goal.deadlineMillis)
                + (s.suggestedMonthlyPaisa > 0 && !current.goal.archived
                ? "\n" + getString(R.string.goal_suggest_monthly, MoneyUtils.format(s.suggestedMonthlyPaisa)) : ""));
        archiveButton.setText(current.goal.archived ? R.string.goal_restore : R.string.goal_archive);
    }

    private void renderHistory(List<GoalContribution> contributions) {
        List<PlanRow> rows = new ArrayList<>();
        if (contributions != null) {
            int income = ThemeColors.get(this, R.attr.etIncome);
            for (GoalContribution c : contributions) {
                rows.add(PlanRow.item("c:" + c.id, "+ " + MoneyUtils.format(c.amountPaisa),
                        PlanDates.format(c.dateMillis), income, c.note, null,
                        PlanRow.NO_PROGRESS, 0, false, c));
            }
        }
        adapter.submitList(rows);
        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggleArchive() {
        if (current == null) return;
        final boolean archive = !current.goal.archived;
        GoalRepository.setArchived(this, goalId, archive, err -> Toast.makeText(this,
                err != null ? err : getString(archive ? R.string.goal_archived_toast : R.string.goal_restored_toast),
                Toast.LENGTH_SHORT).show());
    }

    private void confirmDeleteContribution(GoalContribution c) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.goal_delete_contribution_title)
                .setMessage(getString(R.string.goal_delete_contribution_message,
                        MoneyUtils.format(c.amountPaisa), PlanDates.format(c.dateMillis)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) ->
                        GoalRepository.deleteContribution(this, c.id, err -> {
                            if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    private void confirmDeleteGoal() {
        if (current == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.goal_delete_title)
                .setMessage(getString(R.string.goal_delete_message, current.goal.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) ->
                        GoalRepository.delete(this, goalId, err -> {
                            if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                        }))
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

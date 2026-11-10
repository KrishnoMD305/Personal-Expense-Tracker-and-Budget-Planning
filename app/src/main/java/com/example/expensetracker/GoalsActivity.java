package com.example.expensetracker;

import com.example.expensetracker.databinding.ActivityPlanListBinding;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/** All savings goals with progress bars; tap one for its contribution history and management. */
public class GoalsActivity extends BaseActivity {
    private ActivityPlanListBinding binding;
    private PlanRowAdapter adapter;
    private TextView emptyView;
    private List<GoalWithSaved> lastGoals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlanListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle(R.string.goals);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        emptyView = binding.planEmpty;
        emptyView.setText(R.string.goals_empty);
        binding.planIntro.setText(R.string.goals_intro);
        RecyclerView list = binding.planList;
        adapter = new PlanRowAdapter(row -> {
            Intent i = new Intent(this, GoalDetailActivity.class);
            i.putExtra(GoalDetailActivity.EXTRA_GOAL_ID, ((GoalWithSaved) row.tag).goal.id);
            startActivity(i);
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        FloatingActionButton fab = binding.planFab;
        fab.setContentDescription(getString(R.string.goal_new));
        fab.setOnClickListener(v -> GoalDialogs.showGoalEditor(this, null));

        GoalRepository.observeAll(this).observe(this, goals -> {
            lastGoals = goals;
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // "days left" changes with the date
    }

    private void render() {
        List<GoalWithSaved> goals = lastGoals;
        List<PlanRow> rows = new ArrayList<>();
        if (goals != null) {
            long today = PlanDates.today();
            boolean headerActive = false, headerArchived = false;
            for (GoalWithSaved g : goals) {
                if (!g.goal.archived && !headerActive) {
                    rows.add(PlanRow.header("h:active", getString(R.string.goals_active)));
                    headerActive = true;
                }
                if (g.goal.archived && !headerArchived) {
                    rows.add(PlanRow.header("h:archived", getString(R.string.goals_archived)));
                    headerArchived = true;
                }
                rows.add(row(g, today));
            }
        }
        adapter.submitList(rows);
        emptyView.setVisibility(goals != null && goals.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private PlanRow row(GoalWithSaved g, long today) {
        GoalStatus s = GoalCalculator.status(g, today);
        int color = PlanText.goalColor(this, s);
        String trailing = s.isCompleted() ? getString(R.string.goal_completed) : s.percentText + "%";
        String line1 = getString(R.string.goal_saved_of, MoneyUtils.format(s.savedPaisa),
                MoneyUtils.format(s.targetPaisa));
        StringBuilder line2 = new StringBuilder(PlanText.goalDeadline(this, s, g.goal.deadlineMillis));
        if (s.suggestedMonthlyPaisa > 0 && !g.goal.archived) {
            line2.append('\n').append(getString(R.string.goal_suggest_monthly,
                    MoneyUtils.format(s.suggestedMonthlyPaisa)));
        }
        return PlanRow.item("goal:" + g.goal.id, g.goal.name, trailing, color, line1, line2.toString(),
                s.progress, color, g.goal.archived, g);
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

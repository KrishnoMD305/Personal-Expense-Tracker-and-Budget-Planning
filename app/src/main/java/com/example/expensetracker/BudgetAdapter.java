package com.example.expensetracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.Objects;

public class BudgetAdapter extends ListAdapter<BudgetAdapter.Row, BudgetAdapter.VH> {

    public static final class Row {
        public final String category;
        public final BudgetStatus status;

        public Row(String category, BudgetStatus status) {
            this.category = category;
            this.status = status;
        }
    }

    public interface OnRowClick {
        void onClick(Row row);
    }

    private final OnRowClick listener;

    public BudgetAdapter(OnRowClick listener) {
        super(new DiffUtil.ItemCallback<Row>() {
            @Override
            public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
                return Objects.equals(a.category, b.category);
            }
            @Override
            public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
                return a.status.budgetPaisa == b.status.budgetPaisa
                        && a.status.spentPaisa == b.status.spentPaisa;
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_budget, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row row = getItem(position);
        BudgetStatus s = row.status;
        Context c = h.itemView.getContext();
        h.category.setText(row.category);
        BudgetUi.bindProgress(h.progress, h.percent, s);
        if (s.hasBudget()) {
            h.progress.setVisibility(View.VISIBLE);
            h.line1.setText(c.getString(R.string.budget_line_spent, MoneyUtils.format(s.spentPaisa), MoneyUtils.format(s.budgetPaisa)));
            h.line2.setText(s.isOverspent() ? c.getString(R.string.budget_line_over, MoneyUtils.format(-s.remainingPaisa), s.percentText) : c.getString(R.string.budget_line_left, MoneyUtils.format(s.remainingPaisa), s.percentText));
            h.line2.setVisibility(View.VISIBLE);
        } else {
            h.progress.setVisibility(View.GONE);
            h.line1.setText(R.string.tap_to_set);
            if (s.spentPaisa > 0) {
                h.line2.setText(c.getString(R.string.budget_line_spent_only, MoneyUtils.format(s.spentPaisa)));
                h.line2.setVisibility(View.VISIBLE);
            } else {
                h.line2.setVisibility(View.GONE);
            }
        }
        h.itemView.setOnClickListener(v -> listener.onClick(row));
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView category, percent, line1, line2;
        final LinearProgressIndicator progress;

        VH(@NonNull View v) {
            super(v);
            category = v.findViewById(R.id.budgetCategory);
            percent = v.findViewById(R.id.budgetPercent);
            progress = v.findViewById(R.id.budgetProgress);
            line1 = v.findViewById(R.id.budgetLine1);
            line2 = v.findViewById(R.id.budgetLine2);
        }
    }
}

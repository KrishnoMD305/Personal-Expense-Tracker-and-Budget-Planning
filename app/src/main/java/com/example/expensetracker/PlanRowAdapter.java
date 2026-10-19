package com.example.expensetracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

public class PlanRowAdapter extends ListAdapter<PlanRow, RecyclerView.ViewHolder> {

    public interface OnRowClick {
        void onClick(PlanRow row);
    }

    private final OnRowClick listener;

    public PlanRowAdapter(OnRowClick listener) {
        super(new DiffUtil.ItemCallback<PlanRow>() {
            @Override
            public boolean areItemsTheSame(@NonNull PlanRow a, @NonNull PlanRow b) {
                return a.key.equals(b.key);
            }

            @Override
            public boolean areContentsTheSame(@NonNull PlanRow a, @NonNull PlanRow b) {
                return a.sameContent(b);
            }
        });
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        if (viewType == PlanRow.HEADER) {
            return new HeaderVH(in.inflate(R.layout.item_plan_header, parent, false));
        }
        return new ItemVH(in.inflate(R.layout.item_plan_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PlanRow row = getItem(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).title.setText(row.title);
            return;
        }
        ItemVH h = (ItemVH) holder;
        h.title.setText(row.title);
        if (row.trailing == null || row.trailing.isEmpty()) {
            h.trailing.setVisibility(View.GONE);
        } else {
            h.trailing.setVisibility(View.VISIBLE);
            h.trailing.setText(row.trailing);
            h.trailing.setTextColor(row.trailingColor);
        }
        if (row.progress == PlanRow.NO_PROGRESS) {
            h.progress.setVisibility(View.GONE);
        } else {
            h.progress.setVisibility(View.VISIBLE);
            h.progress.setIndicatorColor(row.progressColor);
            h.progress.setProgressCompat(row.progress, false);
            h.progress.setContentDescription(row.progress + "%");
        }
        bindLine(h.line1, row.line1);
        bindLine(h.line2, row.line2);
        h.itemView.setAlpha(row.dim ? 0.6f : 1f);
        h.itemView.setOnClickListener(v -> listener.onClick(row));
    }

    private static void bindLine(TextView view, String text) {
        if (text == null || text.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.planHeaderTitle);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        final TextView title, trailing, line1, line2;
        final LinearProgressIndicator progress;

        ItemVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.planTitle);
            trailing = v.findViewById(R.id.planTrailing);
            progress = v.findViewById(R.id.planProgress);
            line1 = v.findViewById(R.id.planLine1);
            line2 = v.findViewById(R.id.planLine2);
        }
    }
}

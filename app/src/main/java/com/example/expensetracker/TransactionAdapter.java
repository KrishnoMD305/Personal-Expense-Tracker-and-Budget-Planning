package com.example.expensetracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class TransactionAdapter extends ListAdapter<Transaction, TransactionAdapter.VH> {

    public interface OnItemClick {
        void onClick(Transaction transaction);
    }
    public interface OnItemLongClick {
        void onLongClick(Transaction transaction);
    }

    private final OnItemClick clickListener;
    private final OnItemLongClick listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public TransactionAdapter(OnItemLongClick listener) {
        this(null, listener);
    }
    public TransactionAdapter(OnItemClick clickListener, OnItemLongClick listener) {
        super(new DiffUtil.ItemCallback<Transaction>() {
            @Override
            public boolean areItemsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                return a.id == b.id;
            }
            @Override
            public boolean areContentsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                return a.amountPaisa == b.amountPaisa && a.dateMillis == b.dateMillis && Objects.equals(a.type, b.type) && Objects.equals(a.category, b.category) && Objects.equals(a.note, b.note);
            }
        });
        this.clickListener = clickListener;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Transaction t = getItem(position);
        h.category.setText(t.category);
        String note = (t.note == null || t.note.trim().isEmpty()) ? "" : " \u2022 " + t.note;
        h.details.setText(dateFormat.format(new Date(t.dateMillis)) + note);
        String sign = t.isIncome() ? "+" : "-";
        h.amount.setText(sign + MoneyUtils.format(t.amountPaisa));
        h.amount.setTextColor(ContextCompat.getColor(h.itemView.getContext(), t.isIncome() ? R.color.income : R.color.expense));
        h.itemView.setOnClickListener(clickListener == null ? null : v -> clickListener.onClick(t));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(t);
            return true;
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView category, details, amount;
        VH(@NonNull View v) {
            super(v);
            category = v.findViewById(R.id.itemCategory);
            details = v.findViewById(R.id.itemDetails);
            amount = v.findViewById(R.id.itemAmount);
        }
    }
}

package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private TextView balanceView, incomeView, expenseView, emptyView;
    private long totalIncome = 0, totalExpense = 0;
    private TransactionAdapter adapter;
    private TransactionDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        balanceView = findViewById(R.id.balanceValue);
        incomeView = findViewById(R.id.incomeValue);
        expenseView = findViewById(R.id.expenseValue);
        emptyView = findViewById(R.id.emptyView);
        RecyclerView recycler = findViewById(R.id.transactionList);
        FloatingActionButton fab = findViewById(R.id.fabAdd);

        dao = AppDatabase.getInstance(this).transactionDao();
        adapter = new TransactionAdapter(this::confirmDelete);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        dao.getAll().observe(this, list -> {
            adapter.submitList(list);
            emptyView.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        dao.sumByType(Transaction.TYPE_INCOME).observe(this, v -> {
            totalIncome = v == null ? 0 : v;
            refreshSummary();
        });
        dao.sumByType(Transaction.TYPE_EXPENSE).observe(this, v -> {
            totalExpense = v == null ? 0 : v;
            refreshSummary();
        });

        fab.setOnClickListener(v -> startActivity(new Intent(this, AddTransactionActivity.class)));
    }

    private void refreshSummary() {
        long balance = totalIncome - totalExpense; // exact: whole paisa
        balanceView.setText(MoneyUtils.format(balance));
        balanceView.setTextColor(ContextCompat.getColor(this,
                balance < 0 ? R.color.expense : R.color.on_primary));
        incomeView.setText(MoneyUtils.format(totalIncome));
        expenseView.setText(MoneyUtils.format(totalExpense));
    }

    private void confirmDelete(Transaction t) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, t.category, MoneyUtils.format(t.amountPaisa)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) ->
                        AppDatabase.WRITE_EXECUTOR.execute(() -> dao.delete(t)))
                .show();
    }
}

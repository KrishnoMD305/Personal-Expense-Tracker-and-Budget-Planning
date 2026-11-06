package com.example.expensetracker;

import android.os.Bundle;

/** Stage 5: what the user asked to export. Survives rotation / process death while the file picker is open. */
final class ExportRequest {
    enum Kind { CSV, PDF, BACKUP }

    final Kind kind;
    /** CSV: the data set. */
    final CsvExport.Dataset dataset;
    /** CSV transactions: date window [startMillis, endMillis). PDF: first day / last day (inclusive) of the report. */
    final long startMillis;
    final long endMillis;
    /** PDF only: a month key ("2026-10") for a whole-month report, or null for a custom range. */
    final String monthKey;

    private ExportRequest(Kind kind, CsvExport.Dataset dataset, long startMillis, long endMillis, String monthKey) {
        this.kind = kind;
        this.dataset = dataset;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.monthKey = monthKey;
    }

    static ExportRequest csv(CsvExport.Dataset dataset, long startMillis, long endMillis) {
        return new ExportRequest(Kind.CSV, dataset, startMillis, endMillis, null);
    }

    static ExportRequest pdf(ReportPeriod period) {
        if (period.type == ReportPeriod.Type.MONTH) {
            return new ExportRequest(Kind.PDF, null, period.startMillis, period.endMillis, period.monthKey);
        }
        return new ExportRequest(Kind.PDF, null, period.startMillis, period.endMillis - 1, null);
    }

    static ExportRequest backup() {
        return new ExportRequest(Kind.BACKUP, null, 0L, 0L, null);
    }

    /** Rebuilds the report period of a PDF request. */
    ReportPeriod period() {
        if (monthKey != null) return ReportPeriod.month(monthKey);
        return ReportPeriod.custom(startMillis, endMillis);
    }

    String mimeType() {
        switch (kind) {
            case PDF:
                return "application/pdf";
            case BACKUP:
                return "application/json";
            default:
                return "text/csv";
        }
    }

    Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString("kind", kind.name());
        if (dataset != null) b.putString("dataset", dataset.name());
        b.putLong("start", startMillis);
        b.putLong("end", endMillis);
        if (monthKey != null) b.putString("month", monthKey);
        return b;
    }

    static ExportRequest fromBundle(Bundle b) {
        if (b == null || b.getString("kind") == null) return null;
        try {
            Kind kind = Kind.valueOf(b.getString("kind"));
            CsvExport.Dataset ds = b.getString("dataset") == null ? null : CsvExport.Dataset.valueOf(b.getString("dataset"));
            return new ExportRequest(kind, ds, b.getLong("start"), b.getLong("end"), b.getString("month"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

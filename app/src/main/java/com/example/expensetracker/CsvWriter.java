package com.example.expensetracker;

import java.io.IOException;

/**
 * Stage 5: minimal RFC 4180 CSV output (comma separated, CRLF line ends, quotes doubled). Pure Java.
 *
 * <p>Text cells that start with {@code = + - @} (or a tab / carriage return) are prefixed with an apostrophe,
 * so a note such as "=HYPERLINK(...)" is shown as text instead of being run as a formula by Excel / Sheets
 * (CSV injection). Numeric cells are written with {@link #number} and are never altered.
 */
final class CsvWriter {
    private final Appendable out;
    private int cellsInRow;

    CsvWriter(Appendable out) {
        this.out = out;
    }

    /** Writes a text cell. */
    CsvWriter text(String value) throws IOException {
        separator();
        out.append(escape(guard(value)));
        return this;
    }

    /** Writes a cell that is already a safe number / date string. */
    CsvWriter number(String value) throws IOException {
        separator();
        out.append(escape(value == null ? "" : value));
        return this;
    }

    void endRow() throws IOException {
        out.append("\r\n");
        cellsInRow = 0;
    }

    private void separator() throws IOException {
        if (cellsInRow++ > 0) out.append(',');
    }

    static String guard(String value) {
        if (value == null || value.isEmpty()) return "";
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') return "'" + value;
        return value;
    }

    static String escape(String value) {
        if (value.isEmpty()) return "";
        boolean needsQuotes = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

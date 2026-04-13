package com.burpext.sessionx.core;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe table model backing the main results JTable.
 * All mutations are dispatched on the EDT so the table renders correctly.
 */
public class TestResultTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "#", "Method", "URL", "Orig. Status", "Orig. Len",
        "Mod. Status", "Mod. Len", "Result"
    };

    private final List<TestResult> rows = new ArrayList<>();

    // ─── Mutation API ─────────────────────────────────────────────────────────

    /** Add a new (pending) result row. Must be called from any thread. */
    public int addResult(TestResult result) {
        final int[] indexHolder = {-1};
        final Runnable r = () -> {
            rows.add(result);
            indexHolder[0] = rows.size() - 1;
            fireTableRowsInserted(indexHolder[0], indexHolder[0]);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try { SwingUtilities.invokeAndWait(r); } catch (Exception ignored) {}
        }
        return indexHolder[0];
    }

    /** Notify the table that a row's modified response has arrived. */
    public void rowUpdated(int rowIndex) {
        SwingUtilities.invokeLater(() -> fireTableRowsUpdated(rowIndex, rowIndex));
    }

    /** Clear all rows. */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            int last = rows.size() - 1;
            rows.clear();
            TestResult.resetCounter();
            if (last >= 0) fireTableRowsDeleted(0, last);
        });
    }

    // ─── AbstractTableModel ───────────────────────────────────────────────────

    @Override public int getRowCount()    { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Object getValueAt(int row, int col) {
        if (row >= rows.size()) return null;
        TestResult r = rows.get(row);
        return switch (col) {
            case 0 -> r.getId();
            case 1 -> r.getMethod();
            case 2 -> r.getUrl();
            case 3 -> r.getOrigStatus();
            case 4 -> r.getOrigLength();
            case 5 -> r.getModStatus() == -1 ? "—" : r.getModStatus();
            case 6 -> r.getModLength() == -1 ? "—" : r.getModLength();
            case 7 -> r.getStatus().toString();
            default -> null;
        };
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 0, 3, 4 -> Integer.class;
            default -> String.class;
        };
    }

    /** Get the TestResult object for the given row. */
    public TestResult getResult(int row) {
        if (row < 0 || row >= rows.size()) return null;
        return rows.get(row);
    }

    public int getResultCount() { return rows.size(); }
}

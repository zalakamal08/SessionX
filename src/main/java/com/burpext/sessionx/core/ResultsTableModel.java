package com.burpext.sessionx.core;

import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

/**
 * Unified, Autorize-style view over the shared {@link TestResultTableModel}.
 *
 * One row per intercepted request, with two independently color-coded verdict
 * columns (Modified &amp; Unauthenticated) so a whole authorization test reads at
 * a glance without duplicate tables.
 */
public class ResultsTableModel extends AbstractTableModel implements TableModelListener {

    public static final int COL_ID = 0, COL_METHOD = 1, COL_URL = 2,
            COL_ORIG = 3, COL_MOD = 4, COL_UNAUTH = 5;

    private static final String[] COLS = { "#", "Method", "URL", "Orig", "Modified", "Unauthenticated" };

    private final TestResultTableModel source;

    public ResultsTableModel(TestResultTableModel source) {
        this.source = source;
        source.addTableModelListener(this);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.INSERT) {
            fireTableRowsInserted(e.getFirstRow(), e.getLastRow());
        } else if (e.getType() == TableModelEvent.UPDATE) {
            fireTableRowsUpdated(e.getFirstRow(), e.getLastRow());
        } else {
            fireTableDataChanged();
        }
    }

    @Override public int getRowCount()    { return source.getResultCount(); }
    @Override public int getColumnCount() { return COLS.length; }
    @Override public String getColumnName(int col) { return COLS[col]; }

    @Override
    public Class<?> getColumnClass(int col) {
        return col == COL_ID ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        TestResult r = source.getResult(row);
        if (r == null) return null;
        return switch (col) {
            case COL_ID     -> r.getId();
            case COL_METHOD -> r.getMethod();
            case COL_URL    -> r.getUrl();
            case COL_ORIG   -> r.getOrigStatus() + "  ·  " + r.getOrigLength() + " B";
            case COL_MOD    -> verdictCell(r.getModStatus(), r.getModLength(), r.getModVulnStatus());
            case COL_UNAUTH -> verdictCell(r.getUnauthStatus(), r.getUnauthLength(), r.getUnauthVulnStatus());
            default -> null;
        };
    }

    private String verdictCell(int status, int length, VulnerabilityStatus v) {
        if (status == -1) return "replaying…";
        return word(v) + "  ·  " + status + "  ·  " + length + " B";
    }

    private static String word(VulnerabilityStatus v) {
        return switch (v) {
            case VULNERABLE  -> "Vulnerable";
            case ENFORCED    -> "Enforced";
            case INTERESTING -> "Interesting";
            default          -> "Pending";
        };
    }

    public TestResult getResult(int row) { return source.getResult(row); }
}

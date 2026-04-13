package com.burpext.sessionx.core;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

/**
 * View model that shows only the "Unauthenticated" columns backed by the shared TestResultTableModel.
 */
public class UnauthTableModel extends AbstractTableModel implements TableModelListener {

    private static final String[] COLS = {
        "#", "Method", "URL", "Orig. Status", "Orig. Len",
        "Unauth. Status", "Unauth. Len", "Unauth. Result"
    };

    private final TestResultTableModel source;

    public UnauthTableModel(TestResultTableModel source) {
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
        return (col == 0 || col == 3 || col == 4) ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int col) {
        TestResult r = source.getResult(row);
        if (r == null) return null;
        return switch (col) {
            case 0 -> r.getId();
            case 1 -> r.getMethod();
            case 2 -> r.getUrl();
            case 3 -> r.getOrigStatus();
            case 4 -> r.getOrigLength();
            case 5 -> r.getUnauthStatus() == -1 ? "—" : r.getUnauthStatus();
            case 6 -> r.getUnauthLength() == -1 ? "—" : r.getUnauthLength();
            case 7 -> r.getUnauthVulnStatus().toString();
            default -> null;
        };
    }

    public TestResult getResult(int row) { return source.getResult(row); }
}

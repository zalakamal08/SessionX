package com.burpext.sessionx.core;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

/**
 * View model that shows only the "Modified" columns backed by the shared TestResultTableModel.
 * Listens to the source model and forwards insert/update events.
 */
public class ModifiedTableModel extends AbstractTableModel implements TableModelListener {

    private static final String[] COLS = {
        "#", "Method", "URL", "Orig. Status", "Orig. Len",
        "Mod. Status", "Mod. Len", "Mod. Result"
    };

    private final TestResultTableModel source;

    public ModifiedTableModel(TestResultTableModel source) {
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
            case 5 -> r.getModStatus() == -1 ? "—" : r.getModStatus();
            case 6 -> r.getModLength() == -1 ? "—" : r.getModLength();
            case 7 -> r.getModVulnStatus().toString();
            default -> null;
        };
    }

    public TestResult getResult(int row) { return source.getResult(row); }
}

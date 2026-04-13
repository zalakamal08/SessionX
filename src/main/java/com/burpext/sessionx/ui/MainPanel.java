package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.engine.RequestReplayer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainPanel extends JPanel {

    private final JToggleButton toggleBtn;
    private final JLabel        statsLabel;
    private final JTable        resultsTable;
    private final ResultDetailPanel detailPanel;

    private final TestResultTableModel tableModel;
    private final RequestReplayer      replayer;

    public MainPanel(MontoyaApi api,
                     TestResultTableModel tableModel,
                     RequestReplayer replayer) {
        this.tableModel  = tableModel;
        this.replayer    = replayer;

        setLayout(new BorderLayout());

        // ── Toolbar ──────────────────────────────────────────────────────────
        toggleBtn = new JToggleButton("SessionX: OFF");
        toggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton clearBtn = new JButton("Clear Table");
        
        statsLabel = new JLabel("  Rows: 0   |   🔴 0   🟢 0   🟡 0");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        toolbar.add(toggleBtn);
        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(statsLabel);

        // ── Results table ─────────────────────────────────────────────────────
        resultsTable = buildResultsTable();
        JScrollPane tableScroll = new JScrollPane(resultsTable);

        // ── Detail panel ──────────────────────────────────────────────────────
        detailPanel = new ResultDetailPanel();

        // ── Split pane ────────────────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailPanel);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerSize(5);

        // ── Root tabbed pane ──────────────────────────────────────────────────
        ConfigPanel configPanel = new ConfigPanel(replayer);

        JTabbedPane rootTabs = new JTabbedPane();
        rootTabs.addTab("Request / Response", splitPane);
        rootTabs.addTab("Configuration", configPanel);

        // ── Assemble ─────────────────────────────────────────────────────────
        add(toolbar,  BorderLayout.NORTH);
        add(rootTabs, BorderLayout.CENTER);

        // ── Table selection listener ──────────────────────────────────────────
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = resultsTable.getSelectedRow();
            if (row == -1) return;
            int modelRow = resultsTable.convertRowIndexToModel(row);
            TestResult result = tableModel.getResult(modelRow);
            detailPanel.show(result);
        });

        // Right-click menu
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });

        // Auto-refresh stats when model changes
        tableModel.addTableModelListener(e -> SwingUtilities.invokeLater(this::refreshStats));

        // ── Toggle button action ──────────────────────────────────────────────
        toggleBtn.addActionListener(e -> {
            boolean on = toggleBtn.isSelected();
            replayer.setActive(on);
            toggleBtn.setText(on ? "SessionX: ON " : "SessionX: OFF");
        });

        // ── Clear button action ───────────────────────────────────────────────
        clearBtn.addActionListener(e -> {
            tableModel.clear();
            detailPanel.clear();
            refreshStats();
        });
    }

    private JTable buildResultsTable() {
        JTable table = new JTable(tableModel);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(35);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(320);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(130);

        TableRowSorter<TestResultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Minimal Cell Renderer for just marking Vulnerable states
        table.setDefaultRenderer(Object.class, new ResultCellRenderer());
        table.setDefaultRenderer(Integer.class, new ResultCellRenderer());

        return table;
    }

    private void refreshStats() {
        int total = 0, vuln = 0, enforced = 0, interesting = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            TestResult r = tableModel.getResult(i);
            if (r == null) continue;
            total++;
            switch (r.getStatus()) {
                case VULNERABLE   -> vuln++;
                case ENFORCED     -> enforced++;
                case INTERESTING  -> interesting++;
            }
        }
        statsLabel.setText(String.format("  Rows: %d   |   🔴 %d   🟢 %d   🟡 %d",
                total, vuln, enforced, interesting));
    }

    private void showContextMenu(MouseEvent e) {
        int row = resultsTable.rowAtPoint(e.getPoint());
        if (row < 0) return;
        resultsTable.setRowSelectionInterval(row, row);
        int modelRow = resultsTable.convertRowIndexToModel(row);
        TestResult result = tableModel.getResult(modelRow);
        if (result == null) return;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(ev -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(result.getUrl()), null);
        });
        menu.add(copyUrl);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private class ResultCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            TestResult result = tableModel.getResult(modelRow);

            if (!isSelected && result != null) {
                VulnerabilityStatus status = result.getStatus();
                if (column == 7) {
                    switch (status) {
                        case VULNERABLE  -> setForeground(new Color(220, 53, 69));
                        case ENFORCED    -> setForeground(new Color(40, 167, 69));
                        case INTERESTING -> setForeground(new Color(255, 153, 0));
                        default          -> setForeground(null);
                    }
                } else {
                    if (status == VulnerabilityStatus.VULNERABLE) {
                        // Light tint foreground to flag vulnerable request rows slightly
                        setForeground(new Color(200, 30, 30)); 
                    } else {
                        setForeground(null);
                    }
                }
            }
            return this;
        }
    }
}

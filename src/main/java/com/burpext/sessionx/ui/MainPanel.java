package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.engine.RequestReplayer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Root panel registered as the "SessionX" Burp Suite tab.
 *
 * Layout:
 * ┌────────────────────────────────────────────────────────────────┐
 * │  Toolbar: [SessionX: OFF] [Clear Table]  rows: 0  Vulnerable: 0│
 * ├─────────────────────────────────┬──────────────────────────────┤
 * │  Results Table                  │  Detail Panel                │
 * │  (left ~60%)                    │  (right ~40%)                │
 * ├─────────────────────────────────┴──────────────────────────────┤
 * │  [Result Table Tab] [Configuration Tab]                        │
 * └────────────────────────────────────────────────────────────────┘
 *
 * The "Result Table" and "Configuration" live in a JTabbedPane at the root.
 */
public class MainPanel extends JPanel {

    // ─── Colors ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK         = new Color(0x1E, 0x1E, 0x2E);
    private static final Color BG_TOOLBAR      = new Color(0x18, 0x18, 0x2B);
    private static final Color BG_TABLE        = new Color(0x1A, 0x1A, 0x2C);
    private static final Color BG_ROW_ALT      = new Color(0x22, 0x22, 0x38);
    private static final Color BG_SEL          = new Color(0x45, 0x47, 0x6B);
    private static final Color FG_TEXT         = new Color(0xCD, 0xD6, 0xF4);
    private static final Color FG_DIM          = new Color(0x6C, 0x70, 0x86);
    private static final Color ACCENT_BLUE     = new Color(0x89, 0xB4, 0xFA);
    private static final Color ACCENT_RED      = new Color(0xF3, 0x8B, 0xA8);
    private static final Color ACCENT_GREEN    = new Color(0xA6, 0xE3, 0xA1);
    private static final Color ACCENT_YELLOW   = new Color(0xF9, 0xE2, 0xAF);
    private static final Color GRID_COLOR      = new Color(0x31, 0x32, 0x44);

    private static final Color COLOR_ON        = new Color(0x40, 0xC0, 0x70);
    private static final Color COLOR_OFF       = new Color(0xF3, 0x8B, 0xA8);

    // ─── Components ───────────────────────────────────────────────────────────
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
        setBackground(BG_DARK);

        // ── Toolbar ──────────────────────────────────────────────────────────
        toggleBtn = buildToggleButton();
        JButton clearBtn = buildClearButton();
        statsLabel = new JLabel("  Rows: 0   |   🔴 0   🟢 0   🟡 0");
        statsLabel.setForeground(FG_DIM);
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        toolbar.setBackground(BG_TOOLBAR);
        toolbar.add(toggleBtn);
        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(statsLabel);

        // ── Results table ─────────────────────────────────────────────────────
        resultsTable = buildResultsTable();

        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setBackground(BG_DARK);
        tableScroll.getViewport().setBackground(BG_TABLE);
        tableScroll.setBorder(BorderFactory.createLineBorder(GRID_COLOR));

        // ── Detail panel ──────────────────────────────────────────────────────
        detailPanel = new ResultDetailPanel();

        // ── Split pane ────────────────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailPanel);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerSize(5);
        splitPane.setBackground(BG_DARK);
        splitPane.setBorder(null);

        // ── Root tabbed pane ──────────────────────────────────────────────────
        ConfigPanel configPanel = new ConfigPanel(replayer);

        JTabbedPane rootTabs = new JTabbedPane();
        rootTabs.setBackground(BG_DARK);
        rootTabs.setForeground(FG_TEXT);
        rootTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rootTabs.addTab("Request/Response Viewers", splitPane);
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
            updateToggleAppearance(on);
        });

        // ── Clear button action ───────────────────────────────────────────────
        clearBtn.addActionListener(e -> {
            tableModel.clear();
            detailPanel.clear();
            refreshStats();
        });
    }

    // ─── UI builders ─────────────────────────────────────────────────────────

    private JToggleButton buildToggleButton() {
        JToggleButton btn = new JToggleButton("SessionX: OFF");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(COLOR_OFF);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_OFF.darker(), 1, true),
                new EmptyBorder(6, 18, 6, 18)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void updateToggleAppearance(boolean on) {
        toggleBtn.setText(on ? "SessionX: ON " : "SessionX: OFF");
        toggleBtn.setBackground(on ? COLOR_ON : COLOR_OFF);
        toggleBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder((on ? COLOR_ON : COLOR_OFF).darker(), 1, true),
                new EmptyBorder(6, 18, 6, 18)));
    }

    private JButton buildClearButton() {
        JButton btn = new JButton("Clear Table");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(FG_TEXT);
        btn.setBackground(new Color(0x31, 0x32, 0x44));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GRID_COLOR, 1, true),
                new EmptyBorder(6, 14, 6, 14)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JTable buildResultsTable() {
        JTable table = new JTable(tableModel);
        table.setBackground(BG_TABLE);
        table.setForeground(FG_TEXT);
        table.setGridColor(GRID_COLOR);
        table.setSelectionBackground(BG_SEL);
        table.setSelectionForeground(FG_TEXT);
        table.setRowHeight(22);
        table.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setIntercellSpacing(new Dimension(8, 1));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);

        // Header
        table.getTableHeader().setBackground(new Color(0x28, 0x28, 0x3E));
        table.getTableHeader().setForeground(ACCENT_BLUE);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
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

        // Sortable
        TableRowSorter<TestResultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Custom cell renderer for color-coded rows
        table.setDefaultRenderer(Object.class, new ResultCellRenderer());
        table.setDefaultRenderer(Integer.class, new ResultCellRenderer());

        return table;
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

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

    // ─── Context menu ─────────────────────────────────────────────────────────

    private void showContextMenu(MouseEvent e) {
        int row = resultsTable.rowAtPoint(e.getPoint());
        if (row < 0) return;
        resultsTable.setRowSelectionInterval(row, row);
        int modelRow = resultsTable.convertRowIndexToModel(row);
        TestResult result = tableModel.getResult(modelRow);
        if (result == null) return;

        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(0x28, 0x28, 0x3E));

        JMenuItem copyUrl = styledMenuItem("Copy URL");
        copyUrl.addActionListener(ev -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(result.getUrl()), null);
        });
        menu.add(copyUrl);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem styledMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(new Color(0x28, 0x28, 0x3E));
        item.setForeground(FG_TEXT);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return item;
    }

    // ─── Cell Renderer ────────────────────────────────────────────────────────

    /**
     * Colors each row based on the vulnerability status in the last column.
     * Alternating row background for readability.
     */
    private class ResultCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            TestResult result = tableModel.getResult(modelRow);

            if (isSelected) {
                setBackground(BG_SEL);
                setForeground(FG_TEXT);
            } else {
                Color rowBg = (row % 2 == 0) ? BG_TABLE : BG_ROW_ALT;
                setBackground(rowBg);

                if (result != null) {
                    VulnerabilityStatus status = result.getStatus();
                    // Color the Result column text
                    if (column == 7) {
                        setForeground(switch (status) {
                            case VULNERABLE  -> ACCENT_RED;
                            case ENFORCED    -> ACCENT_GREEN;
                            case INTERESTING -> ACCENT_YELLOW;
                            default          -> FG_DIM;
                        });
                    } else {
                        // Subtle tint on vulnerable rows
                        if (status == VulnerabilityStatus.VULNERABLE) {
                            setForeground(new Color(0xF3, 0xBB, 0xC8));
                        } else {
                            setForeground(FG_TEXT);
                        }
                    }
                } else {
                    setForeground(FG_TEXT);
                }
            }

            setBorder(new EmptyBorder(1, 8, 1, 8));
            setFont(column == 2
                    ? new Font("JetBrains Mono", Font.PLAIN, 11)
                    : new Font("JetBrains Mono", Font.PLAIN, 12));
            return this;
        }
    }
}

package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.engine.RequestReplayer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;

/**
 * Root panel registered as the "SessionX" Burp Suite tab.
 *
 * Toolbar: [Proxy: OFF] [Repeater: OFF] [Clear]
 * Table  : #, Method, URL, Orig.Status, Orig.Len, Mod.Status, Mod.Len, Unauth.Status, Unauth.Len, Result
 * Detail : Tabbed panel (Original | Modified | Unauthenticated)
 */
public class MainPanel extends JPanel {

    // Highlight colors (Autorize-style)
    private static final Color COLOR_VULN      = new Color(0xFF, 0xCC, 0xCC);  // light red
    private static final Color COLOR_ENFORCED  = new Color(0xCC, 0xFF, 0xCC);  // light green
    private static final Color COLOR_INTEREST  = new Color(0xFF, 0xF0, 0xCC);  // light amber
    private static final Color COLOR_VULN_TXT    = new Color(0xAA, 0x00, 0x00);
    private static final Color COLOR_ENFORCED_TXT= new Color(0x00, 0x66, 0x00);
    private static final Color COLOR_INTEREST_TXT= new Color(0x99, 0x66, 0x00);

    private final MontoyaApi           api;
    private final TestResultTableModel tableModel;
    private final RequestReplayer      replayer;

    private final JToggleButton proxyToggle;
    private final JToggleButton repeaterToggle;
    private final JTable        resultsTable;
    private final ResultDetailPanel detailPanel;

    public MainPanel(MontoyaApi api,
                     TestResultTableModel tableModel,
                     RequestReplayer replayer) {
        this.api        = api;
        this.tableModel = tableModel;
        this.replayer   = replayer;

        setLayout(new BorderLayout());

        // ── Detail panel (initialized first — referenced in lambdas below) ───────
        detailPanel = new ResultDetailPanel();

        // ── Toolbar ──────────────────────────────────────────────────────────
        proxyToggle    = new JToggleButton("Proxy: OFF");
        repeaterToggle = new JToggleButton("Repeater: OFF");
        JButton clearBtn = new JButton("Clear");

        proxyToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repeaterToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        proxyToggle.addActionListener(e -> {
            boolean on = proxyToggle.isSelected();
            replayer.setInterceptProxy(on);
            proxyToggle.setText(on ? "Proxy: ON " : "Proxy: OFF");
        });
        repeaterToggle.addActionListener(e -> {
            boolean on = repeaterToggle.isSelected();
            replayer.setInterceptRepeater(on);
            repeaterToggle.setText(on ? "Repeater: ON " : "Repeater: OFF");
        });
        clearBtn.addActionListener(e -> {
            tableModel.clear();
            detailPanel.clear();
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        toolbar.add(proxyToggle);
        toolbar.add(repeaterToggle);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(clearBtn);
        JLabel scopeNote = new JLabel("  ⚠ Only in-scope requests are tested");
        scopeNote.setForeground(Color.DARK_GRAY);
        scopeNote.setFont(scopeNote.getFont().deriveFont(Font.ITALIC, 11f));
        toolbar.add(scopeNote);

        // ── Results table ─────────────────────────────────────────────────────
        resultsTable = buildResultsTable();
        JScrollPane tableScroll = new JScrollPane(resultsTable);

        // ── Split pane ────────────────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailPanel);
        splitPane.setResizeWeight(0.60);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);

        // ── Tabs ──────────────────────────────────────────────────────────────
        ConfigPanel configPanel = new ConfigPanel(replayer);
        JTabbedPane rootTabs = new JTabbedPane();
        rootTabs.addTab("Request / Response", splitPane);
        rootTabs.addTab("Configuration", configPanel);

        add(toolbar,  BorderLayout.NORTH);
        add(rootTabs, BorderLayout.CENTER);

        // ── Selection listener ────────────────────────────────────────────────
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = resultsTable.getSelectedRow();
            if (row == -1) return;
            int modelRow = resultsTable.convertRowIndexToModel(row);
            detailPanel.show(tableModel.getResult(modelRow));
        });

        // ── Right-click menu ──────────────────────────────────────────────────
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showContextMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showContextMenu(e); }
        });

        // Auto-refresh when model changes
        tableModel.addTableModelListener(e -> SwingUtilities.invokeLater(() -> resultsTable.repaint()));
    }

    // ─── Results table ────────────────────────────────────────────────────────

    private JTable buildResultsTable() {
        JTable table = new JTable(tableModel);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);

        // Column widths
        int[] widths = {35, 60, 0, 70, 65, 75, 65, 85, 65, 190};
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
                if (i != 2) table.getColumnModel().getColumn(i).setMaxWidth(widths[i] * 2);
            }
        }
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMaxWidth(75);

        // Sortable
        TableRowSorter<TestResultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Autorize-style row color renderer
        ResultCellRenderer renderer = new ResultCellRenderer();
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        return table;
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

        // Send ORIGINAL to Repeater
        JMenuItem sendOrigToRepeater = new JMenuItem("Send Original to Repeater");
        sendOrigToRepeater.addActionListener(ev -> {
            if (result.getOrigRequestBytes().length > 0) {
                HttpRequest req = HttpRequest.httpRequest(
                        new String(result.getOrigRequestBytes(), StandardCharsets.UTF_8));
                api.repeater().sendToRepeater(req);
            }
        });
        menu.add(sendOrigToRepeater);

        // Send MODIFIED to Repeater
        JMenuItem sendModToRepeater = new JMenuItem("Send Modified to Repeater");
        sendModToRepeater.addActionListener(ev -> {
            if (result.getModRequestBytes().length > 0) {
                HttpRequest req = HttpRequest.httpRequest(
                        new String(result.getModRequestBytes(), StandardCharsets.UTF_8));
                api.repeater().sendToRepeater(req);
            }
        });
        menu.add(sendModToRepeater);

        // Send UNAUTH to Repeater
        JMenuItem sendUnauthToRepeater = new JMenuItem("Send Unauthenticated to Repeater");
        sendUnauthToRepeater.addActionListener(ev -> {
            if (result.getUnauthRequestBytes().length > 0) {
                HttpRequest req = HttpRequest.httpRequest(
                        new String(result.getUnauthRequestBytes(), StandardCharsets.UTF_8));
                api.repeater().sendToRepeater(req);
            }
        });
        menu.add(sendUnauthToRepeater);

        menu.addSeparator();

        // Copy URL
        JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(ev ->
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(result.getUrl()), null));
        menu.add(copyUrl);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ─── Cell renderer (Autorize-style row coloring) ──────────────────────────

    private class ResultCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            TestResult result = tableModel.getResult(modelRow);

            if (!isSelected && result != null) {
                VulnerabilityStatus modStatus   = result.getModVulnStatus();
                VulnerabilityStatus unauthStatus = result.getUnauthVulnStatus();

                Color bg = null;
                Color fg = null;

                // Highest severity wins (Vulnerable > Interesting > Enforced)
                if (modStatus == VulnerabilityStatus.VULNERABLE ||
                    unauthStatus == VulnerabilityStatus.VULNERABLE) {
                    bg = COLOR_VULN;
                    fg = COLOR_VULN_TXT;
                } else if (modStatus == VulnerabilityStatus.INTERESTING ||
                           unauthStatus == VulnerabilityStatus.INTERESTING) {
                    bg = COLOR_INTEREST;
                    fg = COLOR_INTEREST_TXT;
                } else if (modStatus == VulnerabilityStatus.ENFORCED &&
                           unauthStatus == VulnerabilityStatus.ENFORCED) {
                    bg = COLOR_ENFORCED;
                    fg = COLOR_ENFORCED_TXT;
                }

                if (bg != null) {
                    setBackground(bg);
                    setForeground(fg);
                } else {
                    setBackground(table.getBackground());
                    setForeground(table.getForeground());
                }
            } else if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            }

            // Align numeric columns to center
            int modelCol = column;
            setHorizontalAlignment(modelCol == 0 || modelCol == 3 || modelCol == 4 ||
                                   modelCol == 5 || modelCol == 6 || modelCol == 7 || modelCol == 8
                    ? SwingConstants.CENTER : SwingConstants.LEFT);

            return this;
        }
    }
}

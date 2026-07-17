package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.burpext.sessionx.core.ResultsTableModel;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.engine.RequestReplayer;
import com.burpext.sessionx.io.ResultsExporter;
import com.burpext.sessionx.io.SessionStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class MainPanel extends JPanel {

    // Verdict palette — soft, theme-agnostic tints with dark ink for contrast
    private static final Color BG_VULN     = new Color(0xFB, 0xE1, 0xE3);
    private static final Color BG_ENFORCED = new Color(0xDF, 0xF3, 0xE4);
    private static final Color BG_INTEREST = new Color(0xFB, 0xEF, 0xD6);
    private static final Color BG_PENDING  = new Color(0xEE, 0xEE, 0xEE);
    private static final Color FG_VULN     = new Color(0x9B, 0x1C, 0x28);
    private static final Color FG_ENFORCED = new Color(0x1E, 0x6B, 0x35);
    private static final Color FG_INTEREST = new Color(0x8A, 0x5A, 0x00);
    private static final Color FG_PENDING  = new Color(0x77, 0x77, 0x77);

    private static final Color ZEBRA       = new Color(0xF7, 0xF8, 0xFA);
    private static final Color C_ON        = new Color(0x1E, 0x7E, 0x34);
    private static final Color C_OFF       = new Color(0x6C, 0x75, 0x7D);

    private final MontoyaApi           api;
    private final TestResultTableModel store;
    private final RequestReplayer      replayer;
    private final SessionStore         session;

    private final ResultsTableModel resultsModel;
    private final JTable            table;
    private final TableRowSorter<ResultsTableModel> sorter;
    private final ResultDetailPanel detailPanel;

    private final JToggleButton proxyToggle    = new JToggleButton();
    private final JToggleButton repeaterToggle = new JToggleButton();
    private final JTextField    filterField    = new JTextField(20);
    private final JCheckBox     vulnOnlyCheck   = new JCheckBox("Vulnerable only");
    private final JLabel        statsLabel      = new JLabel();

    public MainPanel(MontoyaApi api, TestResultTableModel store,
                     RequestReplayer replayer, SessionStore session) {
        this.api      = api;
        this.store    = store;
        this.replayer = replayer;
        this.session  = session;

        setLayout(new BorderLayout());

        detailPanel  = new ResultDetailPanel(api);
        resultsModel = new ResultsTableModel(store);
        table        = buildTable();
        sorter       = new TableRowSorter<>(resultsModel);
        table.setRowSorter(sorter);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, detailPanel);
        split.setResizeWeight(0.52);
        split.setDividerSize(6);
        split.setBorder(null);

        JPanel resultsTab = new JPanel(new BorderLayout());
        resultsTab.add(buildToolbar(), BorderLayout.NORTH);
        resultsTab.add(split, BorderLayout.CENTER);

        JTabbedPane rootTabs = new JTabbedPane();
        rootTabs.addTab("Results", resultsTab);
        rootTabs.addTab("Configuration", new ConfigPanel(replayer, session));
        add(rootTabs, BorderLayout.CENTER);

        wireSelection();
        addContextMenu();

        // Reflect any restored toggle state, then keep session + stats in sync
        refreshToggle(proxyToggle, "Proxy", replayer.isInterceptProxy());
        refreshToggle(repeaterToggle, "Repeater", replayer.isInterceptRepeater());
        store.addTableModelListener(e -> { applyFilters(); refreshStats(); });
        refreshStats();
    }

    // ── Toolbar ────────────────────────────────────────────────────────────

    private JComponent buildToolbar() {
        proxyToggle.setSelected(replayer.isInterceptProxy());
        repeaterToggle.setSelected(replayer.isInterceptRepeater());
        proxyToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repeaterToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        proxyToggle.addActionListener(e -> {
            replayer.setInterceptProxy(proxyToggle.isSelected());
            refreshToggle(proxyToggle, "Proxy", proxyToggle.isSelected());
            session.requestSave();
        });
        repeaterToggle.addActionListener(e -> {
            replayer.setInterceptRepeater(repeaterToggle.isSelected());
            refreshToggle(repeaterToggle, "Repeater", repeaterToggle.isSelected());
            session.requestSave();
        });

        filterField.setToolTipText("Filter results by URL");
        filterField.putClientProperty("JTextField.placeholderText", "Filter URL…");
        filterField.getDocument().addDocumentListener((SimpleDoc) e -> applyFilters());
        vulnOnlyCheck.addActionListener(e -> applyFilters());

        JButton clearBtn  = new JButton("Clear");
        JButton exportBtn = new JButton("Export CSV…");
        clearBtn.addActionListener(e -> {
            store.clear();
            detailPanel.clear();
            session.requestSave();
        });
        exportBtn.addActionListener(e -> exportResults());

        statsLabel.setForeground(C_OFF);
        statsLabel.setBorder(new EmptyBorder(0, 6, 0, 10));

        JLabel intercept = new JLabel("Intercept:");
        intercept.setForeground(C_OFF);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(6, 10, 6, 10));
        bar.add(intercept);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(proxyToggle);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(repeaterToggle);
        bar.addSeparator();
        bar.add(Box.createHorizontalStrut(4));
        bar.add(new JLabel("Filter:"));
        bar.add(Box.createHorizontalStrut(6));
        bar.add(filterField);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(vulnOnlyCheck);
        bar.add(Box.createHorizontalGlue());
        bar.add(statsLabel);
        bar.add(clearBtn);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(exportBtn);
        return bar;
    }

    private void refreshToggle(JToggleButton btn, String base, boolean on) {
        btn.setText((on ? "● " : "○ ") + base);
        btn.setForeground(on ? C_ON : C_OFF);
        btn.setToolTipText(base + " interception " + (on ? "ON" : "OFF"));
    }

    // ── Table ──────────────────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(resultsModel);
        t.setRowHeight(26);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);

        JTableHeader header = t.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 28));

        // #, Method, URL, Orig, Modified, Unauthenticated
        int[] widths = {40, 66, 0, 130, 190, 190};
        for (int i = 0; i < widths.length; i++) {
            var col = t.getColumnModel().getColumn(i);
            col.setPreferredWidth(widths[i] > 0 ? widths[i] : 340);
            if (i != ResultsTableModel.COL_URL) col.setMaxWidth(widths[i] * 3);
        }

        VerdictRenderer renderer = new VerdictRenderer();
        for (int i = 0; i < resultsModel.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        return t;
    }

    private void wireSelection() {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow == -1) { detailPanel.clear(); return; }
            TestResult r = resultsModel.getResult(table.convertRowIndexToModel(viewRow));
            detailPanel.show(r);
            int col = table.getSelectedColumn();
            if (col == ResultsTableModel.COL_UNAUTH)   detailPanel.selectTab(2);
            else if (col == ResultsTableModel.COL_ORIG) detailPanel.selectTab(0);
            else                                        detailPanel.selectTab(1);
        });
    }

    private void applyFilters() {
        String text = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase();
        boolean vulnOnly = vulnOnlyCheck.isSelected();
        if (text.isEmpty() && !vulnOnly) { sorter.setRowFilter(null); return; }
        sorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends ResultsTableModel, ? extends Integer> entry) {
                TestResult r = store.getResult(entry.getIdentifier());
                if (r == null) return true;
                if (!text.isEmpty()) {
                    String url = r.getUrl() == null ? "" : r.getUrl().toLowerCase();
                    if (!url.contains(text)) return false;
                }
                if (vulnOnly) {
                    return r.getModVulnStatus() == VulnerabilityStatus.VULNERABLE
                            || r.getUnauthVulnStatus() == VulnerabilityStatus.VULNERABLE;
                }
                return true;
            }
        });
    }

    private void refreshStats() {
        int total = store.getResultCount();
        int vuln = 0;
        for (TestResult r : store.getAll()) {
            if (r.getModVulnStatus() == VulnerabilityStatus.VULNERABLE
                    || r.getUnauthVulnStatus() == VulnerabilityStatus.VULNERABLE) vuln++;
        }
        String base = total + (total == 1 ? " result" : " results");
        statsLabel.setText(vuln > 0
                ? "<html>" + base + " · <font color='#C02A38'><b>" + vuln + " vulnerable</b></font></html>"
                : base);
    }

    // ── Context menu ─────────────────────────────────────────────────────────

    private void addContextMenu() {
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) popup(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popup(e); }
        });
    }

    private void popup(MouseEvent e) {
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) return;
        table.setRowSelectionInterval(viewRow, viewRow);
        TestResult r = resultsModel.getResult(table.convertRowIndexToModel(viewRow));
        if (r == null) return;

        JPopupMenu menu = new JPopupMenu();
        menu.add(repeaterItem("Send Original to Repeater",        r.getOrigRequestBytes()));
        menu.add(repeaterItem("Send Modified to Repeater",        r.getModRequestBytes()));
        menu.add(repeaterItem("Send Unauthenticated to Repeater", r.getUnauthRequestBytes()));
        menu.addSeparator();
        JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(ev ->
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(r.getUrl()), null));
        menu.add(copyUrl);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JMenuItem repeaterItem(String label, byte[] bytes) {
        JMenuItem item = new JMenuItem(label);
        item.setEnabled(bytes != null && bytes.length > 0);
        item.addActionListener(ev -> {
            if (bytes != null && bytes.length > 0) {
                api.repeater().sendToRepeater(
                        HttpRequest.httpRequest(new String(bytes, StandardCharsets.UTF_8)));
            }
        });
        return item;
    }

    private void exportResults() {
        if (store.getResultCount() == 0) {
            JOptionPane.showMessageDialog(this, "No results to export.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Results as CSV");
        chooser.setSelectedFile(new File("sessionx_results.csv"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) file = new File(file.getAbsolutePath() + ".csv");
        try {
            ResultsExporter.exportResultsCsv(store.getAll(), file);
            JOptionPane.showMessageDialog(this, "Exported to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Renderer ─────────────────────────────────────────────────────────────

    private class VerdictRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            TestResult r = store.getResult(table.convertRowIndexToModel(row));

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else if (r != null && column == ResultsTableModel.COL_MOD) {
                paintVerdict(r.getModVulnStatus());
            } else if (r != null && column == ResultsTableModel.COL_UNAUTH) {
                paintVerdict(r.getUnauthVulnStatus());
            } else {
                setBackground(row % 2 == 0 ? table.getBackground() : ZEBRA);
                setForeground(table.getForeground());
            }

            boolean center = column == ResultsTableModel.COL_ID
                    || column == ResultsTableModel.COL_METHOD
                    || column == ResultsTableModel.COL_ORIG;
            setHorizontalAlignment(center ? SwingConstants.CENTER : SwingConstants.LEFT);
            setFont(getFont().deriveFont(
                    (column == ResultsTableModel.COL_MOD || column == ResultsTableModel.COL_UNAUTH)
                            ? Font.BOLD : Font.PLAIN));
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return this;
        }

        private void paintVerdict(VulnerabilityStatus v) {
            switch (v) {
                case VULNERABLE  -> { setBackground(BG_VULN);     setForeground(FG_VULN); }
                case INTERESTING -> { setBackground(BG_INTEREST); setForeground(FG_INTEREST); }
                case ENFORCED    -> { setBackground(BG_ENFORCED); setForeground(FG_ENFORCED); }
                default          -> { setBackground(BG_PENDING);  setForeground(FG_PENDING); }
            }
        }
    }

    /** One-lambda DocumentListener. */
    @FunctionalInterface
    private interface SimpleDoc extends DocumentListener {
        void update(DocumentEvent e);
        @Override default void insertUpdate(DocumentEvent e)  { update(e); }
        @Override default void removeUpdate(DocumentEvent e)  { update(e); }
        @Override default void changedUpdate(DocumentEvent e) { update(e); }
    }
}

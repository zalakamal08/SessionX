package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.burpext.sessionx.core.ModifiedTableModel;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.core.UnauthTableModel;
import com.burpext.sessionx.engine.RequestReplayer;
import com.burpext.sessionx.io.ResultsExporter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

public class MainPanel extends JPanel {

    // Autorize-style row background colors
    private static final Color BG_VULN     = new Color(0xFF, 0xCC, 0xCC);
    private static final Color BG_ENFORCED = new Color(0xCC, 0xFF, 0xCC);
    private static final Color BG_INTEREST = new Color(0xFF, 0xF0, 0xCC);
    private static final Color FG_VULN     = new Color(0x8B, 0x00, 0x00);
    private static final Color FG_ENFORCED = new Color(0x00, 0x55, 0x00);
    private static final Color FG_INTEREST = new Color(0x7A, 0x50, 0x00);

    private static final Color C_ON    = new Color(0x1E, 0x7E, 0x34);
    private static final Color C_OFF   = new Color(0x6C, 0x75, 0x7D);
    private static final Color C_VULN_TXT = new Color(0xC0, 0x2A, 0x38);

    private final MontoyaApi           api;
    private final TestResultTableModel store;
    private final RequestReplayer      replayer;

    private final JToggleButton proxyToggle;
    private final JToggleButton repeaterToggle;
    private final JTextField    filterField      = new JTextField(18);
    private final JCheckBox     vulnOnlyCheck    = new JCheckBox("Vulnerable only");
    private final JLabel        statsLabel       = new JLabel();

    private final ModifiedTableModel modModel;
    private final UnauthTableModel   unauthModel;
    private final JTable             modTable;
    private final JTable             unauthTable;
    private final TableRowSorter<AbstractTableModel> modSorter;
    private final TableRowSorter<AbstractTableModel> unauthSorter;
    final         ResultDetailPanel  detailPanel;

    public MainPanel(MontoyaApi api, TestResultTableModel store, RequestReplayer replayer) {
        this.api      = api;
        this.store    = store;
        this.replayer = replayer;

        setLayout(new BorderLayout());

        // Initialize detail panel first (referenced in lambdas below)
        detailPanel = new ResultDetailPanel(api);

        // View models backed by the same store
        modModel    = new ModifiedTableModel(store);
        unauthModel = new UnauthTableModel(store);

        // Interception toggles
        proxyToggle    = new JToggleButton("Proxy");
        repeaterToggle = new JToggleButton("Repeater");
        proxyToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repeaterToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        styleToggle(proxyToggle, "Proxy");
        styleToggle(repeaterToggle, "Repeater");
        proxyToggle.addActionListener(e -> {
            replayer.setInterceptProxy(proxyToggle.isSelected());
            styleToggle(proxyToggle, "Proxy");
        });
        repeaterToggle.addActionListener(e -> {
            replayer.setInterceptRepeater(repeaterToggle.isSelected());
            styleToggle(repeaterToggle, "Repeater");
        });

        // Filter controls
        filterField.setToolTipText("Filter results by URL");
        filterField.putClientProperty("JTextField.placeholderText", "Filter URL…");
        filterField.getDocument().addDocumentListener((SimpleDocListener) e -> applyFilters());
        vulnOnlyCheck.addActionListener(e -> applyFilters());

        JButton clearBtn  = new JButton("Clear");
        JButton exportBtn = new JButton("Export CSV…");
        clearBtn.addActionListener(e -> { store.clear(); detailPanel.clear(); });
        exportBtn.addActionListener(e -> exportResults());

        statsLabel.setForeground(C_OFF);
        statsLabel.setBorder(new EmptyBorder(0, 4, 0, 8));

        // Toolbar: [ Intercept: Proxy Repeater ] | [ filter  vuln-only ] .... [ stats  Clear Export ]
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(4, 8, 4, 8));
        JLabel interceptLbl = new JLabel("Intercept:");
        interceptLbl.setForeground(C_OFF);
        toolbar.add(interceptLbl);
        toolbar.add(Box.createHorizontalStrut(6));
        toolbar.add(proxyToggle);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(repeaterToggle);
        toolbar.addSeparator();
        toolbar.add(new JLabel("  "));
        toolbar.add(filterField);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(vulnOnlyCheck);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statsLabel);
        toolbar.add(clearBtn);
        toolbar.add(Box.createHorizontalStrut(4));
        toolbar.add(exportBtn);

        // Tables
        modTable    = buildTable(modModel,    false);
        unauthTable = buildTable(unauthModel, true);
        modSorter    = new TableRowSorter<>(modModel);
        unauthSorter = new TableRowSorter<>(unauthModel);
        modTable.setRowSorter(modSorter);
        unauthTable.setRowSorter(unauthSorter);

        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("Modified Results",        new JScrollPane(modTable));
        resultTabs.addTab("Unauthenticated Results", new JScrollPane(unauthTable));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultTabs, detailPanel);
        splitPane.setResizeWeight(0.55);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);

        ConfigPanel configPanel = new ConfigPanel(replayer);
        JTabbedPane rootTabs = new JTabbedPane();
        rootTabs.addTab("Results", splitPane);
        rootTabs.addTab("Configuration", configPanel);

        add(toolbar,  BorderLayout.NORTH);
        add(rootTabs, BorderLayout.CENTER);

        // Selection: pre-select the right detail tab
        modTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = modTable.getSelectedRow();
            if (row == -1) return;
            detailPanel.show(modModel.getResult(modTable.convertRowIndexToModel(row)));
            detailPanel.selectTab(1);
        });
        unauthTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = unauthTable.getSelectedRow();
            if (row == -1) return;
            detailPanel.show(unauthModel.getResult(unauthTable.convertRowIndexToModel(row)));
            detailPanel.selectTab(2);
        });

        // Right-click using method references (satisfies ResultProvider functional interface)
        addContextMenu(modTable,    row -> modModel.getResult(row));
        addContextMenu(unauthTable, row -> unauthModel.getResult(row));

        // Keep stats + filters fresh as results stream in
        store.addTableModelListener(e -> { applyFilters(); refreshStats(); });
        refreshStats();
    }

    // Toggle appearance reflects ON/OFF state clearly
    private void styleToggle(JToggleButton btn, String base) {
        boolean on = btn.isSelected();
        btn.setText((on ? "● " : "○ ") + base);
        btn.setForeground(on ? C_ON : C_OFF);
        btn.setToolTipText(on ? base + " interception ON" : base + " interception OFF");
    }

    private void applyFilters() {
        String text = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase();
        boolean vulnOnly = vulnOnlyCheck.isSelected();
        modSorter.setRowFilter(rowFilter(text, vulnOnly, false));
        unauthSorter.setRowFilter(rowFilter(text, vulnOnly, true));
    }

    private RowFilter<AbstractTableModel, Integer> rowFilter(String text, boolean vulnOnly, boolean unauth) {
        if (text.isEmpty() && !vulnOnly) return null;
        return new RowFilter<>() {
            @Override public boolean include(Entry<? extends AbstractTableModel, ? extends Integer> entry) {
                TestResult r = store.getResult(entry.getIdentifier());
                if (r == null) return true;
                if (!text.isEmpty()) {
                    String url = r.getUrl() == null ? "" : r.getUrl().toLowerCase();
                    if (!url.contains(text)) return false;
                }
                if (vulnOnly) {
                    VulnerabilityStatus s = unauth ? r.getUnauthVulnStatus() : r.getModVulnStatus();
                    return s == VulnerabilityStatus.VULNERABLE;
                }
                return true;
            }
        };
    }

    private void refreshStats() {
        int total = store.getResultCount();
        int vuln = 0;
        for (TestResult r : store.getAll()) {
            if (r.getModVulnStatus() == VulnerabilityStatus.VULNERABLE
                    || r.getUnauthVulnStatus() == VulnerabilityStatus.VULNERABLE) {
                vuln++;
            }
        }
        String base = total + (total == 1 ? " result" : " results");
        if (vuln > 0) {
            statsLabel.setText("<html>" + base
                    + " · <font color='#C02A38'><b>" + vuln + " vulnerable</b></font>&nbsp;&nbsp;</html>");
        } else {
            statsLabel.setText(base + "  ");
        }
    }

    // Build a JTable with correct widths and color renderer (sorter attached by caller)
    private <M extends AbstractTableModel> JTable buildTable(M model, boolean useUnauthColors) {
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(header.getFont().deriveFont(Font.BOLD));

        // #, Method, URL, Orig.Status, Orig.Len, X.Status, X.Len, Result
        int[] widths = {36, 62, 0, 84, 74, 84, 74, 150};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i] > 0 ? widths[i] : 320);
            if (i != 2) table.getColumnModel().getColumn(i).setMaxWidth(widths[i] > 0 ? widths[i] * 2 : Integer.MAX_VALUE);
        }

        RowRenderer renderer = new RowRenderer(useUnauthColors);
        for (int i = 0; i < model.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        return table;
    }

    private void addContextMenu(JTable table, IntFunction<TestResult> resultAt) {
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) showContextMenu(e, table, resultAt); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showContextMenu(e, table, resultAt); }
        });
    }

    private void showContextMenu(MouseEvent e, JTable table, IntFunction<TestResult> resultAt) {
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) return;
        table.setRowSelectionInterval(row, row);
        TestResult result = resultAt.apply(table.convertRowIndexToModel(row));
        if (result == null) return;

        JPopupMenu menu = new JPopupMenu();
        menu.add(repeaterItem("Send Original to Repeater",         result.getOrigRequestBytes()));
        menu.add(repeaterItem("Send Modified to Repeater",         result.getModRequestBytes()));
        menu.add(repeaterItem("Send Unauthenticated to Repeater",  result.getUnauthRequestBytes()));
        menu.addSeparator();
        JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(ev ->
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(result.getUrl()), null));
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

    private class RowRenderer extends DefaultTableCellRenderer {
        private final boolean useUnauth;
        RowRenderer(boolean useUnauth) { this.useUnauth = useUnauth; }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            TestResult result = store.getResult(modelRow);

            if (!isSelected && result != null) {
                VulnerabilityStatus status = useUnauth ? result.getUnauthVulnStatus() : result.getModVulnStatus();
                switch (status) {
                    case VULNERABLE  -> { setBackground(BG_VULN);     setForeground(FG_VULN); }
                    case INTERESTING -> { setBackground(BG_INTEREST);  setForeground(FG_INTEREST); }
                    case ENFORCED    -> { setBackground(BG_ENFORCED);  setForeground(FG_ENFORCED); }
                    default          -> { setBackground(table.getBackground()); setForeground(table.getForeground()); }
                }
            } else if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            }

            boolean numeric = column == 0 || column == 3 || column == 4 || column == 5 || column == 6;
            setHorizontalAlignment(numeric ? SwingConstants.CENTER : SwingConstants.LEFT);
            setBorder(new EmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    /** Convenience: implement all three DocumentListener methods with one lambda. */
    @FunctionalInterface
    private interface SimpleDocListener extends DocumentListener {
        void update(DocumentEvent e);
        @Override default void insertUpdate(DocumentEvent e)  { update(e); }
        @Override default void removeUpdate(DocumentEvent e)  { update(e); }
        @Override default void changedUpdate(DocumentEvent e) { update(e); }
    }
}

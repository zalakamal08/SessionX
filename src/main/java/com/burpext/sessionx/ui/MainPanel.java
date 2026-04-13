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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
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

    private final MontoyaApi           api;
    private final TestResultTableModel store;
    private final RequestReplayer      replayer;

    private final JToggleButton proxyToggle;
    private final JToggleButton repeaterToggle;

    private final ModifiedTableModel modModel;
    private final UnauthTableModel   unauthModel;
    private final JTable             modTable;
    private final JTable             unauthTable;
    final         ResultDetailPanel  detailPanel;

    public MainPanel(MontoyaApi api, TestResultTableModel store, RequestReplayer replayer) {
        this.api      = api;
        this.store    = store;
        this.replayer = replayer;

        setLayout(new BorderLayout());

        // Initialize detail panel first (referenced in lambdas below)
        detailPanel = new ResultDetailPanel();

        // View models backed by the same store
        modModel    = new ModifiedTableModel(store);
        unauthModel = new UnauthTableModel(store);

        // Toolbar
        proxyToggle    = new JToggleButton("Proxy: OFF");
        repeaterToggle = new JToggleButton("Repeater: OFF");
        JButton clearBtn  = new JButton("Clear");
        JButton exportBtn = new JButton("Export Results...");

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
        clearBtn.addActionListener(e -> { store.clear(); detailPanel.clear(); });
        exportBtn.addActionListener(e -> exportResults());

        JLabel scopeNote = new JLabel("  In-scope only");
        scopeNote.setForeground(Color.DARK_GRAY);
        scopeNote.setFont(scopeNote.getFont().deriveFont(Font.ITALIC, 11f));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        toolbar.add(proxyToggle);
        toolbar.add(repeaterToggle);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(clearBtn);
        toolbar.add(exportBtn);
        toolbar.add(scopeNote);

        // Tables
        modTable    = buildTable(modModel,    false);
        unauthTable = buildTable(unauthModel, true);

        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("Modified Results",        new JScrollPane(modTable));
        resultTabs.addTab("Unauthenticated Results", new JScrollPane(unauthTable));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultTabs, detailPanel);
        splitPane.setResizeWeight(0.58);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);

        ConfigPanel configPanel = new ConfigPanel(replayer);
        JTabbedPane rootTabs = new JTabbedPane();
        rootTabs.addTab("Request / Response", splitPane);
        rootTabs.addTab("Configuration",      configPanel);

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
    }

    // Build a JTable with correct widths, sorter, and color renderer
    private <M extends AbstractTableModel> JTable buildTable(M model, boolean useUnauthColors) {
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);

        // #, Method, URL, Orig.Status, Orig.Len, X.Status, X.Len, Result
        int[] widths = {35, 60, 0, 80, 70, 80, 70, 170};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i] > 0 ? widths[i] : 300);
            if (i != 2) table.getColumnModel().getColumn(i).setMaxWidth(widths[i] > 0 ? widths[i] * 2 : Integer.MAX_VALUE);
        }

        table.setRowSorter(new TableRowSorter<>(model));

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

            setHorizontalAlignment(
                    column == 0 || column == 3 || column == 4 || column == 5 || column == 6
                            ? SwingConstants.CENTER : SwingConstants.LEFT);
            return this;
        }
    }
}

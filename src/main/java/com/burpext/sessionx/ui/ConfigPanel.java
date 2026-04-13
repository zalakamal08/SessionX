package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.HeaderRule;
import com.burpext.sessionx.core.HeaderRule.Mode;
import com.burpext.sessionx.engine.RequestReplayer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration tab.
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Header Rules                                              [+] [-]│
 * │  ┌────────────────────────────────────────────────────────────┐ │
 * │  │ ✔ │ Header Name        │ Mode    │ Replacement Value       │ │
 * │  │ ✔ │ Authorization      │ Replace │ Bearer lowprivtoken     │ │
 * │  │ ✔ │ Cookie             │ Remove  │                         │ │
 * │  │ ✔ │ X-Custom-Header    │ Add     │ attacker-value          │ │
 * │  └────────────────────────────────────────────────────────────┘ │
 * │  [Add Row]  [Remove Selected]                                    │
 * │                                                                  │
 * │  Quick-add common headers:                                       │
 * │  [Authorization] [Cookie] [X-Api-Key] [X-Auth-Token] [X-User-Id]│
 * │                                                                  │
 * │  Options                                                         │
 * │  [ ] Intercept requests from Repeater                            │
 * │  [ ] Auto-scroll table                                           │
 * └─────────────────────────────────────────────────────────────────┘
 */
public class ConfigPanel extends JPanel {

    // ─── Colors ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK      = new Color(0xFA, 0xFA, 0xFA);
    private static final Color BG_PANEL     = new Color(0xF3, 0xF3, 0xF5);
    private static final Color BG_TABLE     = Color.WHITE;
    private static final Color FG_TEXT      = new Color(0x20, 0x21, 0x24);
    private static final Color FG_DIM       = new Color(0x5F, 0x63, 0x68);
    private static final Color ACCENT_BLUE  = new Color(0x1A, 0x73, 0xE8);
    private static final Color ACCENT_GREEN = new Color(0x1E, 0x8E, 0x3E);
    private static final Color GRID_COLOR   = new Color(0xDA, 0xDC, 0xE0);

    private static final String[] COMMON_HEADERS = {
        "Authorization", "Cookie", "X-Api-Key", "X-Auth-Token",
        "X-User-Id", "X-User", "X-Forwarded-For", "Token"
    };

    private static final String[] TABLE_COLS = { "✔", "Header Name", "Mode", "Replacement Value" };

    private final RequestReplayer replayer;
    private final DefaultTableModel rulesModel;
    private final JTable rulesTable;

    private final JCheckBox chkRepeater   = styledCheck("Intercept requests from Repeater");
    private final JCheckBox chkAutoScroll = styledCheck("Auto-scroll table");
    private volatile boolean autoScroll   = false;

    public ConfigPanel(RequestReplayer replayer) {
        this.replayer = replayer;
        setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(14, 14, 14, 14));

        // ── Rules table ──────────────────────────────────────────────────────
        rulesModel = new DefaultTableModel(TABLE_COLS, 0) {
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        rulesTable = new JTable(rulesModel);
        styleTable(rulesTable);

        // Mode column — combo box editor
        JComboBox<Mode> modeCombo = new JComboBox<>(Mode.values());
        modeCombo.setBackground(BG_TABLE);
        modeCombo.setForeground(FG_TEXT);
        rulesTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(modeCombo));

        // Column widths
        rulesTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        rulesTable.getColumnModel().getColumn(0).setMaxWidth(30);
        rulesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        rulesTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        rulesTable.getColumnModel().getColumn(3).setPreferredWidth(280);

        JScrollPane tableScroll = new JScrollPane(rulesTable);
        tableScroll.setBackground(BG_DARK);
        tableScroll.getViewport().setBackground(BG_TABLE);
        tableScroll.setBorder(BorderFactory.createLineBorder(GRID_COLOR));

        // ── Table buttons ────────────────────────────────────────────────────
        JButton btnAdd    = accentButton("+ Add Row");
        JButton btnRemove = accentButton("— Remove Selected");

        btnAdd.addActionListener(e -> {
            rulesModel.addRow(new Object[]{ true, "Authorization", Mode.REPLACE, "" });
            syncRules();
        });
        btnRemove.addActionListener(e -> {
            int[] sel = rulesTable.getSelectedRows();
            for (int i = sel.length - 1; i >= 0; i--) rulesModel.removeRow(sel[i]);
            syncRules();
        });

        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tableButtons.setBackground(BG_DARK);
        tableButtons.add(btnAdd);
        tableButtons.add(btnRemove);

        // ── Quick-add buttons ────────────────────────────────────────────────
        JPanel quickAdd = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        quickAdd.setBackground(BG_PANEL);
        quickAdd.setBorder(titledBorder("Quick-add common headers"));

        for (String header : COMMON_HEADERS) {
            JButton btn = pillButton(header);
            btn.addActionListener(e -> {
                rulesModel.addRow(new Object[]{ true, header, Mode.REPLACE, "" });
                syncRules();
            });
            quickAdd.add(btn);
        }

        // ── Rules section assembled ──────────────────────────────────────────
        JPanel rulesSection = new JPanel(new BorderLayout(0, 6));
        rulesSection.setBackground(BG_DARK);
        rulesSection.setBorder(titledBorder("Header Interception Rules"));
        rulesSection.add(tableScroll,  BorderLayout.CENTER);
        rulesSection.add(tableButtons, BorderLayout.SOUTH);

        // ── Options section ──────────────────────────────────────────────────
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBackground(BG_PANEL);
        optionsPanel.setBorder(titledBorder("Options"));
        optionsPanel.add(chkRepeater);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(chkAutoScroll);

        chkRepeater.addActionListener(e -> replayer.setInterceptRepeater(chkRepeater.isSelected()));
        chkAutoScroll.addActionListener(e -> autoScroll = chkAutoScroll.isSelected());

        // ── Sync button ──────────────────────────────────────────────────────
        JButton btnApply = accentButton("Apply Rules");
        btnApply.setBackground(new Color(0xE8, 0xF0, 0xFE));
        btnApply.setForeground(ACCENT_BLUE);
        btnApply.addActionListener(e -> syncRules());

        JPanel applyRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        applyRow.setBackground(BG_DARK);
        applyRow.add(btnApply);

        // ── Assemble ─────────────────────────────────────────────────────────
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setBackground(BG_DARK);
        south.add(quickAdd,    BorderLayout.NORTH);
        south.add(optionsPanel,BorderLayout.CENTER);
        south.add(applyRow,    BorderLayout.SOUTH);

        add(rulesSection, BorderLayout.CENTER);
        add(south,        BorderLayout.SOUTH);

        // Listen for table edits → sync rules
        rulesModel.addTableModelListener(e -> syncRules());

        // Seed with a default Authorization rule
        rulesModel.addRow(new Object[]{ true, "Authorization", Mode.REPLACE, "" });
        syncRules();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public boolean isAutoScroll() { return autoScroll; }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Read all table rows, build HeaderRule objects, push to replayer. */
    private void syncRules() {
        List<HeaderRule> rules = new ArrayList<>();
        for (int i = 0; i < rulesModel.getRowCount(); i++) {
            Boolean enabled = (Boolean) rulesModel.getValueAt(i, 0);
            String  name    = (String)  rulesModel.getValueAt(i, 1);
            Object  modeObj =           rulesModel.getValueAt(i, 2);
            String  value   = (String)  rulesModel.getValueAt(i, 3);

            if (name == null || name.isBlank()) continue;

            Mode mode = (modeObj instanceof Mode m) ? m : Mode.fromString(String.valueOf(modeObj));
            HeaderRule rule = new HeaderRule(name.strip(), mode, value == null ? "" : value);
            rule.setEnabled(enabled != null && enabled);
            rules.add(rule);
        }
        replayer.setRules(rules);
    }

    private void styleTable(JTable t) {
        t.setBackground(BG_TABLE);
        t.setForeground(FG_TEXT);
        t.setGridColor(GRID_COLOR);
        t.setSelectionBackground(new Color(0xE8, 0xF0, 0xFE));
        t.setSelectionForeground(FG_TEXT);
        t.setRowHeight(24);
        t.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        t.getTableHeader().setBackground(BG_PANEL);
        t.getTableHeader().setForeground(ACCENT_BLUE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(8, 1));
    }

    private JButton accentButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(BG_PANEL);
        btn.setForeground(ACCENT_BLUE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_BLUE, 1, true),
                new EmptyBorder(4, 12, 4, 12)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton pillButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(Color.WHITE);
        btn.setForeground(FG_TEXT);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GRID_COLOR, 1, true),
                new EmptyBorder(3, 10, 3, 10)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static JCheckBox styledCheck(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setBackground(BG_PANEL);
        cb.setForeground(FG_TEXT);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cb.setBorder(new EmptyBorder(2, 8, 2, 8));
        return cb;
    }

    private static TitledBorder titledBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0xDA, 0xDC, 0xE0), 1, true),
                title);
        b.setTitleColor(ACCENT_BLUE);
        b.setTitleFont(new Font("Segoe UI", Font.BOLD, 12));
        return b;
    }
}

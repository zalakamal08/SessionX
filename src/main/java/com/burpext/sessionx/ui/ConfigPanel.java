package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.HeaderRule;
import com.burpext.sessionx.core.HeaderRule.Mode;
import com.burpext.sessionx.engine.RequestReplayer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ConfigPanel extends JPanel {

    private static final String[] COMMON_HEADERS = {
        "Authorization", "Cookie", "X-Api-Key", "X-Auth-Token",
        "X-User-Id", "X-User", "X-Forwarded-For", "Token"
    };

    private static final String[] TABLE_COLS = { "✔", "Header Name", "Mode", "Replacement Value" };

    private final RequestReplayer replayer;
    private final DefaultTableModel rulesModel;
    private final JTable rulesTable;

    public ConfigPanel(RequestReplayer replayer) {
        this.replayer = replayer;
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(14, 14, 14, 14));

        // ── Rules table ──────────────────────────────────────────────────────
        rulesModel = new DefaultTableModel(TABLE_COLS, 0) {
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        rulesTable = new JTable(rulesModel);
        rulesTable.setRowHeight(24);
        rulesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // Mode column uses a combobox
        JComboBox<Mode> modeCombo = new JComboBox<>(Mode.values());
        rulesTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(modeCombo));

        rulesTable.getColumnModel().getColumn(0).setPreferredWidth(28);
        rulesTable.getColumnModel().getColumn(0).setMaxWidth(30);
        rulesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        rulesTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        rulesTable.getColumnModel().getColumn(3).setPreferredWidth(320);

        JScrollPane tableScroll = new JScrollPane(rulesTable);

        // ── Table action buttons ──────────────────────────────────────────────
        JButton btnAdd    = new JButton("+ Add Row");
        JButton btnRemove = new JButton("- Remove Selected");
        JButton btnApply  = new JButton("Apply Rules");

        btnAdd.addActionListener(e -> {
            rulesModel.addRow(new Object[]{ true, "Authorization", Mode.REPLACE, "" });
            syncRules();
        });
        btnRemove.addActionListener(e -> {
            int[] sel = rulesTable.getSelectedRows();
            for (int i = sel.length - 1; i >= 0; i--) rulesModel.removeRow(sel[i]);
            syncRules();
        });
        btnApply.addActionListener(e -> syncRules());

        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tableButtons.add(btnAdd);
        tableButtons.add(btnRemove);
        tableButtons.add(btnApply);

        // ── Quick-add header buttons ──────────────────────────────────────────
        JPanel quickAdd = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        quickAdd.setBorder(BorderFactory.createTitledBorder("Quick-add common headers"));
        for (String header : COMMON_HEADERS) {
            JButton btn = new JButton(header);
            btn.addActionListener(e -> {
                rulesModel.addRow(new Object[]{ true, header, Mode.REPLACE, "" });
                syncRules();
            });
            quickAdd.add(btn);
        }

        // ── Rules section ─────────────────────────────────────────────────────
        JPanel rulesSection = new JPanel(new BorderLayout(0, 6));
        rulesSection.setBorder(BorderFactory.createTitledBorder("Header Interception Rules"));
        rulesSection.add(tableScroll,  BorderLayout.CENTER);
        rulesSection.add(tableButtons, BorderLayout.SOUTH);

        // ── How it works label ────────────────────────────────────────────────
        JTextArea helpText = new JTextArea(
                "How SessionX works:\n" +
                "• Modified request  — replaces header values with your configured replacement values (User B token)\n" +
                "• Unauthenticated request — strips the header value to nothing (\"Header:\") to test without any token\n\n" +
                "Configure rules above, then enable interception via the Proxy / Repeater toggles in the toolbar.\n" +
                "Only URLs added to Burp's Target Scope are tested."
        );
        helpText.setEditable(false);
        helpText.setLineWrap(true);
        helpText.setWrapStyleWord(true);
        helpText.setOpaque(false);
        helpText.setFont(helpText.getFont().deriveFont(11.5f));
        helpText.setBorder(new EmptyBorder(6, 4, 4, 4));

        JPanel helpPanel = new JPanel(new BorderLayout());
        helpPanel.setBorder(BorderFactory.createTitledBorder("How it works"));
        helpPanel.add(helpText, BorderLayout.CENTER);

        // ── Assemble ──────────────────────────────────────────────────────────
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(quickAdd,  BorderLayout.NORTH);
        south.add(helpPanel, BorderLayout.CENTER);

        add(rulesSection, BorderLayout.CENTER);
        add(south,        BorderLayout.SOUTH);

        // Auto-sync on table changes
        rulesModel.addTableModelListener(e -> syncRules());

        // Add default Authorization rule on startup
        rulesModel.addRow(new Object[]{ true, "Authorization", Mode.REPLACE, "" });
        syncRules();
    }

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
}

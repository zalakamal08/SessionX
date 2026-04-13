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

    private final JCheckBox chkRepeater   = new JCheckBox("Intercept requests from Repeater");
    private final JCheckBox chkAutoScroll = new JCheckBox("Auto-scroll table");
    private volatile boolean autoScroll   = false;

    public ConfigPanel(RequestReplayer replayer) {
        this.replayer = replayer;
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(14, 14, 14, 14));

        // ── Rules table ──
        rulesModel = new DefaultTableModel(TABLE_COLS, 0) {
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };
        rulesTable = new JTable(rulesModel);
        rulesTable.setRowHeight(24);
        rulesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        JComboBox<Mode> modeCombo = new JComboBox<>(Mode.values());
        rulesTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(modeCombo));

        rulesTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        rulesTable.getColumnModel().getColumn(0).setMaxWidth(30);
        rulesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        rulesTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        rulesTable.getColumnModel().getColumn(3).setPreferredWidth(280);

        JScrollPane tableScroll = new JScrollPane(rulesTable);

        // ── Table buttons ──
        JButton btnAdd    = new JButton("+ Add Row");
        JButton btnRemove = new JButton("- Remove Selected");

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
        tableButtons.add(btnAdd);
        tableButtons.add(btnRemove);

        // ── Quick-add buttons ──
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

        // ── Rules section assembled ──
        JPanel rulesSection = new JPanel(new BorderLayout(0, 6));
        rulesSection.setBorder(BorderFactory.createTitledBorder("Header Interception Rules"));
        rulesSection.add(tableScroll,  BorderLayout.CENTER);
        rulesSection.add(tableButtons, BorderLayout.SOUTH);

        // ── Options section ──
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
        optionsPanel.add(chkRepeater);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(chkAutoScroll);

        chkRepeater.addActionListener(e -> replayer.setInterceptRepeater(chkRepeater.isSelected()));
        chkAutoScroll.addActionListener(e -> autoScroll = chkAutoScroll.isSelected());

        // ── Sync button ──
        JButton btnApply = new JButton("Apply Rules");
        btnApply.addActionListener(e -> syncRules());

        JPanel applyRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        applyRow.add(btnApply);

        // ── Assemble ──
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(quickAdd,    BorderLayout.NORTH);
        south.add(optionsPanel,BorderLayout.CENTER);
        south.add(applyRow,    BorderLayout.SOUTH);

        add(rulesSection, BorderLayout.CENTER);
        add(south,        BorderLayout.SOUTH);

        rulesModel.addTableModelListener(e -> syncRules());

        rulesModel.addRow(new Object[]{ true, "Authorization", Mode.REPLACE, "" });
        syncRules();
    }

    public boolean isAutoScroll() { return autoScroll; }

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

package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.*;
import com.burpext.sessionx.engine.LoginExecutor;
import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.util.ActivityLogger;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main editor panel for a session profile.
 *
 * Tabs:
 *   1. Tokens         - token extraction + injection definitions
 *   2. Login Sequence - ordered HTTP steps to fire when session expires
 *   3. Scope          - whitelist/blacklist URL pattern rules
 *   4. Error/Refresh  - what triggers a refresh, what URL to exclude
 */
public class ProfileEditorPanel {

    private final JPanel         root;
    private final MontoyaApi     api;
    private final ProfileManager profileManager;
    private final TokenStore     tokenStore;

    private SessionProfile currentProfile;

    // Profile header fields
    private JTextField profileNameField;
    private JTextField targetHostField;
    private JToggleButton enabledToggle;

    // Tab: Tokens
    private DefaultTableModel tokenTableModel;

    // Tab: Login Sequence
    private DefaultTableModel stepTableModel;

    // Tab: Scope
    private JRadioButton whitelistRadio;
    private JRadioButton blacklistRadio;
    private DefaultTableModel scopeTableModel;

    // Tab: Error / Refresh
    private JTextField statusCodesField;
    private JTextField bodyKeywordField;
    private JTextField excludeUrlField;

    public ProfileEditorPanel(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        this.api            = api;
        this.profileManager = profileManager;
        this.tokenStore     = tokenStore;

        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_SURFACE);

        showEmptyState();
    }

    // --- Load profile into editor ---

    public void loadProfile(SessionProfile profile) {
        this.currentProfile = profile;
        root.removeAll();
        root.add(buildProfileHeader(profile), BorderLayout.NORTH);
        root.add(buildTabPane(profile), BorderLayout.CENTER);
        root.add(buildActionBar(), BorderLayout.SOUTH);
        root.revalidate();
        root.repaint();
    }

    private void showEmptyState() {
        root.removeAll();
        JLabel hint = UiTheme.mutedLabel("Select a profile from the left, or create a new one.");
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        root.add(hint, BorderLayout.CENTER);
        root.revalidate();
    }

    // --- Profile header (name, host, enable toggle) ---

    private JPanel buildProfileHeader(SessionProfile profile) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.PAD_MD, UiTheme.PAD_MD));
        header.setBackground(UiTheme.BG_SURFACE);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));

        header.add(UiTheme.label("Name:"));
        profileNameField = UiTheme.textField();
        profileNameField.setText(profile.getName());
        profileNameField.setPreferredSize(new Dimension(180, 28));
        header.add(profileNameField);

        header.add(UiTheme.label("Target Host:"));
        targetHostField = UiTheme.textField();
        targetHostField.setText(profile.getTargetHost());
        targetHostField.setToolTipText("Display label only, e.g. api.target.com");
        targetHostField.setPreferredSize(new Dimension(160, 28));
        header.add(targetHostField);

        enabledToggle = new JToggleButton(profile.isEnabled() ? "ACTIVE" : "DISABLED",
            profile.isEnabled());
        enabledToggle.setFont(UiTheme.FONT_BOLD);
        enabledToggle.setForeground(profile.isEnabled() ? UiTheme.ACCENT_GREEN : UiTheme.TEXT_MUTED);
        enabledToggle.setBackground(UiTheme.BG_ELEVATED);
        enabledToggle.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER, 1),
            new EmptyBorder(4, 12, 4, 12)));
        enabledToggle.setFocusPainted(false);
        enabledToggle.addActionListener(e -> {
            boolean on = enabledToggle.isSelected();
            enabledToggle.setText(on ? "ACTIVE" : "DISABLED");
            enabledToggle.setForeground(on ? UiTheme.ACCENT_GREEN : UiTheme.TEXT_MUTED);
        });
        header.add(enabledToggle);

        return header;
    }

    // --- Tab pane ---

    private JTabbedPane buildTabPane(SessionProfile profile) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(UiTheme.BG_SURFACE);
        tabs.setForeground(UiTheme.TEXT_PRIMARY);
        tabs.setFont(UiTheme.FONT_NORMAL);

        tabs.addTab("[1] Tokens",          buildTokensTab(profile));
        tabs.addTab("[2] Login Sequence",  buildLoginSequenceTab(profile));
        tabs.addTab("[3] Scope",           buildScopeTab(profile));
        tabs.addTab("[4] Error / Refresh", buildErrorRefreshTab(profile));

        return tabs;
    }

    // --- Tab 1: Tokens ---

    private JPanel buildTokensTab(SessionProfile profile) {
        JPanel panel = darkPanel();
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(UiTheme.PAD_MD, UiTheme.PAD_LG, UiTheme.PAD_MD, UiTheme.PAD_LG));

        JLabel hint = UiTheme.mutedLabel(
            "Each row: where to extract a token from a login response, and where to inject it into requests.");
        hint.setBorder(new EmptyBorder(0, 0, UiTheme.PAD_SM, 0));
        panel.add(hint, BorderLayout.NORTH);

        String[] cols = {"Type", "Extract From", "Regex (1 capture group)", "Step #", "Inject At", "Key / Header Name"};
        tokenTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return true; }
        };

        for (TokenDefinition td : profile.getTokens()) {
            tokenTableModel.addRow(new Object[]{
                td.getTokenType(), td.getExtractFrom(), td.getExtractRegex(),
                td.getLoginStepIndex(), td.getInjectLocation(), td.getInjectKey()
            });
        }

        JTable table = styledTable(tokenTableModel);
        setComboEditor(table, 0, TokenType.values());
        setComboEditor(table, 1, ExtractSource.values());
        setComboEditor(table, 4, TokenLocation.values());
        table.getColumnModel().getColumn(2).setCellRenderer(new MonoRenderer());

        int[] widths = {130, 160, 220, 55, 165, 140};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buildRowControls(tokenTableModel, table), BorderLayout.SOUTH);

        return panel;
    }

    // --- Tab 2: Login Sequence ---

    private JPanel buildLoginSequenceTab(SessionProfile profile) {
        JPanel panel = darkPanel();
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(UiTheme.PAD_MD, UiTheme.PAD_LG, UiTheme.PAD_MD, UiTheme.PAD_LG));

        JLabel hint = UiTheme.mutedLabel(
            "Steps execute in order. Use {{step0:CSRF}} in a body to inject a token extracted in step 0.");
        hint.setBorder(new EmptyBorder(0, 0, UiTheme.PAD_SM, 0));
        panel.add(hint, BorderLayout.NORTH);

        String[] cols = {"Label", "Method", "URL", "Body", "Content-Type"};
        stepTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return true; }
        };

        for (LoginStep step : profile.getLoginSteps()) {
            String ct = step.getHeaders().getOrDefault("Content-Type", "");
            stepTableModel.addRow(new Object[]{
                step.getLabel(), step.getMethod(), step.getUrl(), step.getBody(), ct
            });
        }

        JTable table = styledTable(stepTableModel);
        setComboEditor(table, 1, new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"});
        table.getColumnModel().getColumn(2).setCellRenderer(new MonoRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new MonoRenderer());

        int[] widths = {120, 70, 280, 220, 140};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buildRowControls(stepTableModel, table), BorderLayout.SOUTH);

        return panel;
    }

    // --- Tab 3: Scope ---

    private JPanel buildScopeTab(SessionProfile profile) {
        JPanel panel = darkPanel();
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(UiTheme.PAD_MD, UiTheme.PAD_LG, UiTheme.PAD_MD, UiTheme.PAD_LG));

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.PAD_SM, 0));
        modePanel.setBackground(UiTheme.BG_SURFACE);
        modePanel.setBorder(new EmptyBorder(0, 0, UiTheme.PAD_MD, 0));

        whitelistRadio = new JRadioButton("Whitelist - only process matching URLs");
        blacklistRadio = new JRadioButton("Blacklist - skip matching URLs");
        styleRadio(whitelistRadio);
        styleRadio(blacklistRadio);

        ButtonGroup group = new ButtonGroup();
        group.add(whitelistRadio);
        group.add(blacklistRadio);

        boolean isWhitelist = profile.getScope().getMode() == ScopeMode.WHITELIST;
        whitelistRadio.setSelected(isWhitelist);
        blacklistRadio.setSelected(!isWhitelist);

        modePanel.add(UiTheme.label("Mode:"));
        modePanel.add(whitelistRadio);
        modePanel.add(blacklistRadio);
        modePanel.add(UiTheme.mutedLabel("  Wildcard: *.example.com/api/*"));

        panel.add(modePanel, BorderLayout.NORTH);

        String[] cols = {"URL Pattern", "Enabled", "Comment"};
        scopeTableModel = new DefaultTableModel(cols, 0) {
            @Override public Class<?> getColumnClass(int col) {
                return col == 1 ? Boolean.class : String.class;
            }
            @Override public boolean isCellEditable(int row, int col) { return true; }
        };

        for (ScopeRule rule : profile.getScope().getRules()) {
            scopeTableModel.addRow(new Object[]{rule.getPattern(), rule.isEnabled(), rule.getComment()});
        }

        JTable table = styledTable(scopeTableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(0).setCellRenderer(new MonoRenderer());

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buildRowControls(scopeTableModel, table), BorderLayout.SOUTH);

        return panel;
    }

    // --- Tab 4: Error / Refresh ---

    private JPanel buildErrorRefreshTab(SessionProfile profile) {
        JPanel panel = darkPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(UiTheme.PAD_LG, UiTheme.PAD_LG * 2, UiTheme.PAD_LG, UiTheme.PAD_LG * 2));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, UiTheme.PAD_MD);

        ErrorCondition ec = profile.getErrorCondition();

        addSectionTitle(panel, gbc, 0, "TRIGGER CONDITION");
        addFormRow(panel, gbc, 1, "Trigger refresh when status code is:",
            statusCodesField = UiTheme.textField(),
            "Comma-separated codes, e.g. 401, 403");
        statusCodesField.setText(
            ec.getTriggerOnStatusCodes().stream()
              .map(String::valueOf)
              .collect(Collectors.joining(", ")));

        addFormRow(panel, gbc, 2, "Also require body contains (optional):",
            bodyKeywordField = UiTheme.textField(),
            "Only trigger if response body contains this text");
        bodyKeywordField.setText(ec.getTriggerOnBodyKeyword());

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weighty = 0.05;
        panel.add(new JLabel(), gbc);
        gbc.weighty = 0; gbc.gridwidth = 1;

        addSectionTitle(panel, gbc, 4, "REFRESH EXCLUSION");
        addFormRow(panel, gbc, 5, "Skip token injection for requests to URL:",
            excludeUrlField = UiTheme.monoField(),
            "Prevents infinite loop - enter your login/token endpoint here");
        excludeUrlField.setText(ec.getRefreshExcludeUrl());

        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);

        return panel;
    }

    // --- Action bar ---

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.PAD_SM, UiTheme.PAD_SM));
        bar.setBackground(UiTheme.BG_SURFACE);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER));

        JButton saveBtn = UiTheme.primaryButton("Save Profile");
        saveBtn.addActionListener(e -> saveCurrentProfile());
        bar.add(saveBtn);

        JButton runBtn = UiTheme.button("Run Login Now");
        runBtn.setToolTipText("Manually trigger the login sequence to populate tokens");
        runBtn.addActionListener(e -> runLoginNow());
        bar.add(runBtn);

        JButton exportBtn = UiTheme.button("Export JSON");
        exportBtn.addActionListener(e -> exportProfile());
        bar.add(exportBtn);

        JButton importBtn = UiTheme.button("Import JSON");
        importBtn.addActionListener(e -> importProfile());
        bar.add(importBtn);

        bar.add(Box.createHorizontalGlue());

        JButton deleteBtn = UiTheme.dangerButton("Delete Profile");
        deleteBtn.addActionListener(e -> deleteCurrentProfile());
        bar.add(deleteBtn);

        return bar;
    }

    // --- Save logic ---

    private void saveCurrentProfile() {
        if (currentProfile == null) return;

        currentProfile.setName(profileNameField.getText().trim());
        currentProfile.setTargetHost(targetHostField.getText().trim());
        currentProfile.setEnabled(enabledToggle.isSelected());

        List<TokenDefinition> tokens = new ArrayList<>();
        for (int i = 0; i < tokenTableModel.getRowCount(); i++) {
            TokenDefinition td = new TokenDefinition();
            td.setTokenType((TokenType) tokenTableModel.getValueAt(i, 0));
            td.setExtractFrom((ExtractSource) tokenTableModel.getValueAt(i, 1));
            td.setExtractRegex(str(tokenTableModel.getValueAt(i, 2)));
            try {
                td.setLoginStepIndex(Integer.parseInt(str(tokenTableModel.getValueAt(i, 3))));
            } catch (NumberFormatException ignored) {}
            td.setInjectLocation((TokenLocation) tokenTableModel.getValueAt(i, 4));
            td.setInjectKey(str(tokenTableModel.getValueAt(i, 5)));
            tokens.add(td);
        }
        currentProfile.setTokens(tokens);

        List<LoginStep> steps = new ArrayList<>();
        for (int i = 0; i < stepTableModel.getRowCount(); i++) {
            LoginStep step = new LoginStep();
            step.setLabel(str(stepTableModel.getValueAt(i, 0)));
            step.setMethod(str(stepTableModel.getValueAt(i, 1)));
            step.setUrl(str(stepTableModel.getValueAt(i, 2)));
            step.setBody(str(stepTableModel.getValueAt(i, 3)));
            String ct = str(stepTableModel.getValueAt(i, 4));
            if (!ct.isBlank()) step.getHeaders().put("Content-Type", ct);
            steps.add(step);
        }
        currentProfile.setLoginSteps(steps);

        ScopeList scope = new ScopeList();
        scope.setMode(whitelistRadio.isSelected() ? ScopeMode.WHITELIST : ScopeMode.BLACKLIST);
        List<ScopeRule> rules = new ArrayList<>();
        for (int i = 0; i < scopeTableModel.getRowCount(); i++) {
            ScopeRule rule = new ScopeRule();
            rule.setPattern(str(scopeTableModel.getValueAt(i, 0)));
            rule.setEnabled((Boolean) scopeTableModel.getValueAt(i, 1));
            rule.setComment(str(scopeTableModel.getValueAt(i, 2)));
            rules.add(rule);
        }
        scope.setRules(rules);
        currentProfile.setScope(scope);

        ErrorCondition ec = new ErrorCondition();
        String codes = statusCodesField.getText().trim();
        if (!codes.isBlank()) {
            List<Integer> codeList = Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
            ec.setTriggerOnStatusCodes(codeList);
        }
        ec.setTriggerOnBodyKeyword(bodyKeywordField.getText().trim());
        ec.setRefreshExcludeUrl(excludeUrlField.getText().trim());
        currentProfile.setErrorCondition(ec);

        profileManager.updateProfile(currentProfile);
        JOptionPane.showMessageDialog(root, "Profile \"" + currentProfile.getName() + "\" saved.",
            "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void runLoginNow() {
        if (currentProfile == null) return;
        saveCurrentProfile();
        new Thread(() -> {
            LoginExecutor exec = new LoginExecutor(api, tokenStore, ActivityLogger.getInstance());
            exec.execute(currentProfile);
        }).start();
    }

    private void deleteCurrentProfile() {
        if (currentProfile == null) return;
        int confirm = JOptionPane.showConfirmDialog(root,
            "Delete profile \"" + currentProfile.getName() + "\"?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            profileManager.deleteProfile(currentProfile.getId());
            currentProfile = null;
            showEmptyState();
        }
    }

    private void exportProfile() {
        if (currentProfile == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(currentProfile.getName().replaceAll("\\s+", "_") + ".json"));
        fc.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (fc.showSaveDialog(root) == JFileChooser.APPROVE_OPTION) {
            try {
                profileManager.exportToFile(currentProfile, fc.getSelectedFile());
                JOptionPane.showMessageDialog(root, "Profile exported successfully.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(root, "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importProfile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (fc.showOpenDialog(root) == JFileChooser.APPROVE_OPTION) {
            try {
                SessionProfile imported = profileManager.importFromFile(fc.getSelectedFile());
                loadProfile(imported);
                JOptionPane.showMessageDialog(root, "Profile imported: \"" + imported.getName() + "\"",
                    "Import", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(root, "Import failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- UI Helpers ---

    private JPanel darkPanel() {
        JPanel p = new JPanel();
        p.setBackground(UiTheme.BG_SURFACE);
        return p;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(UiTheme.BG_ELEVATED);
        table.setForeground(UiTheme.TEXT_PRIMARY);
        table.setFont(UiTheme.FONT_NORMAL);
        table.setGridColor(UiTheme.BORDER);
        table.setRowHeight(26);
        table.setSelectionBackground(new Color(0x1F, 0x6F, 0xEB, 80));
        table.setSelectionForeground(UiTheme.TEXT_PRIMARY);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.getTableHeader().setBackground(UiTheme.BG_SURFACE);
        table.getTableHeader().setForeground(UiTheme.TEXT_MUTED);
        table.getTableHeader().setFont(UiTheme.FONT_SMALL);
        table.getTableHeader().setBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));
        return table;
    }

    private JPanel buildRowControls(DefaultTableModel model, JTable table) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.PAD_SM, UiTheme.PAD_SM));
        row.setBackground(UiTheme.BG_SURFACE);

        JButton addBtn = UiTheme.button("+ Add Row");
        addBtn.addActionListener(e -> model.addRow(new Object[model.getColumnCount()]));
        row.add(addBtn);

        JButton removeBtn = UiTheme.dangerButton("Remove Selected");
        removeBtn.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected >= 0) model.removeRow(selected);
        });
        row.add(removeBtn);

        return row;
    }

    private <T> void setComboEditor(JTable table, int col, T[] values) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setBackground(UiTheme.BG_ELEVATED);
        combo.setForeground(UiTheme.TEXT_PRIMARY);
        combo.setFont(UiTheme.FONT_NORMAL);
        table.getColumnModel().getColumn(col).setCellEditor(new DefaultCellEditor(combo));
    }

    private void addSectionTitle(JPanel panel, GridBagConstraints gbc, int row, String title) {
        gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(UiTheme.TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(0, 0, 2, 0));
        panel.add(lbl, gbc);
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2;
        panel.add(UiTheme.separator(), gbc);
        gbc.gridwidth = 1;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc,
                             int row, String labelText,
                             JTextField field, String hint) {
        gbc.gridy = row; gbc.gridx = 0; gbc.weightx = 0;
        JLabel lbl = UiTheme.label(labelText);
        lbl.setPreferredSize(new Dimension(260, 28));
        panel.add(lbl, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        field.setToolTipText(hint);
        panel.add(field, gbc);
    }

    private void styleRadio(JRadioButton radio) {
        radio.setFont(UiTheme.FONT_NORMAL);
        radio.setForeground(UiTheme.TEXT_PRIMARY);
        radio.setBackground(UiTheme.BG_SURFACE);
    }

    private String str(Object val) {
        return val == null ? "" : val.toString();
    }

    // --- Inner: Monospace cell renderer ---

    private static class MonoRenderer extends DefaultTableCellRenderer {
        MonoRenderer() {
            setFont(UiTheme.FONT_MONO);
            setBackground(UiTheme.BG_ELEVATED);
            setForeground(UiTheme.TEXT_PRIMARY);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(isSelected ? new Color(0x1F, 0x6F, 0xEB, 80) : UiTheme.BG_ELEVATED);
            setForeground(UiTheme.TEXT_PRIMARY);
            setFont(UiTheme.FONT_MONO);
            return this;
        }
    }

    public JPanel getPanel() { return root; }
}

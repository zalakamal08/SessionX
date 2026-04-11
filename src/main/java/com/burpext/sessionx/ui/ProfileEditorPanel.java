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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Profile editor panel -- right-hand content area.
 *
 * Follows Postman2Burp RequestInspectorPanel style:
 *   - System default backgrounds throughout
 *   - SansSerif fonts, size 12 body / 11 small
 *   - Standard JTabbedPane, JTable, JTextField -- no paint overrides
 *   - Toolbar buttons via stock Swing (no custom painting)
 *   - Thin Color(210,210,210) borders only where needed for separation
 *
 * Tabs:
 *   1. Tokens         - per-token extraction + injection config
 *   2. Login Sequence - multi-step HTTP login flow
 *   3. Scope          - URL whitelist / blacklist
 *   4. Error / Refresh - trigger conditions and loop prevention
 */
public class ProfileEditorPanel {

    private final JPanel         root;
    private final MontoyaApi     api;
    private final ProfileManager profileManager;
    private final TokenStore     tokenStore;

    private SessionProfile currentProfile;

    // Header controls
    private JTextField    profileNameField;
    private JTextField    targetHostField;
    private JToggleButton enabledToggle;

    // Tab 1: Tokens
    private DefaultTableModel tokenTableModel;

    // Tab 2: Login Sequence
    private DefaultTableModel stepTableModel;

    // Tab 3: Scope
    private JRadioButton whitelistRadio;
    private JRadioButton blacklistRadio;
    private DefaultTableModel scopeTableModel;

    // Tab 4: Error / Refresh
    private JTextField statusCodesField;
    private JTextField bodyKeywordField;
    private JTextField excludeUrlField;

    // Keep a reference to the step table so interactive buttons can index into it
    private JTable stepTable;

    public ProfileEditorPanel(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        this.api            = api;
        this.profileManager = profileManager;
        this.tokenStore     = tokenStore;

        root = new JPanel(new BorderLayout(0, 0));
        showEmptyState();
    }

    public void loadProfile(SessionProfile profile) {
        this.currentProfile = profile;
        root.removeAll();
        root.add(buildProfileHeader(profile), BorderLayout.NORTH);
        root.add(buildTabs(profile),          BorderLayout.CENTER);
        root.add(buildActionBar(),            BorderLayout.SOUTH);
        root.revalidate();
        root.repaint();
    }

    private void showEmptyState() {
        root.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        JLabel hint = UiTheme.mutedLabel("Select a profile or create a new one.");
        center.add(hint);
        root.add(center, BorderLayout.CENTER);
        root.revalidate();
    }

    // =========================================================================
    // PROFILE HEADER
    // =========================================================================

    private JPanel buildProfileHeader(SessionProfile profile) {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.getBorderColor()),
            new EmptyBorder(UiTheme.SP_SM, UiTheme.SP_LG, UiTheme.SP_SM, UiTheme.SP_LG)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor  = GridBagConstraints.CENTER;
        gbc.fill    = GridBagConstraints.NONE;
        gbc.gridy   = 0;
        gbc.weighty = 1.0;

        gbc.gridx = 0; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_XS);
        bar.add(headerLabel("Name:"), gbc);

        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_LG);
        profileNameField = compactField(profile.getName(), 190);
        bar.add(profileNameField, gbc);

        gbc.gridx = 2; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_XS);
        bar.add(headerLabel("Host:"), gbc);

        gbc.gridx = 3; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_LG);
        targetHostField = compactField(profile.getTargetHost(), 160);
        targetHostField.setToolTipText("e.g. api.example.com");
        bar.add(targetHostField, gbc);

        gbc.gridx = 4; gbc.insets = new Insets(0, 0, 0, 0);
        enabledToggle = new JToggleButton(
            profile.isEnabled() ? "Active" : "Inactive", profile.isEnabled());
        enabledToggle.setFont(UiTheme.FONT_UI_SM);
        enabledToggle.setFocusPainted(false);
        enabledToggle.setForeground(profile.isEnabled() ? UiTheme.STATUS_OK : UiTheme.getMutedText());
        enabledToggle.addActionListener(e -> {
            enabledToggle.setText(enabledToggle.isSelected() ? "Active" : "Inactive");
            enabledToggle.setForeground(
                enabledToggle.isSelected() ? UiTheme.STATUS_OK : UiTheme.getMutedText());
        });
        bar.add(enabledToggle, gbc);

        // Filler
        gbc.gridx = 5; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(0,0,0,0);
        bar.add(new JLabel(), gbc);

        return bar;
    }

    // =========================================================================
    // TABS  (stock JTabbedPane -- no paint overrides)
    // =========================================================================

    private JTabbedPane buildTabs(SessionProfile profile) {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(UiTheme.FONT_UI);
        tabs.addTab("Tokens",          buildTokensTab(profile));
        tabs.addTab("Login Sequence",  buildLoginSequenceTab(profile));
        tabs.addTab("Scope",           buildScopeTab(profile));
        tabs.addTab("Error / Refresh", buildErrorRefreshTab(profile));
        return tabs;
    }

    // =========================================================================
    // TAB 1: TOKENS
    // =========================================================================

    private JPanel buildTokensTab(SessionProfile profile) {
        JPanel panel = tabPanel();

        JLabel hint = UiTheme.mutedLabel(
            "Token extraction + injection config. Variable Name is the primary key (e.g. access_token). JSONPath takes priority over Regex for JSON bodies.");
        hint.setBorder(new EmptyBorder(0, 0, UiTheme.SP_SM, 0));
        panel.add(hint, BorderLayout.NORTH);

        String[] cols = {"Variable Name", "Type", "Extract From", "JSONPath", "Regex (fallback)", "Step #", "Inject At", "Key / Header"};
        tokenTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };

        for (TokenDefinition td : profile.getTokens()) {
            tokenTableModel.addRow(new Object[]{
                td.getVariableName(),
                td.getTokenType(), td.getExtractFrom(),
                td.getExtractJsonPath(), td.getExtractRegex(),
                td.getLoginStepIndex(), td.getInjectLocation(), td.getInjectKey()
            });
        }

        JTable table = buildTable(tokenTableModel);
        setComboEditor(table, 1, TokenType.values());
        setComboEditor(table, 2, ExtractSource.values());
        setComboEditor(table, 6, TokenLocation.values());
        setMonoColumns(table, new int[]{3, 4, 7});
        setColumnWidths(table, new int[]{130, 100, 140, 175, 175, 50, 155, 120});

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buildTableFooter(tokenTableModel, table), BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================================
    // TAB 2: LOGIN SEQUENCE
    // =========================================================================

    private JPanel buildLoginSequenceTab(SessionProfile profile) {
        JPanel panel = tabPanel();

        JLabel hint = UiTheme.mutedLabel(
            "Steps execute top-to-bottom. Use {{step0:varName}} to reference variables from prior steps.  " +
            "Use  ⚡ Add Step  to build steps interactively.");
        hint.setBorder(new EmptyBorder(0, 0, UiTheme.SP_SM, 0));
        panel.add(hint, BorderLayout.NORTH);

        String[] cols = {"Label", "Method", "URL", "Body", "Content-Type"};
        stepTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };

        for (LoginStep step : profile.getLoginSteps()) {
            String ct = step.getHeaders().getOrDefault("Content-Type", "");
            stepTableModel.addRow(new Object[]{
                step.getLabel(), step.getMethod(), step.getUrl(), step.getBody(), ct
            });
        }

        stepTable = buildTable(stepTableModel);
        setComboEditor(stepTable, 1, new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"});
        setMonoColumns(stepTable, new int[]{2, 3});
        setColumnWidths(stepTable, new int[]{120, 68, 250, 210, 140});

        panel.add(new JScrollPane(stepTable), BorderLayout.CENTER);
        panel.add(buildStepFooter(profile), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStepFooter(SessionProfile profile) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SP_SM, UiTheme.SP_SM));
        row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        // Interactive step builder
        JButton addInteractive = UiTheme.button("⚡ Add Step (Interactive)");
        addInteractive.setFont(UiTheme.FONT_BOLD);
        addInteractive.setToolTipText("Open the step builder — send a request and click values to extract them");
        addInteractive.addActionListener(e -> openStepBuilder(profile, -1));
        row.add(addInteractive);

        JButton editStep = UiTheme.smallButton("🔧 Edit Step", "Edit the selected step interactively");
        editStep.addActionListener(e -> {
            int sel = stepTable == null ? -1 : stepTable.getSelectedRow();
            if (sel < 0) { showInfo("Select a step row to edit."); return; }
            openStepBuilder(profile, sel);
        });
        row.add(editStep);

        // Plain add/remove
        JButton addBtn = UiTheme.smallButton("➕ Add Row", "Add a blank step row manually");
        addBtn.addActionListener(e -> stepTableModel.addRow(new Object[stepTableModel.getColumnCount()]));
        row.add(addBtn);

        JButton removeBtn = UiTheme.smallButton("➖ Remove Row", "Remove selected step row");
        removeBtn.addActionListener(e -> {
            if (stepTable == null) return;
            int sel = stepTable.getSelectedRow();
            if (sel >= 0) stepTableModel.removeRow(sel);
        });
        row.add(removeBtn);

        return row;
    }

    /**
     * Opens the interactive LoginStepBuilderDialog for the given step index.
     * @param profile  current profile
     * @param stepIdx  -1 to add a new step, >=0 to edit an existing step
     */
    private void openStepBuilder(SessionProfile profile, int stepIdx) {
        // Save current UI state so the dialog sees up-to-date token definitions
        saveCurrentProfile();

        boolean isNew = (stepIdx < 0);
        int targetIdx = isNew ? profile.getLoginSteps().size() : stepIdx;

        LoginStep preExisting = isNew ? null
            : (stepIdx < profile.getLoginSteps().size() ? profile.getLoginSteps().get(stepIdx) : null);

        // Collect token definitions already assigned to this step
        List<TokenDefinition> stepTDs = new ArrayList<>();
        for (TokenDefinition td : profile.getTokens()) {
            if (td.getLoginStepIndex() == targetIdx) stepTDs.add(td);
        }

        // Collect variable names from prior steps for autocomplete
        List<String> priorVars = new ArrayList<>();
        for (TokenDefinition td : profile.getTokens()) {
            if (td.getLoginStepIndex() < targetIdx) {
                String key = td.effectiveKey();
                if (!key.isBlank() && !priorVars.contains(key)) priorVars.add(key);
            }
        }

        java.awt.Frame owner = javax.swing.SwingUtilities.getWindowAncestor(root) instanceof java.awt.Frame
            ? (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(root) : null;

        LoginStepBuilderDialog dialog = new LoginStepBuilderDialog(
            owner, api, targetIdx, preExisting, stepTDs, priorVars);
        dialog.setVisible(true);

        if (!dialog.wasCommitted()) return;

        LoginStep newStep = dialog.getResultStep();

        if (isNew) {
            // Append new step to model
            String ct = newStep.getHeaders().getOrDefault("Content-Type", "");
            stepTableModel.addRow(new Object[]{
                newStep.getLabel(), newStep.getMethod(), newStep.getUrl(), newStep.getBody(), ct
            });
        } else {
            // Update the existing row
            String ct = newStep.getHeaders().getOrDefault("Content-Type", "");
            stepTableModel.setValueAt(newStep.getLabel(),  stepIdx, 0);
            stepTableModel.setValueAt(newStep.getMethod(), stepIdx, 1);
            stepTableModel.setValueAt(newStep.getUrl(),    stepIdx, 2);
            stepTableModel.setValueAt(newStep.getBody(),   stepIdx, 3);
            stepTableModel.setValueAt(ct,                  stepIdx, 4);

            // Remove old token definitions for this step, they'll be replaced
            profile.getTokens().removeIf(td -> td.getLoginStepIndex() == targetIdx);
        }

        // Add returned token definitions to the profile and refresh the token table
        for (TokenDefinition td : dialog.getResultTokens()) {
            td.setLoginStepIndex(targetIdx);
            profile.getTokens().add(td);
        }

        // Reload visible token table rows
        refreshTokenTable(profile);

        // Persist
        profileManager.updateProfile(profile);
        ActivityLogger.getInstance().info("Step " + (targetIdx + 1) + " configured via Step Builder.");
    }

    private void refreshTokenTable(SessionProfile profile) {
        if (tokenTableModel == null) return;
        tokenTableModel.setRowCount(0);
        for (TokenDefinition td : profile.getTokens()) {
            tokenTableModel.addRow(new Object[]{
                td.getVariableName(),
                td.getTokenType(), td.getExtractFrom(),
                td.getExtractJsonPath(), td.getExtractRegex(),
                td.getLoginStepIndex(), td.getInjectLocation(), td.getInjectKey()
            });
        }
    }

    // =========================================================================
    // TAB 3: SCOPE
    // =========================================================================

    private JPanel buildScopeTab(SessionProfile profile) {
        JPanel panel = tabPanel();

        JLabel hint = UiTheme.mutedLabel("URL pattern rules — supports * wildcard (e.g. *.example.com/api/*)");
        hint.setBorder(new EmptyBorder(0, 0, UiTheme.SP_XS, 0));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.SP_MD, 0));
        modeRow.setOpaque(false);
        modeRow.setBorder(new EmptyBorder(0, 0, UiTheme.SP_SM, 0));

        whitelistRadio = new JRadioButton("Whitelist (process only matching URLs)");
        blacklistRadio = new JRadioButton("Blacklist (skip matching URLs)");
        whitelistRadio.setFont(UiTheme.FONT_UI_SM);
        blacklistRadio.setFont(UiTheme.FONT_UI_SM);

        ButtonGroup group = new ButtonGroup();
        group.add(whitelistRadio);
        group.add(blacklistRadio);

        boolean isWhitelist = profile.getScope().getMode() == ScopeMode.WHITELIST;
        whitelistRadio.setSelected(isWhitelist);
        blacklistRadio.setSelected(!isWhitelist);
        modeRow.add(whitelistRadio);
        modeRow.add(blacklistRadio);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(hint, BorderLayout.NORTH);
        north.add(modeRow, BorderLayout.CENTER);
        panel.add(north, BorderLayout.NORTH);

        String[] cols = {"URL Pattern", "Enabled", "Note"};
        scopeTableModel = new DefaultTableModel(cols, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 1 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };

        for (ScopeRule rule : profile.getScope().getRules()) {
            scopeTableModel.addRow(new Object[]{rule.getPattern(), rule.isEnabled(), rule.getComment()});
        }

        JTable table = buildTable(scopeTableModel);
        setMonoColumns(table, new int[]{0});
        setColumnWidths(table, new int[]{300, 60, 230});

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buildTableFooter(scopeTableModel, table), BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================================
    // TAB 4: ERROR / REFRESH
    // =========================================================================

    private JPanel buildErrorRefreshTab(SessionProfile profile) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(UiTheme.SP_LG, UiTheme.SP_LG, UiTheme.SP_LG, UiTheme.SP_LG));

        ErrorCondition ec = profile.getErrorCondition();

        panel.add(sectionRow("Trigger Condition"));
        panel.add(Box.createVerticalStrut(UiTheme.SP_MD));

        panel.add(formRow(
            "Trigger refresh on HTTP status code(s):",
            statusCodesField = UiTheme.textField(),
            "Comma-separated, e.g.  401, 403"));
        statusCodesField.setText(
            ec.getTriggerOnStatusCodes().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        panel.add(Box.createVerticalStrut(UiTheme.SP_SM));

        panel.add(formRow(
            "Also require response body contains (optional):",
            bodyKeywordField = UiTheme.textField(),
            "Only trigger refresh if the response body includes this text"));
        bodyKeywordField.setText(ec.getTriggerOnBodyKeyword());

        panel.add(Box.createVerticalStrut(UiTheme.SP_LG));
        panel.add(sectionRow("Refresh Exclusion"));
        panel.add(Box.createVerticalStrut(UiTheme.SP_MD));

        panel.add(formRow(
            "Do not inject tokens into requests matching:",
            excludeUrlField = UiTheme.monoField(),
            "Prevents infinite refresh loop -- enter your login endpoint path"));
        excludeUrlField.setText(ec.getRefreshExcludeUrl());

        panel.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    // =========================================================================
    // ACTION BAR
    // =========================================================================

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        JButton saveBtn = UiTheme.button("💾 Save");
        saveBtn.setFont(UiTheme.FONT_BOLD);
        saveBtn.addActionListener(e -> saveCurrentProfile());
        bar.add(saveBtn);

        JButton runBtn = UiTheme.button("▶ Run Login");
        runBtn.setToolTipText("Manually trigger the login sequence to populate tokens");
        runBtn.addActionListener(e -> runLoginNow());
        bar.add(runBtn);

        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 16));
        sep.setForeground(UiTheme.getBorderColor());
        bar.add(sep);

        JButton exportBtn = UiTheme.button("📤 Export");
        exportBtn.addActionListener(e -> exportProfile());
        bar.add(exportBtn);

        JButton importBtn = UiTheme.button("📥 Import");
        importBtn.addActionListener(e -> importProfile());
        bar.add(importBtn);

        JSeparator sep2 = new JSeparator(JSeparator.VERTICAL);
        sep2.setPreferredSize(new Dimension(1, 16));
        sep2.setForeground(UiTheme.getBorderColor());
        bar.add(sep2);

        JButton deleteBtn = UiTheme.button("🗑 Delete");
        deleteBtn.setForeground(UiTheme.STATUS_ERR);
        deleteBtn.addActionListener(e -> deleteCurrentProfile());
        bar.add(deleteBtn);

        return bar;
    }

    // =========================================================================
    // TABLE HELPERS
    // =========================================================================

    private JPanel buildTableFooter(DefaultTableModel model, JTable table) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SP_SM, UiTheme.SP_SM));
        row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        JButton addBtn = UiTheme.smallButton("➕ Add Row", "Add a new row");
        addBtn.addActionListener(e -> model.addRow(new Object[model.getColumnCount()]));
        row.add(addBtn);

        JButton removeBtn = UiTheme.smallButton("➖ Remove Row", "Remove selected row");
        removeBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) model.removeRow(sel);
        });
        row.add(removeBtn);

        return row;
    }

    private JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    Color bg = getBackground();
                    if (row % 2 != 0) {
                        // Alternate row styling dynamically derived from current theme background
                        c.setBackground(UiTheme.isDarkTheme() ? bg.brighter() : new Color(
                            Math.max(0, bg.getRed() - 10),
                            Math.max(0, bg.getGreen() - 10),
                            Math.max(0, bg.getBlue() - 10)
                        ));
                    } else {
                        c.setBackground(bg);
                    }
                }
                return c;
            }
        };
        table.setFont(UiTheme.FONT_UI);
        table.setGridColor(UiTheme.getBorderColor());
        table.setRowHeight(26);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);

        JTableHeader header = table.getTableHeader();
        header.setFont(UiTheme.FONT_BOLD);
        header.setReorderingAllowed(false);

        return table;
    }

    private void setMonoColumns(JTable table, int[] cols) {
        DefaultTableCellRenderer monoR = new DefaultTableCellRenderer();
        monoR.setFont(UiTheme.FONT_MONO_SM);
        monoR.setBorder(new EmptyBorder(0, 5, 0, 5));
        for (int c : cols) {
            table.getColumnModel().getColumn(c).setCellRenderer(monoR);
        }
    }

    private <T> void setComboEditor(JTable table, int col, T[] values) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setFont(UiTheme.FONT_UI);
        table.getColumnModel().getColumn(col).setCellEditor(new DefaultCellEditor(combo));
    }

    private void setColumnWidths(JTable table, int[] widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // =========================================================================
    // FORM HELPERS
    // =========================================================================

    private JPanel formRow(String labelText, JTextField field, String hint) {
        JPanel row = new JPanel(new BorderLayout(0, UiTheme.SP_XS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setPreferredSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UiTheme.FONT_UI_SM);
        lbl.setForeground(UiTheme.getSecondaryText());
        row.add(lbl, BorderLayout.NORTH);

        field.setToolTipText(hint);
        row.add(field, BorderLayout.CENTER);

        return row;
    }

    /** Labeled horizontal rule that acts as a visual section break */
    private JPanel sectionRow(String title) {
        JPanel row = new JPanel(new BorderLayout(UiTheme.SP_SM, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel lbl = UiTheme.sectionLabel(title);
        row.add(lbl, BorderLayout.WEST);

        JSeparator line = new JSeparator();
        line.setForeground(UiTheme.getBorderColor());
        row.add(line, BorderLayout.CENTER);

        return row;
    }

    private JLabel headerLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UiTheme.FONT_UI_SM);
        l.setForeground(UiTheme.getSecondaryText());
        return l;
    }

    private JTextField compactField(String text, int width) {
        JTextField f = UiTheme.textField();
        f.setText(text);
        f.setPreferredSize(new Dimension(width, 26));
        return f;
    }

    private JPanel tabPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBorder(new EmptyBorder(UiTheme.SP_MD, UiTheme.SP_LG, 0, UiTheme.SP_LG));
        return p;
    }

    // =========================================================================
    // SAVE / ACTIONS
    // =========================================================================

    private void saveCurrentProfile() {
        if (currentProfile == null) return;

        currentProfile.setName(profileNameField.getText().trim());
        currentProfile.setTargetHost(targetHostField.getText().trim());
        currentProfile.setEnabled(enabledToggle.isSelected());

        // Tokens  (columns: varName | type | extractFrom | jsonPath | regex | stepIdx | injectLoc | injectKey)
        List<TokenDefinition> tokens = new ArrayList<>();
        for (int i = 0; i < tokenTableModel.getRowCount(); i++) {
            TokenDefinition td = new TokenDefinition();
            td.setVariableName(str(tokenTableModel.getValueAt(i, 0)));
            td.setTokenType(castEnum(tokenTableModel, i, 1, TokenType.class));
            td.setExtractFrom(castEnum(tokenTableModel, i, 2, ExtractSource.class));
            td.setExtractJsonPath(str(tokenTableModel.getValueAt(i, 3)));
            td.setExtractRegex(str(tokenTableModel.getValueAt(i, 4)));
            try { td.setLoginStepIndex(Integer.parseInt(str(tokenTableModel.getValueAt(i, 5)))); }
            catch (NumberFormatException ignored) {}
            td.setInjectLocation(castEnum(tokenTableModel, i, 6, TokenLocation.class));
            td.setInjectKey(str(tokenTableModel.getValueAt(i, 7)));
            tokens.add(td);
        }
        currentProfile.setTokens(tokens);

        // Login steps
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

        // Scope
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

        // Error condition
        ErrorCondition ec = new ErrorCondition();
        String codes = statusCodesField.getText().trim();
        if (!codes.isBlank()) {
            List<Integer> codeList = Arrays.stream(codes.split(","))
                .map(String::trim).filter(s -> s.matches("\\d+"))
                .map(Integer::parseInt).collect(Collectors.toList());
            ec.setTriggerOnStatusCodes(codeList);
        }
        ec.setTriggerOnBodyKeyword(bodyKeywordField.getText().trim());
        ec.setRefreshExcludeUrl(excludeUrlField.getText().trim());
        currentProfile.setErrorCondition(ec);

        profileManager.updateProfile(currentProfile);
        ActivityLogger.getInstance().info("Profile saved: \"" + currentProfile.getName() + "\"");
    }

    private void runLoginNow() {
        if (currentProfile == null) return;
        saveCurrentProfile();
        new Thread(() -> {
            LoginExecutor exec = new LoginExecutor(api, tokenStore, ActivityLogger.getInstance());
            exec.execute(currentProfile);
        }, "sessionx-login").start();
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(root, msg, "SessionX", JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteCurrentProfile() {
        if (currentProfile == null) return;
        int confirm = JOptionPane.showConfirmDialog(root,
            "Delete \"" + currentProfile.getName() + "\"?",
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
                JOptionPane.showMessageDialog(root,
                    "Profile exported successfully.", "Export", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(root,
                    "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(root,
                    "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private String str(Object val) { return val == null ? "" : val.toString(); }

    @SuppressWarnings("unchecked")
    private <T> T castEnum(DefaultTableModel m, int row, int col, Class<T> type) {
        Object v = m.getValueAt(row, col);
        return type.isInstance(v) ? (T) v : null;
    }

    public JPanel getPanel() { return root; }
}

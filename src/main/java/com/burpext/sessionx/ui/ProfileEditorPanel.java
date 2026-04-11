package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.*;
import com.burpext.sessionx.engine.LoginExecutor;
import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.util.ActivityLogger;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Profile editor panel — shown on the right side when a profile is selected.
 *
 * Design intent:
 *   - Tab strip at the top, minimal tab styling
 *   - Each tab uses consistent form layout: left labels (fixed width) + right fields
 *   - Tables are borderless with zebra rows
 *   - Action bar pinned to bottom, primary action leftmost
 *   - Empty state: plain centered message, no graphics
 *
 * Tabs:
 *   1. Tokens         - extraction regex + injection location per token type
 *   2. Login Sequence - ordered HTTP steps
 *   3. Scope          - URL whitelist/blacklist patterns
 *   4. Error / Refresh - trigger conditions
 */
public class ProfileEditorPanel {

    private final JPanel         root;
    private final MontoyaApi     api;
    private final ProfileManager profileManager;
    private final TokenStore     tokenStore;

    private SessionProfile currentProfile;

    // Header controls
    private JTextField   profileNameField;
    private JTextField   targetHostField;
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

    public ProfileEditorPanel(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        this.api            = api;
        this.profileManager = profileManager;
        this.tokenStore     = tokenStore;

        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_PANEL);
        showEmptyState();
    }

    // --- State ---

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
        center.setBackground(UiTheme.BG_PANEL);
        JLabel hint = UiTheme.mutedLabel("Select a profile or create a new one");
        center.add(hint);
        root.add(center, BorderLayout.CENTER);
        root.revalidate();
    }

    // =========================================================================
    // PROFILE HEADER
    // =========================================================================

    private JPanel buildProfileHeader(SessionProfile profile) {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBackground(UiTheme.BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, UiTheme.BORDER_SUBTLE),
            new EmptyBorder(UiTheme.SP_SM, UiTheme.SP_LG, UiTheme.SP_SM, UiTheme.SP_LG)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor  = GridBagConstraints.CENTER;
        gbc.fill    = GridBagConstraints.NONE;
        gbc.gridy   = 0;
        gbc.weighty = 1.0;

        gbc.gridx = 0; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_XS);
        bar.add(smallLabel("Name"), gbc);

        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_LG);
        profileNameField = compactField(profile.getName(), 190);
        bar.add(profileNameField, gbc);

        gbc.gridx = 2; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_XS);
        bar.add(smallLabel("Host"), gbc);

        gbc.gridx = 3; gbc.insets = new Insets(0, 0, 0, UiTheme.SP_LG);
        targetHostField = compactField(profile.getTargetHost(), 160);
        targetHostField.setToolTipText("Display label e.g. api.target.com");
        bar.add(targetHostField, gbc);

        gbc.gridx = 4; gbc.insets = new Insets(0, 0, 0, 0);
        enabledToggle = buildToggle(profile.isEnabled());
        bar.add(enabledToggle, gbc);

        // Filler pushes everything to the left
        gbc.gridx   = 5;
        gbc.weightx = 1.0;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.insets  = new Insets(0, 0, 0, 0);
        bar.add(new JLabel(), gbc);

        return bar;
    }


    private JToggleButton buildToggle(boolean on) {
        JToggleButton t = new JToggleButton(on ? "Active" : "Inactive", on) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isSelected() ? new Color(0x1A, 0x40, 0x1A) : UiTheme.BG_INPUT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        t.setFont(UiTheme.FONT_UI_SM);
        t.setForeground(on ? UiTheme.STATUS_OK : UiTheme.TEXT_MUTED);
        t.setOpaque(false);
        t.setContentAreaFilled(false);
        t.setBorderPainted(true);
        t.setFocusPainted(false);
        t.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER_NORMAL, 1),
            new EmptyBorder(4, 10, 4, 10)));
        t.addActionListener(e -> {
            t.setText(t.isSelected() ? "Active" : "Inactive");
            t.setForeground(t.isSelected() ? UiTheme.STATUS_OK : UiTheme.TEXT_MUTED);
            t.repaint();
        });
        return t;
    }

    // =========================================================================
    // TABS
    // =========================================================================

    private JTabbedPane buildTabs(SessionProfile profile) {
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(UiTheme.BG_PANEL);
        tabs.setForeground(UiTheme.TEXT_SECONDARY);
        tabs.setFont(UiTheme.FONT_UI_SM);

        // Use HTML to get consistent tab rendering
        tabs.addTab("Tokens",         buildTokensTab(profile));
        tabs.addTab("Login Sequence", buildLoginSequenceTab(profile));
        tabs.addTab("Scope",          buildScopeTab(profile));
        tabs.addTab("Error / Refresh",buildErrorRefreshTab(profile));

        return tabs;
    }

    // =========================================================================
    // TAB 1: TOKENS
    // =========================================================================

    private JPanel buildTokensTab(SessionProfile profile) {
        JPanel panel = tabPanel();

        JLabel hint = UiTheme.mutedLabel(
            "Define how each token is extracted from login responses and injected into outgoing requests.");
        hint.setBorder(new EmptyBorder(0, 0, UiTheme.SP_SM, 0));

        panel.add(hint, BorderLayout.NORTH);

        String[] cols = {"Type", "Extract From", "Regex (group 1)", "Step #", "Inject At", "Key / Header"};
        tokenTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return true; }
        };

        for (TokenDefinition td : profile.getTokens()) {
            tokenTableModel.addRow(new Object[]{
                td.getTokenType(), td.getExtractFrom(), td.getExtractRegex(),
                td.getLoginStepIndex(), td.getInjectLocation(), td.getInjectKey()
            });
        }

        JTable table = buildTable(tokenTableModel);
        setComboEditor(table, 0, TokenType.values());
        setComboEditor(table, 1, ExtractSource.values());
        setComboEditor(table, 4, TokenLocation.values());
        table.getColumnModel().getColumn(2).setCellRenderer(monoRenderer());

        setColumnWidths(table, new int[]{120, 155, 210, 50, 160, 130});

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
            "Steps execute top-to-bottom. Reference prior step tokens with {{step0:CSRF}} syntax.");
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

        JTable table = buildTable(stepTableModel);
        setComboEditor(table, 1, new String[]{"GET","POST","PUT","PATCH","DELETE"});
        table.getColumnModel().getColumn(2).setCellRenderer(monoRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(monoRenderer());
        setColumnWidths(table, new int[]{120, 68, 260, 210, 140});

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buildTableFooter(stepTableModel, table), BorderLayout.SOUTH);
        return panel;
    }

    // =========================================================================
    // TAB 3: SCOPE
    // =========================================================================

    private JPanel buildScopeTab(SessionProfile profile) {
        JPanel panel = tabPanel();

        // Mode selector
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.SP_SM, 0));
        modeRow.setOpaque(false);
        modeRow.setBorder(new EmptyBorder(0, 0, UiTheme.SP_MD, 0));

        whitelistRadio = styledRadio("Whitelist  (only process matching URLs)");
        blacklistRadio = styledRadio("Blacklist  (skip matching URLs)");

        ButtonGroup group = new ButtonGroup();
        group.add(whitelistRadio);
        group.add(blacklistRadio);

        boolean isWhitelist = profile.getScope().getMode() == ScopeMode.WHITELIST;
        whitelistRadio.setSelected(isWhitelist);
        blacklistRadio.setSelected(!isWhitelist);

        modeRow.add(whitelistRadio);
        modeRow.add(blacklistRadio);
        modeRow.add(UiTheme.mutedLabel("   Wildcard: *.example.com/api/*"));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(UiTheme.mutedLabel("URL pattern rules — supports * wildcard"), BorderLayout.NORTH);
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
        table.getColumnModel().getColumn(0).setCellRenderer(monoRenderer());
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
        panel.setBackground(UiTheme.BG_PANEL);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(UiTheme.SP_LG, UiTheme.SP_XL, UiTheme.SP_LG, UiTheme.SP_XL));

        ErrorCondition ec = profile.getErrorCondition();

        // Section: Trigger
        panel.add(sectionDivider("Trigger Condition"));
        panel.add(Box.createVerticalStrut(UiTheme.SP_MD));

        panel.add(formRow(
            "Trigger refresh on status code(s):",
            statusCodesField = UiTheme.textField(),
            "Comma-separated, e.g. 401, 403"));
        statusCodesField.setText(
            ec.getTriggerOnStatusCodes().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        panel.add(Box.createVerticalStrut(UiTheme.SP_SM));

        panel.add(formRow(
            "Also require body contains (optional):",
            bodyKeywordField = UiTheme.textField(),
            "Only trigger refresh if response body includes this text"));
        bodyKeywordField.setText(ec.getTriggerOnBodyKeyword());

        panel.add(Box.createVerticalStrut(UiTheme.SP_XL));

        // Section: Exclusion
        panel.add(sectionDivider("Refresh Exclusion"));
        panel.add(Box.createVerticalStrut(UiTheme.SP_MD));

        panel.add(formRow(
            "Skip injection for requests matching:",
            excludeUrlField = UiTheme.monoField(),
            "Prevents infinite refresh loop. Enter your login endpoint path."));
        excludeUrlField.setText(ec.getRefreshExcludeUrl());

        panel.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiTheme.BG_PANEL);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(UiTheme.BG_PANEL);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    // =========================================================================
    // ACTION BAR
    // =========================================================================

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UiTheme.BG_PANEL);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, UiTheme.BORDER_SUBTLE));

        // Left: primary actions
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.SP_SM, UiTheme.SP_SM));
        left.setOpaque(false);

        JButton saveBtn = UiTheme.primaryButton("Save");
        saveBtn.addActionListener(e -> saveCurrentProfile());
        left.add(saveBtn);

        JButton runBtn = UiTheme.button("Run Login Now");
        runBtn.setToolTipText("Manually trigger the login sequence to populate tokens");
        runBtn.addActionListener(e -> runLoginNow());
        left.add(runBtn);

        JButton exportBtn = UiTheme.button("Export JSON");
        exportBtn.addActionListener(e -> exportProfile());
        left.add(exportBtn);

        JButton importBtn = UiTheme.button("Import JSON");
        importBtn.addActionListener(e -> importProfile());
        left.add(importBtn);

        // Right: destructive action
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SP_SM, UiTheme.SP_SM));
        right.setOpaque(false);

        JButton deleteBtn = UiTheme.dangerButton("Delete Profile");
        deleteBtn.addActionListener(e -> deleteCurrentProfile());
        right.add(deleteBtn);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    // TABLE BUILDER
    // =========================================================================

    private JPanel buildTableFooter(DefaultTableModel model, JTable table) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SP_SM, UiTheme.SP_SM));
        row.setBackground(UiTheme.BG_PANEL);
        row.setBorder(new MatteBorder(1, 0, 0, 0, UiTheme.BORDER_SUBTLE));

        JButton addBtn = UiTheme.button("+ Add Row");
        addBtn.addActionListener(e -> model.addRow(new Object[model.getColumnCount()]));
        row.add(addBtn);

        JButton removeBtn = UiTheme.button("Remove Row");
        removeBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) model.removeRow(sel);
        });
        row.add(removeBtn);

        return row;
    }

    private JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            // Zebra-stripe rows
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? UiTheme.BG_PANEL : UiTheme.BG_ROW_ALT);
                }
                return c;
            }
        };

        table.setBackground(UiTheme.BG_PANEL);
        table.setForeground(UiTheme.TEXT_PRIMARY);
        table.setFont(UiTheme.FONT_UI);
        table.setGridColor(UiTheme.BORDER_SUBTLE);
        table.setRowHeight(28);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0x1F, 0x6F, 0xEB, 60));
        table.setSelectionForeground(UiTheme.TEXT_PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setBackground(UiTheme.BG_PANEL);
        header.setForeground(UiTheme.TEXT_SECONDARY);
        header.setFont(UiTheme.FONT_LABEL);
        header.setBorder(new MatteBorder(0, 0, 1, 0, UiTheme.BORDER_NORMAL));
        header.setReorderingAllowed(false);

        // Default cell renderer: dark background
        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer();
        defaultRenderer.setBackground(UiTheme.BG_PANEL);
        defaultRenderer.setForeground(UiTheme.TEXT_PRIMARY);
        defaultRenderer.setFont(UiTheme.FONT_UI);
        defaultRenderer.setBorder(new EmptyBorder(0, 8, 0, 8));
        table.setDefaultRenderer(Object.class, defaultRenderer);

        return table;
    }

    private TableCellRenderer monoRenderer() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                setFont(UiTheme.FONT_MONO_SM);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                if (!sel) setBackground(row % 2 == 0 ? UiTheme.BG_PANEL : UiTheme.BG_ROW_ALT);
                return this;
            }
        };
        r.setForeground(UiTheme.TEXT_PRIMARY);
        return r;
    }

    private <T> void setComboEditor(JTable table, int col, T[] values) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setBackground(UiTheme.BG_INPUT);
        combo.setForeground(UiTheme.TEXT_PRIMARY);
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

    /** A standard label-above-field form row for the error/refresh tab */
    private JPanel formRow(String labelText, JTextField field, String hint) {
        JPanel row = new JPanel(new BorderLayout(0, UiTheme.SP_XS));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        row.setPreferredSize(new Dimension(Integer.MAX_VALUE, 58));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UiTheme.FONT_UI_SM);
        lbl.setForeground(UiTheme.TEXT_SECONDARY);
        lbl.setPreferredSize(new Dimension(Integer.MAX_VALUE, 16));
        row.add(lbl, BorderLayout.NORTH);

        field.setToolTipText(hint);
        field.setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));
        row.add(field, BorderLayout.CENTER);

        return row;
    }

    /** Sectional divider with title — a label + line */
    private JPanel sectionDivider(String title) {
        JPanel div = new JPanel(new BorderLayout(UiTheme.SP_SM, 0));
        div.setOpaque(false);
        div.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel lbl = UiTheme.sectionLabel(title);
        lbl.setBorder(null);
        div.add(lbl, BorderLayout.WEST);

        JSeparator line = new JSeparator();
        line.setForeground(UiTheme.BORDER_SUBTLE);
        div.add(line, BorderLayout.CENTER);

        return div;
    }

    private JLabel smallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UiTheme.FONT_UI_SM);
        l.setForeground(UiTheme.TEXT_SECONDARY);
        return l;
    }

    private JTextField compactField(String text, int width) {
        JTextField f = UiTheme.textField();
        f.setText(text);
        f.setPreferredSize(new Dimension(width, 28));
        return f;
    }

    private JRadioButton styledRadio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setFont(UiTheme.FONT_UI_SM);
        r.setForeground(UiTheme.TEXT_SECONDARY);
        r.setBackground(UiTheme.BG_PANEL);
        r.setOpaque(false);
        return r;
    }

    private Component spacer(int w) {
        return Box.createHorizontalStrut(w);
    }

    private JPanel tabPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(UiTheme.BG_PANEL);
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

        // Tokens
        List<TokenDefinition> tokens = new ArrayList<>();
        for (int i = 0; i < tokenTableModel.getRowCount(); i++) {
            TokenDefinition td = new TokenDefinition();
            td.setTokenType(val(tokenTableModel, i, 0, TokenType.class));
            td.setExtractFrom(val(tokenTableModel, i, 1, ExtractSource.class));
            td.setExtractRegex(str(tokenTableModel.getValueAt(i, 2)));
            try { td.setLoginStepIndex(Integer.parseInt(str(tokenTableModel.getValueAt(i, 3)))); }
            catch (NumberFormatException ignored) {}
            td.setInjectLocation(val(tokenTableModel, i, 4, TokenLocation.class));
            td.setInjectKey(str(tokenTableModel.getValueAt(i, 5)));
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

        // Brief feedback via toggle button text
        enabledToggle.setForeground(UiTheme.STATUS_OK);
        Timer t = new Timer(1200, e -> enabledToggle.setForeground(
            enabledToggle.isSelected() ? UiTheme.STATUS_OK : UiTheme.TEXT_MUTED));
        t.setRepeats(false);
        t.start();
    }

    private void runLoginNow() {
        if (currentProfile == null) return;
        saveCurrentProfile();
        new Thread(() -> {
            LoginExecutor exec = new LoginExecutor(api, tokenStore, ActivityLogger.getInstance());
            exec.execute(currentProfile);
        }, "sessionx-login").start();
    }

    private void deleteCurrentProfile() {
        if (currentProfile == null) return;
        int confirm = JOptionPane.showConfirmDialog(root,
            "Delete \"" + currentProfile.getName() + "\"?",
            "Confirm", JOptionPane.YES_NO_OPTION);
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
                JOptionPane.showMessageDialog(root, "Profile exported.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(root, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
                JOptionPane.showMessageDialog(root, "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private String str(Object val) { return val == null ? "" : val.toString(); }

    @SuppressWarnings("unchecked")
    private <T> T val(DefaultTableModel m, int row, int col, Class<T> type) {
        Object v = m.getValueAt(row, col);
        if (type.isInstance(v)) return (T) v;
        return null;
    }

    public JPanel getPanel() { return root; }
}

package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.*;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.util.JsonPathUtil;
import com.burpext.sessionx.util.RegexUtil;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive Login Step Builder dialog — ATOR-style UX.
 *
 * Workflow:
 *  1. User types (or pastes) a request URL / headers / body
 *  2. Clicks "▶ Send Request" — response populates the right panel
 *  3. Selects any text in the response → clicks "+ Extract"
 *     - A variable name prompt appears; auto-generated regex/JSONPath shown
 *     - Entry added to the Extracted Variables table
 *  4. Clicks "Done" — the dialog commits changes:
 *     - Updates the LoginStep fields (label, method, url, headers, body)
 *     - Returns a list of TokenDefinitions for the extracted variables
 *
 * Cross-step variable insertion:
 *  - A "{{" popup appears in the body/headers textarea offering variables
 *    from prior steps (passed in via availableVars).
 */
public class LoginStepBuilderDialog extends JDialog {

    // --- Result ---
    private LoginStep       resultStep;
    private List<TokenDefinition> resultTokens = new ArrayList<>();
    private boolean         committed = false;

    // --- Services ---
    private final MontoyaApi api;
    private final int        stepIndex;   // 0-based index of the step being edited
    private final List<String> availableVars;  // variable names from prior steps

    // --- UI: Request side ---
    private JTextField  labelField;
    private JComboBox<String> methodCombo;
    private JTextField  urlField;
    private JTextArea   headersArea;
    private JTextArea   bodyArea;

    // --- UI: Response side ---
    private JTextArea   responseArea;
    private JLabel      statusLabel;

    // --- UI: Extraction table ---
    private DefaultTableModel extractTableModel;

    // Column indices for extraction table
    private static final int COL_VAR_NAME  = 0;
    private static final int COL_JSONPATH  = 1;
    private static final int COL_REGEX     = 2;
    private static final int COL_SOURCE    = 3;
    private static final int COL_PREVIEW   = 4;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * @param owner         Parent frame
     * @param api           Burp Montoya API
     * @param stepIndex     0-based index of this step in the sequence
     * @param existing      Pre-populated step (for "Edit Step" mode), or null for new step
     * @param existingTDs   Pre-populated token definitions for this step, or empty list
     * @param availableVars Variable names already extracted in previous steps
     */
    public LoginStepBuilderDialog(Frame owner, MontoyaApi api,
                                   int stepIndex, LoginStep existing,
                                   List<TokenDefinition> existingTDs,
                                   List<String> availableVars) {
        super(owner, "Step " + (stepIndex + 1) + " — Login Request Builder", true);
        this.api           = api;
        this.stepIndex     = stepIndex;
        this.availableVars = availableVars != null ? availableVars : List.of();

        buildUi(existing, existingTDs);

        setSize(1150, 700);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    // -------------------------------------------------------------------------
    // UI Construction
    // -------------------------------------------------------------------------

    private void buildUi(LoginStep existing, List<TokenDefinition> existingTDs) {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Top split: request (left) | response (right)
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildRequestPanel(existing), buildResponsePanel());
        topSplit.setDividerLocation(480);
        topSplit.setResizeWeight(0.45);
        topSplit.setBorder(null);
        topSplit.setDividerSize(5);

        // Bottom: extraction table
        JPanel bottomPanel = buildExtractionPanel(existingTDs);

        // Main vertical split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomPanel);
        mainSplit.setDividerLocation(380);
        mainSplit.setResizeWeight(0.6);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(5);

        root.add(mainSplit, BorderLayout.CENTER);
        root.add(buildButtonBar(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    // --- Request Panel ---

    private JPanel buildRequestPanel(LoginStep existing) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(titledBorder("Request"));

        // Method + URL row
        JPanel urlRow = new JPanel(new BorderLayout(4, 0));
        String[] methods = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"};
        methodCombo = new JComboBox<>(methods);
        methodCombo.setFont(UiTheme.FONT_UI);
        methodCombo.setPreferredSize(new Dimension(90, 28));
        urlField = UiTheme.monoField();
        urlField.setToolTipText("Full URL, e.g. http://api.example.com/auth/login");
        urlRow.add(methodCombo, BorderLayout.WEST);
        urlRow.add(urlField, BorderLayout.CENTER);

        // Label field
        JPanel labelRow = new JPanel(new BorderLayout(4, 0));
        JLabel lbl = new JLabel("Label:");
        lbl.setFont(UiTheme.FONT_UI_SM);
        lbl.setForeground(UiTheme.getSecondaryText());
        lbl.setPreferredSize(new Dimension(50, 24));
        labelField = UiTheme.textField();
        labelRow.add(lbl, BorderLayout.WEST);
        labelRow.add(labelField, BorderLayout.CENTER);

        // Headers area
        headersArea = monoArea(5);
        headersArea.setToolTipText("One header per line: Content-Type: application/json");
        installVarAutocomplete(headersArea);

        // Body area
        bodyArea = monoArea(8);
        bodyArea.setToolTipText("Request body. Use {{step0:varName}} to reference prior step variables.");
        installVarAutocomplete(bodyArea);

        JPanel north = new JPanel(new GridLayout(2, 1, 0, 4));
        north.add(labelRow);
        north.add(urlRow);

        panel.add(north, BorderLayout.NORTH);

        JSplitPane hbSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            labeledScroll("Headers", headersArea),
            labeledScroll("Body", bodyArea));
        hbSplit.setDividerLocation(100);
        hbSplit.setBorder(null);
        hbSplit.setDividerSize(4);
        panel.add(hbSplit, BorderLayout.CENTER);

        // Send button
        JButton sendBtn = UiTheme.button("▶  Send Request");
        sendBtn.setFont(UiTheme.FONT_BOLD);
        sendBtn.addActionListener(e -> sendRequest());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        south.add(sendBtn);
        panel.add(south, BorderLayout.SOUTH);

        // Pre-populate if editing an existing step
        if (existing != null) {
            labelField.setText(existing.getLabel());
            methodCombo.setSelectedItem(existing.getMethod());
            urlField.setText(existing.getUrl());
            StringBuilder hdr = new StringBuilder();
            existing.getHeaders().forEach((k, v) -> hdr.append(k).append(": ").append(v).append("\n"));
            headersArea.setText(hdr.toString());
            bodyArea.setText(existing.getBody());
        } else {
            labelField.setText("Step " + (stepIndex + 1));
        }

        return panel;
    }

    // --- Response Panel ---

    private JPanel buildResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(titledBorder("Response  (select a value and click  + Extract)"));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(UiTheme.FONT_UI_SM);
        statusLabel.setForeground(UiTheme.getMutedText());
        panel.add(statusLabel, BorderLayout.NORTH);

        responseArea = new JTextArea();
        responseArea.setFont(UiTheme.FONT_MONO_SM);
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(false);
        responseArea.setTabSize(2);

        JScrollPane scrollPane = new JScrollPane(responseArea);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton extractBtn = UiTheme.button("+ Extract Selected Value");
        extractBtn.setToolTipText("Select text in the response above, then click to extract it");
        extractBtn.addActionListener(e -> extractSelection());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        south.add(extractBtn);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    // --- Extraction Table ---

    private JPanel buildExtractionPanel(List<TokenDefinition> existingTDs) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(titledBorder("Extracted Variables  (these become tokens injected into later requests)"));

        String[] cols = {"Variable Name", "JSONPath", "Regex (fallback)", "Source", "Inject As", "Inject Key"};
        extractTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c != COL_PREVIEW; }
        };

        // Pre-populate from existing token definitions for this step
        if (existingTDs != null) {
            for (TokenDefinition td : existingTDs) {
                extractTableModel.addRow(new Object[]{
                    td.getVariableName(),
                    td.getExtractJsonPath(),
                    td.getExtractRegex(),
                    td.getExtractFrom(),
                    td.getInjectLocation(),
                    td.getInjectKey()
                });
            }
        }

        JTable table = buildExtractTable();
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        JButton addRow = UiTheme.smallButton("➕ Add Row", "Manually add a variable row");
        addRow.addActionListener(e -> extractTableModel.addRow(new Object[]{"", "", "", ExtractSource.RESPONSE_BODY_JSON, TokenLocation.AUTHORIZATION_HEADER, "Authorization"}));
        footer.add(addRow);

        JButton removeRow = UiTheme.smallButton("➖ Remove Row", "Remove selected row");
        removeRow.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) extractTableModel.removeRow(sel);
        });
        footer.add(removeRow);

        JLabel hint = UiTheme.mutedLabel(" Variables are usable in later steps as  {{step" + stepIndex + ":varName}}");
        footer.add(hint);

        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JTable buildExtractTable() {
        JTable table = new JTable(extractTableModel);
        table.setFont(UiTheme.FONT_UI);
        table.setRowHeight(26);
        table.getTableHeader().setFont(UiTheme.FONT_BOLD);
        table.getTableHeader().setReorderingAllowed(false);
        table.setGridColor(UiTheme.getBorderColor());
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);

        // Combo editors
        setCombo(table, 3, ExtractSource.values());
        setCombo(table, 4, TokenLocation.values());

        // Column widths
        int[] widths = {140, 180, 200, 155, 165, 120};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Mono font for path/regex columns
        DefaultTableCellRenderer mono = new DefaultTableCellRenderer();
        mono.setFont(UiTheme.FONT_MONO_SM);
        table.getColumnModel().getColumn(1).setCellRenderer(mono);
        table.getColumnModel().getColumn(2).setCellRenderer(mono);

        return table;
    }

    // --- Button Bar ---

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        JButton cancelBtn = UiTheme.button("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        bar.add(cancelBtn);

        JButton doneBtn = UiTheme.button("✔ Done");
        doneBtn.setFont(UiTheme.FONT_BOLD);
        doneBtn.addActionListener(e -> commit());
        bar.add(doneBtn);

        return bar;
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /** Fires the HTTP request and populates the response panel. */
    private void sendRequest() {
        String method = (String) methodCombo.getSelectedItem();
        String url    = urlField.getText().trim();

        if (url.isBlank()) {
            showError("Please enter a URL.");
            return;
        }

        statusLabel.setText("Sending…");
        statusLabel.setForeground(UiTheme.getMutedText());
        responseArea.setText("");

        // Run in background so Swing EDT stays responsive
        new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.httpRequestFromUrl(url);
                req = req.withMethod(method);

                // Parse headers
                Map<String, String> hdrs = parseHeaders(headersArea.getText());
                String body = bodyArea.getText();

                if (body != null && !body.isBlank()) {
                    if (!hdrs.containsKey("Content-Type")) {
                        req = req.withHeader("Content-Type", "application/json");
                    }
                    req = req.withBody(body);
                }
                for (Map.Entry<String, String> h : hdrs.entrySet()) {
                    req = req.withHeader(h.getKey(), h.getValue());
                }

                HttpRequestResponse resp = api.http().sendRequest(req);

                int    code   = resp.response().statusCode();
                String reason = resp.response().reasonPhrase();
                String raw    = resp.response().toString();

                SwingUtilities.invokeLater(() -> {
                    responseArea.setText(raw);
                    responseArea.setCaretPosition(0);
                    statusLabel.setText("HTTP " + code + " " + reason);
                    statusLabel.setForeground(code < 300 ? UiTheme.STATUS_OK
                        : code < 500 ? new Color(0xE6A817) : UiTheme.STATUS_ERR);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    responseArea.setText("Error: " + ex.getMessage());
                    statusLabel.setText("Request failed");
                    statusLabel.setForeground(UiTheme.STATUS_ERR);
                });
            }
        }, "sessionx-step-builder").start();
    }

    /**
     * Reads the current text selection in the response area and opens a
     * variable-naming dialog. On confirmation, adds a row to the extraction table
     * with an auto-generated regex and JSONPath.
     */
    private void extractSelection() {
        String fullText = responseArea.getText();
        int selStart = responseArea.getSelectionStart();
        int selEnd   = responseArea.getSelectionEnd();

        if (selStart == selEnd) {
            showError("Please select a value in the response text first.");
            return;
        }

        String selectedValue = fullText.substring(selStart, selEnd).trim();
        if (selectedValue.isBlank()) {
            showError("Selection is empty — please select the actual value.");
            return;
        }

        // Prompt for variable name
        JPanel prompt = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JTextField varNameField = UiTheme.textField();
        varNameField.setPreferredSize(new Dimension(220, 28));
        varNameField.setToolTipText("e.g. access_token, csrf, session_id");

        // Auto-suggest name from JSON key if applicable
        String suggestedName = guessVariableName(fullText, selStart, selectedValue);
        varNameField.setText(suggestedName);

        String autoRegex    = RegexUtil.generateFromContext(fullText, selStart, selEnd);
        String autoJsonPath = JsonPathUtil.deriveJsonPath(toBodyOnly(fullText), selectedValue);

        JTextField regexField    = UiTheme.monoField();
        JTextField jsonPathField = UiTheme.monoField();
        regexField.setText(autoRegex);
        jsonPathField.setText(autoJsonPath);

        addRow(prompt, gc, 0, "Variable name:", varNameField);
        addRow(prompt, gc, 1, "JSONPath (preferred):", jsonPathField);
        addRow(prompt, gc, 2, "Regex fallback:", regexField);
        addRow(prompt, gc, 3, "Selected value:", readonlyField(selectedValue));

        int res = JOptionPane.showConfirmDialog(this, prompt,
            "Extract Variable", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res != JOptionPane.OK_OPTION) return;

        String varName = varNameField.getText().trim();
        if (varName.isBlank()) {
            showError("Variable name cannot be empty.");
            return;
        }

        // Determine inject location heuristic
        ExtractSource source = guessSource(fullText, selStart);
        TokenLocation injectLoc = guessInjectLocation(varName);
        String injectKey = guessInjectKey(varName, injectLoc);

        extractTableModel.addRow(new Object[]{
            varName,
            jsonPathField.getText().trim(),
            regexField.getText().trim(),
            source,
            injectLoc,
            injectKey
        });

        // Highlight extracted selection in response
        highlightSelection(selStart, selEnd);
    }

    /** Commits the dialog state into resultStep and resultTokens. */
    private void commit() {
        String url = urlField.getText().trim();
        if (url.isBlank()) {
            showError("URL cannot be empty.");
            return;
        }

        // Build LoginStep
        resultStep = new LoginStep();
        resultStep.setLabel(labelField.getText().trim().isEmpty()
            ? "Step " + (stepIndex + 1) : labelField.getText().trim());
        resultStep.setMethod((String) methodCombo.getSelectedItem());
        resultStep.setUrl(url);
        resultStep.setBody(bodyArea.getText());

        Map<String, String> hdrs = parseHeaders(headersArea.getText());
        resultStep.setHeaders(hdrs);

        // Build TokenDefinitions from extraction table
        resultTokens = new ArrayList<>();
        for (int row = 0; row < extractTableModel.getRowCount(); row++) {
            String varName = str(extractTableModel.getValueAt(row, COL_VAR_NAME));
            if (varName.isBlank()) continue;

            TokenDefinition td = new TokenDefinition();
            td.setVariableName(varName);
            td.setExtractJsonPath(str(extractTableModel.getValueAt(row, COL_JSONPATH)));
            td.setExtractRegex(str(extractTableModel.getValueAt(row, COL_REGEX)));
            Object src = extractTableModel.getValueAt(row, COL_SOURCE);
            if (src instanceof ExtractSource) td.setExtractFrom((ExtractSource) src);
            Object loc = extractTableModel.getValueAt(row, COL_VAR_NAME + 4); // col 4
            if (loc instanceof TokenLocation) td.setInjectLocation((TokenLocation) loc);
            td.setInjectKey(str(extractTableModel.getValueAt(row, 5)));
            td.setLoginStepIndex(stepIndex);

            // Heuristic: set TokenType from variable name for legacy injection
            td.setTokenType(guessTokenType(varName));

            resultTokens.add(td);
        }

        committed = true;
        dispose();
    }

    // -------------------------------------------------------------------------
    // Variable autocomplete  ({{  triggers popup in body/header areas)
    // -------------------------------------------------------------------------

    private void installVarAutocomplete(JTextArea area) {
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == '{') {
                    SwingUtilities.invokeLater(() -> {
                        String text = area.getText();
                        int caret   = area.getCaretPosition();
                        if (caret >= 2 && text.charAt(caret - 2) == '{' && text.charAt(caret - 1) == '{') {
                            showVarPopup(area, caret);
                        }
                    });
                }
            }
        });
    }

    private void showVarPopup(JTextArea area, int caretPos) {
        if (availableVars.isEmpty()) return;

        JPopupMenu popup = new JPopupMenu();
        for (String varName : availableVars) {
            String snippet = "step" + (stepIndex - 1) + ":" + varName + "}}";
            JMenuItem item = new JMenuItem(snippet);
            item.setFont(UiTheme.FONT_MONO_SM);
            item.addActionListener(e -> {
                try {
                    area.getDocument().insertString(caretPos, snippet, null);
                } catch (Exception ex) { /* ignore */ }
            });
            popup.add(item);
        }

        try {
            Rectangle rect = area.modelToView2D(caretPos).getBounds();
            popup.show(area, rect.x, rect.y + rect.height);
        } catch (Exception ignore) {}
    }

    // -------------------------------------------------------------------------
    // Heuristics
    // -------------------------------------------------------------------------

    private String guessVariableName(String fullText, int selStart, String selectedValue) {
        // Try: "keyName": "SELECTED"  -> suggest keyName
        int window = Math.max(0, selStart - 80);
        String before = fullText.substring(window, selStart);
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"([^\"]+)\"\\s*:\\s*\"?$")
            .matcher(before);
        if (m.find()) return m.group(1);
        return "";
    }

    private ExtractSource guessSource(String fullText, int selStart) {
        // Heuristic: if selection is in the first 500 chars it's usually headers
        String snippet = fullText.substring(0, Math.min(selStart, fullText.length()));
        // Simple marker: the blank line separating HTTP headers from body
        int headerBodySep = fullText.indexOf("\r\n\r\n");
        if (headerBodySep < 0) headerBodySep = fullText.indexOf("\n\n");
        if (headerBodySep > 0 && selStart < headerBodySep) {
            // In headers
            if (fullText.substring(0, selStart).toLowerCase().contains("set-cookie")) {
                return ExtractSource.RESPONSE_COOKIE;
            }
            return ExtractSource.RESPONSE_HEADER;
        }
        // In body — check if it looks like JSON
        String bodySnip = fullText.substring(Math.max(0, headerBodySep), Math.min(fullText.length(), headerBodySep + 30)).trim();
        if (bodySnip.startsWith("{") || bodySnip.startsWith("[")) {
            return ExtractSource.RESPONSE_BODY_JSON;
        }
        return ExtractSource.RESPONSE_BODY_HTML;
    }

    private TokenLocation guessInjectLocation(String varName) {
        String lower = varName.toLowerCase();
        if (lower.contains("bearer") || lower.contains("access") || lower.contains("jwt")) {
            return TokenLocation.AUTHORIZATION_HEADER;
        }
        if (lower.contains("refresh")) return TokenLocation.CUSTOM_HEADER;
        if (lower.contains("csrf")  || lower.contains("xsrf")) return TokenLocation.CUSTOM_HEADER;
        if (lower.contains("cookie") || lower.contains("session")) return TokenLocation.COOKIE;
        return TokenLocation.AUTHORIZATION_HEADER;
    }

    private String guessInjectKey(String varName, TokenLocation loc) {
        if (loc == TokenLocation.AUTHORIZATION_HEADER) return "Authorization";
        String lower = varName.toLowerCase();
        if (lower.contains("csrf")) return "X-CSRF-Token";
        if (lower.contains("xsrf")) return "X-XSRF-Token";
        if (lower.contains("refresh")) return "X-Refresh-Token";
        return varName;
    }

    private TokenType guessTokenType(String varName) {
        String lower = varName.toLowerCase();
        if (lower.contains("refresh")) return TokenType.REFRESH;
        if (lower.contains("csrf") || lower.contains("xsrf")) return TokenType.CSRF;
        if (lower.contains("cookie") || lower.contains("session")) return TokenType.SESSION_COOKIE;
        if (lower.contains("custom")) return TokenType.CUSTOM;
        return TokenType.BEARER;
    }

    /** Strips HTTP response headers — returns just the body portion of the response. */
    private String toBodyOnly(String fullResponse) {
        int sep = fullResponse.indexOf("\r\n\r\n");
        if (sep < 0) sep = fullResponse.indexOf("\n\n");
        return sep > 0 ? fullResponse.substring(sep).trim() : fullResponse;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private Map<String, String> parseHeaders(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String line : raw.split("\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                if (!key.isBlank()) map.put(key, val);
            }
        }
        return map;
    }

    private void highlightSelection(int start, int end) {
        try {
            DefaultHighlighter.DefaultHighlightPainter painter =
                new DefaultHighlighter.DefaultHighlightPainter(new Color(0x90EE90, false)); // light green
            responseArea.getHighlighter().addHighlight(start, end, painter);
        } catch (Exception ignore) {}
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation", JOptionPane.WARNING_MESSAGE);
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }

    private JTextArea monoArea(int rows) {
        JTextArea area = new JTextArea(rows, 40);
        area.setFont(UiTheme.FONT_MONO_SM);
        area.setTabSize(2);
        area.setLineWrap(false);
        return area;
    }

    private JScrollPane labeledScroll(String title, JTextArea area) {
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()),
            title, TitledBorder.LEFT, TitledBorder.TOP,
            UiTheme.FONT_UI_SM, UiTheme.getSecondaryText()));
        return sp;
    }

    private TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, UiTheme.getBorderColor()),
            title, TitledBorder.LEFT, TitledBorder.TOP,
            UiTheme.FONT_BOLD, UiTheme.getPrimaryText());
    }

    private <T> void setCombo(JTable table, int col, T[] values) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setFont(UiTheme.FONT_UI);
        table.getColumnModel().getColumn(col).setCellEditor(new DefaultCellEditor(combo));
    }

    private void addRow(JPanel p, GridBagConstraints gc, int row, String labelText, JComponent field) {
        gc.gridy = row;
        gc.gridx = 0; gc.weightx = 0;
        JLabel l = new JLabel(labelText);
        l.setFont(UiTheme.FONT_UI_SM);
        l.setForeground(UiTheme.getSecondaryText());
        p.add(l, gc);
        gc.gridx = 1; gc.weightx = 1.0;
        p.add(field, gc);
    }

    private JTextField readonlyField(String text) {
        JTextField f = UiTheme.monoField();
        f.setText(text);
        f.setEditable(false);
        f.setForeground(UiTheme.getMutedText());
        return f;
    }

    // -------------------------------------------------------------------------
    // Result accessors (called after dialog closes)
    // -------------------------------------------------------------------------

    public boolean wasCommitted()          { return committed; }
    public LoginStep getResultStep()       { return resultStep; }
    public List<TokenDefinition> getResultTokens() { return resultTokens; }
}

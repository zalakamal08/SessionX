package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Detail view for a single test result.
 *
 * Uses Burp's native Montoya HTTP editors (Pretty / Raw / Hex, syntax
 * highlighting, search, message inspector). A slim one-line status strip
 * shows the request identity and the Original → Modified → Unauth verdict.
 */
public class ResultDetailPanel extends JPanel {

    // Verdict colors (shared with the result tables)
    private static final Color C_VULN     = new Color(0xC0, 0x2A, 0x38);
    private static final Color C_ENFORCED = new Color(0x1E, 0x7E, 0x34);
    private static final Color C_INTEREST = new Color(0xB8, 0x6E, 0x00);
    private static final Color C_MUTED    = new Color(0x6C, 0x75, 0x7D);
    private static final Color C_METHOD   = new Color(0x2A, 0x5D, 0xB0);

    private final MontoyaApi api;

    private final JLabel identity = new JLabel(" ");   // #id  METHOD  URL
    private final JLabel summary  = new JLabel(" ");    // Orig 200 · Mod 200 · Unauth 401

    // Native Burp editors (read-only)
    private final HttpRequestEditor  origRequest;
    private final HttpResponseEditor origResponse;
    private final HttpRequestEditor  modRequest;
    private final HttpResponseEditor modResponse;
    private final HttpRequestEditor  unauthRequest;
    private final HttpResponseEditor unauthResponse;

    private final JTabbedPane tabs;

    public ResultDetailPanel(MontoyaApi api) {
        this.api = api;

        origRequest    = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        origResponse   = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        modRequest     = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        modResponse    = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        unauthRequest  = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        unauthResponse = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);

        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.addTab("Original",        buildSplit(origRequest, origResponse));
        tabs.addTab("Modified",        buildSplit(modRequest, modResponse));
        tabs.addTab("Unauthenticated", buildSplit(unauthRequest, unauthResponse));
        add(tabs, BorderLayout.CENTER);

        showEmpty();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public void show(TestResult result) {
        if (result == null) { showEmpty(); return; }

        identity.setText(String.format(
                "<html><b>#%d</b> &nbsp;<font color='#2A5DB0'>%s</font>&nbsp; %s</html>",
                result.getId(), result.getMethod(), escape(result.getUrl())));
        identity.setToolTipText(result.getUrl());

        summary.setText(buildSummary(result));

        setRequest(origRequest, result.getOrigRequestBytes());
        setResponse(origResponse, result.getOrigResponseBytes());
        setRequest(modRequest, result.getModRequestBytes());
        setResponse(modResponse, result.getModStatus() == -1 ? null : result.getModResponseBytes());
        setRequest(unauthRequest, result.getUnauthRequestBytes());
        setResponse(unauthResponse, result.getUnauthStatus() == -1 ? null : result.getUnauthResponseBytes());
    }

    public void clear() { showEmpty(); }

    /** Pre-select a tab by index: 0=Original, 1=Modified, 2=Unauthenticated */
    public void selectTab(int index) {
        if (index >= 0 && index < tabs.getTabCount()) tabs.setSelectedIndex(index);
    }

    // ── UI construction ─────────────────────────────────────────────────────

    private JComponent buildHeader() {
        identity.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 11f));
        summary.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(new EmptyBorder(6, 10, 6, 10));
        header.add(identity, BorderLayout.CENTER);
        header.add(summary, BorderLayout.EAST);
        return header;
    }

    private JSplitPane buildSplit(HttpRequestEditor req, HttpResponseEditor resp) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                titled("Request", req.uiComponent()),
                titled("Response", resp.uiComponent()));
        split.setResizeWeight(0.5);
        split.setDividerSize(4);
        return split;
    }

    private JPanel titled(String label, Component body) {
        JLabel header = new JLabel("  " + label);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setForeground(C_MUTED);
        header.setBorder(new EmptyBorder(3, 2, 3, 2));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    // ── State helpers ───────────────────────────────────────────────────────

    private void showEmpty() {
        identity.setText("Select a row to inspect the request / response pair");
        identity.setToolTipText(null);
        summary.setText(" ");
        setRequest(origRequest, null);
        setResponse(origResponse, null);
        setRequest(modRequest, null);
        setResponse(modResponse, null);
        setRequest(unauthRequest, null);
        setResponse(unauthResponse, null);
    }

    /** One-line, color-coded summary: Orig 200 4682B · Mod 200 🔴 · Unauth 401 🟢 */
    private String buildSummary(TestResult r) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("<font color='#6C757D'>ORIG</font> %d&nbsp;·&nbsp;%dB",
                r.getOrigStatus(), r.getOrigLength()));
        sb.append("&nbsp;&nbsp;&nbsp;");
        sb.append(part("MOD", r.getModStatus(), r.getModLength(), r.getModVulnStatus()));
        sb.append("&nbsp;&nbsp;&nbsp;");
        sb.append(part("UNAUTH", r.getUnauthStatus(), r.getUnauthLength(), r.getUnauthVulnStatus()));
        sb.append("</html>");
        return sb.toString();
    }

    private String part(String label, int status, int length, VulnerabilityStatus vuln) {
        if (status == -1) {
            return String.format("<font color='#6C757D'>%s replaying…</font>", label);
        }
        String hex = toHex(colorFor(vuln));
        return String.format(
                "<font color='#6C757D'>%s</font> <font color='%s'><b>%d</b>&nbsp;·&nbsp;%dB</font>",
                label, hex, status, length);
    }

    private void setRequest(HttpRequestEditor editor, byte[] bytes) {
        try {
            editor.setRequest(HttpRequest.httpRequest(
                    ByteArray.byteArray(bytes != null ? bytes : new byte[0])));
        } catch (Exception ignored) {
            // Malformed bytes — leave the editor unchanged rather than crash the UI.
        }
    }

    private void setResponse(HttpResponseEditor editor, byte[] bytes) {
        try {
            editor.setResponse(HttpResponse.httpResponse(
                    ByteArray.byteArray(bytes != null ? bytes : new byte[0])));
        } catch (Exception ignored) {
            // Malformed bytes — leave the editor unchanged rather than crash the UI.
        }
    }

    private static Color colorFor(VulnerabilityStatus status) {
        if (status == null) return C_MUTED;
        return switch (status) {
            case VULNERABLE  -> C_VULN;
            case ENFORCED    -> C_ENFORCED;
            case INTERESTING -> C_INTEREST;
            default          -> C_MUTED;
        };
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

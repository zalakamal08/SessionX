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
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Detail view for a single test result.
 *
 * Uses Burp's native Montoya HTTP editors (Pretty / Raw / Hex, syntax
 * highlighting, search, message inspector) instead of plain text areas, and
 * shows an at-a-glance comparison header across the three request variants.
 */
public class ResultDetailPanel extends JPanel {

    // Verdict colors (shared with the result tables)
    private static final Color C_VULN     = new Color(0xC0, 0x2A, 0x38);
    private static final Color C_ENFORCED = new Color(0x1E, 0x7E, 0x34);
    private static final Color C_INTEREST = new Color(0xB8, 0x6E, 0x00);
    private static final Color C_PENDING  = new Color(0x6C, 0x75, 0x7D);
    private static final Color C_CARD_BG   = new Color(0xF7, 0xF7, 0xF7);
    private static final Color C_CARD_LINE = new Color(0xDD, 0xDD, 0xDD);

    private final MontoyaApi api;

    private final JLabel idLabel     = new JLabel(" ");
    private final JLabel methodLabel = new JLabel(" ");
    private final JLabel urlLabel    = new JLabel(" ");

    // Comparison cards
    private final Card origCard   = new Card("ORIGINAL");
    private final Card modCard    = new Card("MODIFIED");
    private final Card unauthCard = new Card("UNAUTH");

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

        idLabel.setText("#" + result.getId());
        methodLabel.setText(result.getMethod());
        urlLabel.setText(result.getUrl());
        urlLabel.setToolTipText(result.getUrl());

        origCard.set(result.getOrigStatus(), result.getOrigLength(), null);
        modCard.set(result.getModStatus(), result.getModLength(), result.getModVulnStatus());
        unauthCard.set(result.getUnauthStatus(), result.getUnauthLength(), result.getUnauthVulnStatus());

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
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Title line: #id  METHOD  URL
        idLabel.setFont(idLabel.getFont().deriveFont(Font.BOLD, 13f));
        idLabel.setForeground(C_PENDING);
        methodLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        methodLabel.setForeground(new Color(0x2A, 0x5D, 0xB0));
        urlLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        title.add(idLabel);
        title.add(methodLabel);
        title.add(urlLabel);

        // Comparison cards
        JPanel cards = new JPanel(new GridLayout(1, 3, 8, 0));
        cards.add(origCard);
        cards.add(modCard);
        cards.add(unauthCard);

        header.add(title, BorderLayout.NORTH);
        header.add(cards, BorderLayout.CENTER);
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
        header.setForeground(C_PENDING);
        header.setBorder(new EmptyBorder(3, 2, 3, 2));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    // ── State helpers ───────────────────────────────────────────────────────

    private void showEmpty() {
        idLabel.setText("—");
        methodLabel.setText("");
        urlLabel.setText("Select a row to inspect the request / response pair");
        urlLabel.setToolTipText(null);
        origCard.reset();
        modCard.reset();
        unauthCard.reset();
        setRequest(origRequest, null);
        setResponse(origResponse, null);
        setRequest(modRequest, null);
        setResponse(modResponse, null);
        setRequest(unauthRequest, null);
        setResponse(unauthResponse, null);
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
        if (status == null) return C_PENDING;
        return switch (status) {
            case VULNERABLE  -> C_VULN;
            case ENFORCED    -> C_ENFORCED;
            case INTERESTING -> C_INTEREST;
            default          -> C_PENDING;
        };
    }

    /** Compact card showing status code, byte length, and (optionally) the verdict. */
    private static class Card extends JPanel {
        private final JLabel value  = new JLabel("—", SwingConstants.CENTER);
        private final JLabel verdict = new JLabel(" ", SwingConstants.CENTER);

        Card(String heading) {
            setLayout(new BorderLayout());
            setBackground(C_CARD_BG);
            setBorder(new CompoundBorder(
                    new LineBorder(C_CARD_LINE, 1, true),
                    new EmptyBorder(5, 8, 5, 8)));

            JLabel title = new JLabel(heading, SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
            title.setForeground(C_PENDING);

            value.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
            verdict.setFont(verdict.getFont().deriveFont(Font.BOLD, 10f));

            add(title, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(verdict, BorderLayout.SOUTH);
        }

        void set(int status, int length, VulnerabilityStatus vuln) {
            if (status == -1) {
                value.setText("replaying…");
                value.setForeground(C_PENDING);
                verdict.setText(" ");
                return;
            }
            value.setText(status + "  ·  " + length + " B");
            Color c = colorFor(vuln);
            value.setForeground(vuln == null ? Color.DARK_GRAY : c);
            verdict.setText(vuln == null ? "baseline" : vuln.toString());
            verdict.setForeground(vuln == null ? C_PENDING : c);
        }

        void reset() {
            value.setText("—");
            value.setForeground(C_PENDING);
            verdict.setText(" ");
        }
    }
}

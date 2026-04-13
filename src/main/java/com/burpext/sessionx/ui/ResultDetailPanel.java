package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;

/**
 * Right-panel showing request/response detail for a selected table row.
 *
 * Layout:
 *   ┌──────────────────────────────────────────┐
 *   │  [Original Request] [Original Response]  │  ← tab 1
 *   │  [Modified Request] [Modified Response]  │  ← tab 2
 *   └──────────────────────────────────────────┘
 *
 * Each tab contains a vertical split: top = request, bottom = response.
 */
public class ResultDetailPanel extends JPanel {

    // ─── Colors ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK       = new Color(0xFA, 0xFA, 0xFA);
    private static final Color BG_PANEL      = new Color(0xF3, 0xF3, 0xF5);
    private static final Color BG_TEXT       = Color.WHITE;
    private static final Color FG_TEXT       = new Color(0x20, 0x21, 0x24);
    private static final Color FG_DIM        = new Color(0x5F, 0x63, 0x68);
    private static final Color ACCENT_RED    = new Color(0xD9, 0x30, 0x25);
    private static final Color ACCENT_GREEN  = new Color(0x1E, 0x8E, 0x3E);
    private static final Color ACCENT_YELLOW = new Color(0xE3, 0x74, 0x00);
    private static final Color ACCENT_BLUE   = new Color(0x1A, 0x73, 0xE8);

    private final JTabbedPane tabs;

    // Original tab
    private final JTextArea origRequestArea  = makeTextArea();
    private final JTextArea origResponseArea = makeTextArea();

    // Modified tab
    private final JTextArea modRequestArea   = makeTextArea();
    private final JTextArea modResponseArea  = makeTextArea();

    // Status banner
    private final JLabel statusBanner = new JLabel(" ", SwingConstants.CENTER);

    public ResultDetailPanel() {
        setLayout(new BorderLayout());
        setBackground(BG_DARK);

        // Status banner at top
        statusBanner.setFont(new Font("JetBrains Mono", Font.BOLD, 13));
        statusBanner.setOpaque(true);
        statusBanner.setBackground(BG_PANEL);
        statusBanner.setForeground(FG_DIM);
        statusBanner.setBorder(new EmptyBorder(8, 12, 8, 12));
        add(statusBanner, BorderLayout.NORTH);

        // Tabs
        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(BG_DARK);
        tabs.setForeground(FG_TEXT);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabs.addTab("Original",      buildSplitPane(origRequestArea, origResponseArea));
        tabs.addTab("Modified",      buildSplitPane(modRequestArea,  modResponseArea));
        add(tabs, BorderLayout.CENTER);

        showEmpty();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Display the request/response pair for the given TestResult. */
    public void show(TestResult result) {
        if (result == null) { showEmpty(); return; }

        // Status banner
        VulnerabilityStatus status = result.getStatus();
        statusBanner.setText(buildBannerText(result));
        statusBanner.setForeground(colorFor(status));

        // Original
        origRequestArea.setText(bytesToString(result.getOrigRequestBytes()));
        origResponseArea.setText(bytesToString(result.getOrigResponseBytes()));
        origRequestArea.setCaretPosition(0);
        origResponseArea.setCaretPosition(0);

        // Modified
        modRequestArea.setText(bytesToString(result.getModRequestBytes()));
        modResponseArea.setText(
                result.getModStatus() == -1
                        ? "(Modified response not yet received...)"
                        : bytesToString(result.getModResponseBytes()));
        modRequestArea.setCaretPosition(0);
        modResponseArea.setCaretPosition(0);

        // Highlight modified tab if result is interesting
        tabs.setForegroundAt(1, colorFor(status));
    }

    public void clear() { showEmpty(); }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void showEmpty() {
        statusBanner.setText("Select a row to inspect the request/response pair");
        statusBanner.setForeground(FG_DIM);
        origRequestArea.setText("");
        origResponseArea.setText("");
        modRequestArea.setText("");
        modResponseArea.setText("");
        tabs.setForegroundAt(1, FG_TEXT);
    }

    private String buildBannerText(TestResult r) {
        String base = "#" + r.getId() + "  " + r.getMethod() + "  " + r.getUrl();
        if (r.getModStatus() == -1) return base + "   [replaying…]";
        return String.format("%s   |   Orig: %d / %d bytes   →   Mod: %d / %d bytes   |   %s",
                base,
                r.getOrigStatus(), r.getOrigLength(),
                r.getModStatus(), r.getModLength(),
                r.getStatus());
    }

    private Color colorFor(VulnerabilityStatus s) {
        return switch (s) {
            case VULNERABLE   -> ACCENT_RED;
            case ENFORCED     -> ACCENT_GREEN;
            case INTERESTING  -> ACCENT_YELLOW;
            default           -> FG_DIM;
        };
    }

    private JSplitPane buildSplitPane(JTextArea top, JTextArea bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                labelledScroll("Request",  top),
                labelledScroll("Response", bottom));
        split.setResizeWeight(0.35);
        split.setDividerSize(5);
        split.setBackground(BG_DARK);
        split.setBorder(null);
        return split;
    }

    private JPanel labelledScroll(String label, JTextArea area) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(ACCENT_BLUE);
        lbl.setBorder(new EmptyBorder(4, 8, 4, 8));
        lbl.setOpaque(true);
        lbl.setBackground(BG_PANEL);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.add(lbl,    BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea makeTextArea() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
        ta.setBackground(BG_TEXT);
        ta.setForeground(FG_TEXT);
        ta.setCaretColor(FG_TEXT);
        ta.setLineWrap(false);
        ta.setBorder(new EmptyBorder(4, 6, 4, 6));
        return ta;
    }

    private static String bytesToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

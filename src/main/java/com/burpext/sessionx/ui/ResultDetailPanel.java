package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResult.VulnerabilityStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class ResultDetailPanel extends JPanel {

    private final JTabbedPane tabs;

    // Original tab
    private final JTextArea origRequestArea  = makeTextArea();
    private final JTextArea origResponseArea = makeTextArea();

    // Modified tab
    private final JTextArea modRequestArea   = makeTextArea();
    private final JTextArea modResponseArea  = makeTextArea();

    // Unauthenticated tab
    private final JTextArea unauthRequestArea   = makeTextArea();
    private final JTextArea unauthResponseArea  = makeTextArea();

    // Status banner
    private final JLabel statusBanner = new JLabel(" ", SwingConstants.CENTER);

    public ResultDetailPanel() {
        setLayout(new BorderLayout());

        // Status banner at top
        statusBanner.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        statusBanner.setOpaque(true);
        statusBanner.setBorder(new EmptyBorder(8, 12, 8, 12));
        add(statusBanner, BorderLayout.NORTH);

        // Tabs
        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.addTab("Original",      buildSplitPane(origRequestArea, origResponseArea));
        tabs.addTab("Modified",      buildSplitPane(modRequestArea,  modResponseArea));
        tabs.addTab("Unauthenticated", buildSplitPane(unauthRequestArea, unauthResponseArea));
        add(tabs, BorderLayout.CENTER);

        showEmpty();
    }

    public void show(TestResult result) {
        if (result == null) { showEmpty(); return; }

        statusBanner.setText(buildBannerText(result));
        
        VulnerabilityStatus modStatus = result.getModVulnStatus();
        VulnerabilityStatus unauthStatus = result.getUnauthVulnStatus();

        if (modStatus == VulnerabilityStatus.VULNERABLE || unauthStatus == VulnerabilityStatus.VULNERABLE) {
            statusBanner.setForeground(new Color(220, 53, 69)); // Minimal Red
        } else if (modStatus == VulnerabilityStatus.ENFORCED && unauthStatus == VulnerabilityStatus.ENFORCED) {
            statusBanner.setForeground(new Color(40, 167, 69)); // Minimal Green
        } else {
            statusBanner.setForeground(new Color(255, 153, 0));  // Minimal Orange
        }

        origRequestArea.setText(bytesToString(result.getOrigRequestBytes()));
        origResponseArea.setText(bytesToString(result.getOrigResponseBytes()));
        origRequestArea.setCaretPosition(0);
        origResponseArea.setCaretPosition(0);

        modRequestArea.setText(bytesToString(result.getModRequestBytes()));
        modResponseArea.setText(
                result.getModStatus() == -1
                        ? "(Modified response not yet received...)"
                        : bytesToString(result.getModResponseBytes()));
        modRequestArea.setCaretPosition(0);
        modResponseArea.setCaretPosition(0);

        unauthRequestArea.setText(bytesToString(result.getUnauthRequestBytes()));
        unauthResponseArea.setText(
                result.getUnauthStatus() == -1
                        ? "(Unauthenticated response not yet received...)"
                        : bytesToString(result.getUnauthResponseBytes()));
        unauthRequestArea.setCaretPosition(0);
        unauthResponseArea.setCaretPosition(0);
    }

    public void clear() { showEmpty(); }

    /** Pre-select a tab by index: 0=Original, 1=Modified, 2=Unauthenticated */
    public void selectTab(int index) {
        if (index >= 0 && index < tabs.getTabCount()) {
            tabs.setSelectedIndex(index);
        }
    }

    private void showEmpty() {
        statusBanner.setText("Select a row to inspect the request/response pair");
        statusBanner.setForeground(null);
        origRequestArea.setText("");
        origResponseArea.setText("");
        modRequestArea.setText("");
        modResponseArea.setText("");
        unauthRequestArea.setText("");
        unauthResponseArea.setText("");
    }

    private String buildBannerText(TestResult r) {
        String base = "#" + r.getId() + "  " + r.getMethod() + "  " + r.getUrl();
        if (r.getModStatus() == -1 || r.getUnauthStatus() == -1) return base + "   [replaying…]";
        return String.format("%s   |   Orig: %d / %dB  →  Mod: %d / %dB  →  Unauth: %d / %dB   |   %s",
                base,
                r.getOrigStatus(), r.getOrigLength(),
                r.getModStatus(), r.getModLength(),
                r.getUnauthStatus(), r.getUnauthLength(),
                r.getCombinedStatus());
    }

    private JSplitPane buildSplitPane(JTextArea top, JTextArea bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                labelledScroll("Request",  top),
                labelledScroll("Response", bottom));
        split.setResizeWeight(0.5);
        return split;
    }

    private JPanel labelledScroll(String label, JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(label));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea makeTextArea() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ta.setLineWrap(false);
        return ta;
    }

    private static String bytesToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

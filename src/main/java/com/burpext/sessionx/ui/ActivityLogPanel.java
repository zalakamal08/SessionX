package com.burpext.sessionx.ui;

import com.burpext.sessionx.util.ActivityLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;

/**
 * Always-visible activity log panel at the bottom of the SessionX tab.
 *
 * Displays timestamped, color-coded log entries from ActivityLogger.
 * Colors:
 *   [INFO]    - muted grey
 *   [TOKEN]   - green
 *   [REFRESH] - orange
 *   [ERROR]   - red
 *   [SCOPE]   - blue
 *   [WARN]    - yellow/orange
 */
public class ActivityLogPanel {

    private final JPanel       root;
    private final JTextPane    logPane;
    private final StyledDocument doc;

    public ActivityLogPanel() {
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(UiTheme.BG_DEEP);
        logPane.setFont(UiTheme.FONT_MONO);
        doc = logPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER));
        scroll.setBackground(UiTheme.BG_DEEP);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.PAD_SM, 4));
        toolbar.setBackground(UiTheme.BG_SURFACE);
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER));

        JLabel title = UiTheme.label("ACTIVITY LOG");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(UiTheme.TEXT_MUTED);
        toolbar.add(title);

        toolbar.add(Box.createHorizontalGlue());

        JButton clearBtn = UiTheme.button("Clear");
        clearBtn.setFont(UiTheme.FONT_SMALL);
        clearBtn.addActionListener(e -> clearLog());
        toolbar.add(clearBtn);

        JButton copyBtn = UiTheme.button("Copy All");
        copyBtn.setFont(UiTheme.FONT_SMALL);
        copyBtn.addActionListener(e -> copyAll());
        toolbar.add(copyBtn);

        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_DEEP);
        root.setPreferredSize(new Dimension(0, 180));
        root.add(toolbar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        // Subscribe to logger
        ActivityLogger.getInstance().addListener(this::appendEntry);
    }

    // --- Log entry rendering ---

    private void appendEntry(String entry) {
        SwingUtilities.invokeLater(() -> {
            try {
                Color color = colorFor(entry);
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, color);
                StyleConstants.setFontFamily(attrs, "Consolas");
                StyleConstants.setFontSize(attrs, 12);
                doc.insertString(doc.getLength(), entry + "\n", attrs);

                // Auto-scroll to latest
                logPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private Color colorFor(String entry) {
        if (entry.contains("[TOKEN]"))   return UiTheme.ACCENT_GREEN;
        if (entry.contains("[REFRESH]")) return UiTheme.ACCENT_ORANGE;
        if (entry.contains("[ERROR]"))   return UiTheme.ACCENT_RED;
        if (entry.contains("[SCOPE]"))   return UiTheme.ACCENT_BLUE;
        if (entry.contains("[WARN]"))    return new Color(0xD2, 0x99, 0x22);
        return UiTheme.TEXT_MUTED;
    }

    // --- Actions ---

    private void clearLog() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void copyAll() {
        try {
            String text = doc.getText(0, doc.getLength());
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (BadLocationException ignored) {}
    }

    public JPanel getPanel() { return root; }
}

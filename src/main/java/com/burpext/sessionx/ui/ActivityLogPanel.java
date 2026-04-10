package com.burpext.sessionx.ui;

import com.burpext.sessionx.util.ActivityLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Activity log strip at the bottom of the extension tab.
 *
 * Design intent:
 *   - Terminal-like, but clean — no "hacker" chrome
 *   - Monospace text, subtle colors for log levels
 *   - LOG LEVEL colors are strictly functional (green=good, red=error, etc.)
 *     but the surrounding chrome is neutral
 *   - Auto-scrolls to latest entry
 */
public class ActivityLogPanel {

    private final JPanel     root;
    private final JTextPane  logPane;
    private final StyledDocument doc;

    public ActivityLogPanel() {
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(UiTheme.BG_BASE);
        logPane.setFont(UiTheme.FONT_MONO_SM);
        logPane.setBorder(new EmptyBorder(UiTheme.SP_SM, UiTheme.SP_MD, UiTheme.SP_SM, UiTheme.SP_MD));
        doc = logPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(null);
        scroll.setBackground(UiTheme.BG_BASE);
        scroll.getViewport().setBackground(UiTheme.BG_BASE);

        // Toolbar
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiTheme.BG_PANEL);
        toolbar.setBorder(new MatteBorder(1, 0, 0, 0, UiTheme.BORDER_SUBTLE));

        JLabel title = UiTheme.sectionLabel("Activity Log");
        title.setBorder(new EmptyBorder(UiTheme.SP_SM, UiTheme.SP_MD, UiTheme.SP_SM, 0));
        toolbar.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SP_SM, 4));
        actions.setOpaque(false);

        JButton copyBtn = UiTheme.button("Copy All");
        copyBtn.addActionListener(e -> copyAll());
        actions.add(copyBtn);

        JButton clearBtn = UiTheme.button("Clear");
        clearBtn.addActionListener(e -> clearLog());
        actions.add(clearBtn);

        toolbar.add(actions, BorderLayout.EAST);

        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_BASE);
        root.setPreferredSize(new Dimension(0, 160));
        root.add(toolbar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        ActivityLogger.getInstance().addListener(this::appendEntry);
    }

    // --- Rendering ---

    private void appendEntry(String entry) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attrs, UiTheme.FONT_MONO_SM.getFamily());
                StyleConstants.setFontSize(attrs, UiTheme.FONT_MONO_SM.getSize());
                StyleConstants.setForeground(attrs, colorFor(entry));
                doc.insertString(doc.getLength(), entry + "\n", attrs);
                logPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    // Log level -> color mapping (functional, not decorative)
    private Color colorFor(String entry) {
        if (entry.contains("[TOKEN]"))   return UiTheme.STATUS_OK;
        if (entry.contains("[REFRESH]")) return UiTheme.STATUS_WARN;
        if (entry.contains("[ERROR]"))   return UiTheme.STATUS_ERR;
        if (entry.contains("[SCOPE]"))   return UiTheme.TEXT_ACCENT;
        if (entry.contains("[WARN]"))    return UiTheme.STATUS_WARN;
        return UiTheme.TEXT_SECONDARY;   // [INFO] — default
    }

    private void clearLog() {
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
    }

    private void copyAll() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(doc.getText(0, doc.getLength())), null);
        } catch (BadLocationException ignored) {}
    }

    public JPanel getPanel() { return root; }
}

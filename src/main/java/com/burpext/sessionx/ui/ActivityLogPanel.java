package com.burpext.sessionx.ui;

import com.burpext.sessionx.util.ActivityLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Activity log at the bottom -- mirrors Postman2Burp's status bar style.
 *
 * Design:
 *   - Thin top border (same as Postman2Burp status bar bottom border)
 *   - SansSerif italic footnote text style for idle state
 *   - Log area uses system default background; monospaced text
 *   - Colors: only text-level coloring for log level (green/red/amber/blue)
 *     on system-default background -- no panel coloring
 */
public class ActivityLogPanel {

    private final JPanel    root;
    private final JTextPane logPane;
    private final StyledDocument doc;

    public ActivityLogPanel() {
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(UiTheme.FONT_MONO_SM);
        logPane.setBorder(new EmptyBorder(UiTheme.SP_SM, UiTheme.SP_MD, UiTheme.SP_SM, UiTheme.SP_MD));
        doc = logPane.getStyledDocument();

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(null);

        // Toolbar -- same as Postman2Burp status bar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        JLabel title = new JLabel("Activity Log");
        title.setFont(UiTheme.FONT_BOLD);
        title.setForeground(UiTheme.getSecondaryText());
        toolbar.add(title);

        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 14));
        sep.setForeground(UiTheme.getBorderColor());
        toolbar.add(sep);

        JButton clearBtn = UiTheme.smallButton("✕ Clear", "Clear log");
        clearBtn.addActionListener(e -> clearLog());
        toolbar.add(clearBtn);

        JButton copyBtn = UiTheme.smallButton("📋 Copy All", "Copy all log entries");
        copyBtn.addActionListener(e -> copyAll());
        toolbar.add(copyBtn);

        root = new JPanel(new BorderLayout(0, 0));
        root.setPreferredSize(new Dimension(0, 150));
        root.add(toolbar, BorderLayout.NORTH);
        root.add(scroll,  BorderLayout.CENTER);

        ActivityLogger.getInstance().addListener(this::appendEntry);
    }

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

    /** Colors are strictly for log-level readability, not decoration */
    private Color colorFor(String entry) {
        if (entry.contains("[TOKEN]"))   return UiTheme.STATUS_OK;
        if (entry.contains("[REFRESH]")) return UiTheme.STATUS_WARN;
        if (entry.contains("[ERROR]"))   return UiTheme.STATUS_ERR;
        if (entry.contains("[SCOPE]"))   return UiTheme.getAccentColor();
        if (entry.contains("[WARN]"))    return UiTheme.STATUS_WARN;
        return UiTheme.getSecondaryText();
    }

    private void clearLog() {
        try { doc.remove(0, doc.getLength()); }
        catch (BadLocationException ignored) {}
    }

    private void copyAll() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(doc.getText(0, doc.getLength())), null);
        } catch (BadLocationException ignored) {}
    }

    public JPanel getPanel() { return root; }
}

package com.burpext.sessionx.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Minimalist design system for SessionX.
 *
 * Palette: Two-tone neutral dark.
 *   - One accent color (calm blue) only for interactive state
 *   - Everything else is achieved through weight, spacing, and contrast
 */
public final class UiTheme {

    // --- Palette ---
    public static final Color BG_BASE     = new Color(0x16, 0x16, 0x16);   // deepest bg
    public static final Color BG_PANEL    = new Color(0x1E, 0x1E, 0x1E);   // panel surface
    public static final Color BG_INPUT    = new Color(0x12, 0x12, 0x12);   // input fields
    public static final Color BG_ROW_ALT = new Color(0x1A, 0x1A, 0x1A);   // table alt row
    public static final Color BG_HOVER    = new Color(0x28, 0x28, 0x28);   // hover state

    public static final Color BORDER_SUBTLE = new Color(0x2C, 0x2C, 0x2C); // almost invisible
    public static final Color BORDER_NORMAL = new Color(0x38, 0x38, 0x38); // standard borders
    public static final Color BORDER_STRONG = new Color(0x4A, 0x4A, 0x4A); // emphasized

    public static final Color TEXT_PRIMARY  = new Color(0xE2, 0xE2, 0xE2); // body text
    public static final Color TEXT_SECONDARY= new Color(0x9A, 0x9A, 0x9A); // supporting text
    public static final Color TEXT_MUTED    = new Color(0x60, 0x60, 0x60); // placeholder / disabled
    public static final Color TEXT_ACCENT   = new Color(0x58, 0xA6, 0xFF); // single accent (links, active)

    public static final Color STATUS_OK   = new Color(0x57, 0xAB, 0x5A);   // active indicator
    public static final Color STATUS_OFF  = new Color(0x4A, 0x4A, 0x4A);   // inactive indicator
    public static final Color STATUS_ERR  = new Color(0xE5, 0x53, 0x4B);   // error state
    public static final Color STATUS_WARN = new Color(0xCC, 0x96, 0x26);   // warning

    // --- Typography ---
    public static final Font FONT_UI      = load("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_UI_MED  = load("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_UI_SM   = load("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BOLD    = load("Segoe UI", Font.BOLD,  13);
    public static final Font FONT_LABEL   = load("Segoe UI", Font.BOLD,  11);  // SECTION LABELS
    public static final Font FONT_MONO    = mono(12);
    public static final Font FONT_MONO_SM = mono(11);

    // --- Spacing ---
    public static final int SP_XS = 4;
    public static final int SP_SM = 8;
    public static final int SP_MD = 14;
    public static final int SP_LG = 20;
    public static final int SP_XL = 28;

    private UiTheme() {}

    // --- Font helpers ---

    private static Font load(String name, int style, int size) {
        return new Font(name, style, size);
    }

    private static Font mono(int size) {
        // Prefer JetBrains Mono -> Consolas -> Courier New
        String[] candidates = {"JetBrains Mono", "Consolas", "Courier New", Font.MONOSPACED};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, size);
            if (!f.getFamily().equals(Font.MONOSPACED)) return f;
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    // =========================================================================
    // COMPONENT FACTORIES
    // =========================================================================

    /** Standard body label */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    /** Small section-header label — ALL CAPS, no color, just weight */
    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FONT_LABEL);
        l.setForeground(TEXT_SECONDARY);
        l.setBorder(new EmptyBorder(0, 0, SP_XS, 0));
        return l;
    }

    /** Dimmed supporting text */
    public static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI_SM);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    /** Standard dark text field */
    public static JTextField textField() {
        JTextField f = new JTextField();
        f.setFont(FONT_UI);
        f.setForeground(TEXT_PRIMARY);
        f.setBackground(BG_INPUT);
        f.setCaretColor(TEXT_PRIMARY);
        f.setSelectedTextColor(TEXT_PRIMARY);
        f.setSelectionColor(new Color(0x58, 0xA6, 0xFF, 55));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_NORMAL, 1),
            new EmptyBorder(5, 8, 5, 8)));
        return f;
    }

    /** Monospace text field (for regex, URLs, JSON paths) */
    public static JTextField monoField() {
        JTextField f = textField();
        f.setFont(FONT_MONO);
        return f;
    }

    /**
     * Standard ghost button — no fill, just an outline that appears on hover.
     * This is the default button style.
     */
    public static JButton button(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isArmed()) {
                    g2.setColor(BG_HOVER.brighter());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                } else if (getModel().isRollover()) {
                    g2.setColor(BG_HOVER);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(FONT_UI_SM);
        b.setForeground(TEXT_SECONDARY);
        b.setBackground(BG_PANEL);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_NORMAL, 1),
            new EmptyBorder(5, 12, 5, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /**
     * Primary action button — filled with accent color. Use sparingly.
     */
    public static JButton primaryButton(String text) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = new Color(0x1F, 0x6F, 0xEB);
                Color hover = new Color(0x16, 0x5A, 0xD3);
                g2.setColor(getModel().isRollover() || getModel().isArmed() ? hover : base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(FONT_UI_SM);
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(0x1F, 0x6F, 0xEB));
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(5, 14, 5, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /**
     * Destructive action button — same as ghost but text is muted red on hover.
     */
    public static JButton dangerButton(String text) {
        JButton b = button(text);
        b.setForeground(STATUS_ERR);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x4A, 0x1A, 0x1A), 1),
            new EmptyBorder(5, 12, 5, 12)));
        return b;
    }

    /** Thin horizontal divider */
    public static JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER_SUBTLE);
        s.setBackground(BORDER_SUBTLE);
        return s;
    }

    /** Styled bare JPanel with the base background */
    public static JPanel panel() {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        return p;
    }

    /** JComboBox styled to match the dark theme */
    public static <T> JComboBox<T> comboBox() {
        JComboBox<T> c = new JComboBox<>();
        c.setFont(FONT_UI);
        c.setForeground(TEXT_PRIMARY);
        c.setBackground(BG_INPUT);
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? BG_HOVER : BG_INPUT);
                setForeground(TEXT_PRIMARY);
                setFont(FONT_UI);
                setBorder(new EmptyBorder(3, 8, 3, 8));
                return this;
            }
        });
        return c;
    }
}

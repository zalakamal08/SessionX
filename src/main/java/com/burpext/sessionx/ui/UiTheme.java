package com.burpext.sessionx.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Lightweight, system-native design tokens for SessionX.
 *
 * Inspired by Postman2Burp: no manual background colors, no dark theme.
 * Rely on the OS/Burp look-and-feel for surfaces; only override what is
 * strictly necessary for legibility (text on system defaults).
 *
 * Color rules:
 *   - Borders : Color(210,210,210) -- same as Postman2Burp toolbar border
 *   - Text primary   : Color(40,40,40)   -- near-black on white
 *   - Text secondary : Color(100,100,100)
 *   - Text muted     : Color(150,150,150)
 *   - Status ok      : Color(34,139,34)  -- forest green
 *   - Status err     : Color(180,40,40)  -- muted red
 *   - Status warn    : Color(180,120,0)  -- amber
 *   - Accent (links) : Color(50,100,200) -- calm blue, used for "+ New" text only
 */
public final class UiTheme {

    // --- Borders ---
    public static final Color BORDER = new Color(210, 210, 210);

    // --- Text ---
    public static final Color TEXT_PRIMARY   = new Color(40,  40,  40);
    public static final Color TEXT_SECONDARY = new Color(100, 100, 100);
    public static final Color TEXT_MUTED     = new Color(150, 150, 150);
    public static final Color TEXT_ACCENT    = new Color(50,  100, 200);

    // --- Status indicators (functional, never decorative) ---
    public static final Color STATUS_OK   = new Color(34,  139, 34);
    public static final Color STATUS_OFF  = new Color(180, 180, 180);
    public static final Color STATUS_ERR  = new Color(180, 40,  40);
    public static final Color STATUS_WARN = new Color(180, 120, 0);

    // --- Typography -- matches Postman2Burp exactly ---
    public static final Font FONT_BRAND  = new Font("SansSerif", Font.BOLD,  13);
    public static final Font FONT_UI     = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_UI_SM  = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font FONT_BOLD   = new Font("SansSerif", Font.BOLD,  12);
    public static final Font FONT_LABEL  = new Font("SansSerif", Font.BOLD,  11);  // section caps
    public static final Font FONT_ITALIC = new Font("SansSerif", Font.ITALIC,11);
    public static final Font FONT_MONO   = new Font("Monospaced", Font.PLAIN, 12);
    public static final Font FONT_MONO_SM= new Font("Monospaced", Font.PLAIN, 11);

    // --- Spacing ---
    public static final int SP_XS = 3;
    public static final int SP_SM = 6;
    public static final int SP_MD = 10;
    public static final int SP_LG = 16;

    private UiTheme() {}

    // =========================================================================
    // COMPONENT FACTORIES  (stock Swing, minimal overrides)
    // =========================================================================

    /** Standard body label */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    /** Small ALL-CAPS section divider label */
    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FONT_LABEL);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    /** Muted hint / placeholder text */
    public static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI_SM);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    /**
     * Standard text field — let the system L&F control the background.
     * Only override font and a simple lineBorder to match Postman2Burp.
     */
    public static JTextField textField() {
        JTextField f = new JTextField();
        f.setFont(FONT_UI);
        f.setForeground(TEXT_PRIMARY);
        return f;
    }

    /** Monospace field (regex, URL, key paths) */
    public static JTextField monoField() {
        JTextField f = textField();
        f.setFont(FONT_MONO);
        return f;
    }

    /**
     * Standard toolbar button — same style as Postman2Burp's toolbarButton().
     * No fill override; let the L&F paint the button normally.
     */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_UI);
        b.setFocusPainted(false);
        return b;
    }

    /** Small utility button (tree toolbar style) */
    public static JButton smallButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(FONT_UI_SM);
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 5, 1, 5));
        return b;
    }

    /** Thin horizontal line divider */
    public static JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER);
        return s;
    }

    /** Combo box — system default, just font override */
    public static <T> JComboBox<T> comboBox() {
        JComboBox<T> c = new JComboBox<>();
        c.setFont(FONT_UI);
        return c;
    }
}

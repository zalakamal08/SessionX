package com.burpext.sessionx.ui;

import java.awt.*;

/**
 * Centralized design system for SessionX's dark, professional UI.
 *
 * All colors, fonts, and spacing constants live here.
 * No magic numbers scattered across UI classes.
 */
public final class UiTheme {

    // ─── Color Palette ────────────────────────────────────────────────────────
    public static final Color BG_DEEP      = new Color(0x0D, 0x11, 0x17); // darkest background
    public static final Color BG_SURFACE   = new Color(0x16, 0x1B, 0x22); // panel background
    public static final Color BG_ELEVATED  = new Color(0x21, 0x26, 0x2D); // row hover / input fields
    public static final Color BORDER       = new Color(0x30, 0x36, 0x3D);
    public static final Color TEXT_PRIMARY = new Color(0xE6, 0xED, 0xF3);
    public static final Color TEXT_MUTED   = new Color(0x8B, 0x94, 0x9E);
    public static final Color ACCENT_GREEN = new Color(0x3F, 0xB9, 0x50); // active / healthy / tokens
    public static final Color ACCENT_BLUE  = new Color(0x58, 0xA6, 0xFF); // scope entries
    public static final Color ACCENT_ORANGE= new Color(0xD2, 0x99, 0x22); // refresh / warning
    public static final Color ACCENT_RED   = new Color(0xF8, 0x51, 0x49); // error / disabled

    // ─── Fonts ────────────────────────────────────────────────────────────────
    public static final Font FONT_NORMAL    = new Font("Segoe UI",   Font.PLAIN,  13);
    public static final Font FONT_BOLD      = new Font("Segoe UI",   Font.BOLD,   13);
    public static final Font FONT_SMALL     = new Font("Segoe UI",   Font.PLAIN,  11);
    public static final Font FONT_MONO      = new Font("Consolas",   Font.PLAIN,  12);
    public static final Font FONT_HEADING   = new Font("Segoe UI",   Font.BOLD,   15);

    // ─── Spacing ──────────────────────────────────────────────────────────────
    public static final int PAD_SM = 6;
    public static final int PAD_MD = 10;
    public static final int PAD_LG = 16;

    private UiTheme() {}

    // ─── Factory Helpers ──────────────────────────────────────────────────────

    /** Creates a standard dark-themed panel. */
    public static javax.swing.JPanel darkPanel() {
        javax.swing.JPanel p = new javax.swing.JPanel();
        p.setBackground(BG_SURFACE);
        return p;
    }

    /** Creates a styled label. */
    public static javax.swing.JLabel label(String text) {
        javax.swing.JLabel l = new javax.swing.JLabel(text);
        l.setFont(FONT_NORMAL);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    /** Creates a muted label (for hints/descriptions). */
    public static javax.swing.JLabel mutedLabel(String text) {
        javax.swing.JLabel l = new javax.swing.JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(TEXT_MUTED);
        return l;
    }

    /** Creates a styled button. */
    public static javax.swing.JButton button(String text) {
        javax.swing.JButton b = new javax.swing.JButton(text);
        b.setFont(FONT_NORMAL);
        b.setForeground(TEXT_PRIMARY);
        b.setBackground(BG_ELEVATED);
        b.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(BORDER, 1),
            javax.swing.BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Creates a primary action button (accent color). */
    public static javax.swing.JButton primaryButton(String text) {
        javax.swing.JButton b = button(text);
        b.setBackground(new Color(0x23, 0x8A, 0x39));  // darker green for bg
        b.setForeground(Color.WHITE);
        return b;
    }

    /** Creates a danger button (red). */
    public static javax.swing.JButton dangerButton(String text) {
        javax.swing.JButton b = button(text);
        b.setBackground(new Color(0x7D, 0x27, 0x22));
        b.setForeground(Color.WHITE);
        return b;
    }

    /** Creates a styled text field. */
    public static javax.swing.JTextField textField() {
        javax.swing.JTextField f = new javax.swing.JTextField();
        f.setFont(FONT_NORMAL);
        f.setForeground(TEXT_PRIMARY);
        f.setBackground(BG_ELEVATED);
        f.setCaretColor(TEXT_PRIMARY);
        f.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(BORDER, 1),
            javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return f;
    }

    /** Creates a monospace text field (for regex, URLs). */
    public static javax.swing.JTextField monoField() {
        javax.swing.JTextField f = textField();
        f.setFont(FONT_MONO);
        return f;
    }

    /** Creates a styled combo box. */
    public static <T> javax.swing.JComboBox<T> comboBox() {
        javax.swing.JComboBox<T> c = new javax.swing.JComboBox<>();
        c.setFont(FONT_NORMAL);
        c.setForeground(TEXT_PRIMARY);
        c.setBackground(BG_ELEVATED);
        return c;
    }

    /** Horizontal separator. */
    public static javax.swing.JSeparator separator() {
        javax.swing.JSeparator s = new javax.swing.JSeparator();
        s.setForeground(BORDER);
        s.setBackground(BORDER);
        return s;
    }
}

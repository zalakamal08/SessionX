package com.burpext.sessionx.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Lightweight, dynamically theme-aware design tokens for SessionX.
 *
 * Ensures Burp Suite's Dark Mode (Darcula) and Light Mode are respected
 * automatically by querying UIManager for system colors.
 */
public final class UiTheme {

    // --- Status indicators (functional, never decorative) ---
    // These remain hardcoded as they represent distinct states independent of theme.
    public static final Color STATUS_OK   = new Color(60, 160, 60);  // Green
    public static final Color STATUS_OFF  = new Color(130, 130, 130); // Gray
    public static final Color STATUS_ERR  = new Color(200, 60,  60);  // Red
    public static final Color STATUS_WARN = new Color(200, 140, 0);   // Amber

    // --- Typography ---
    public static final Font FONT_BRAND  = new Font("SansSerif", Font.BOLD,  13);
    public static final Font FONT_UI     = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_UI_SM  = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font FONT_BOLD   = new Font("SansSerif", Font.BOLD,  12);
    public static final Font FONT_LABEL  = new Font("SansSerif", Font.BOLD,  11);
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
    // DYNAMIC COLOR RESOLUTION
    // =========================================================================

    public static Color getPrimaryText() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : new Color(40, 40, 40);
    }

    public static Color getSecondaryText() {
        Color c = UIManager.getColor("textInactiveText");
        if (c != null) return c;
        // Fallback: blend primary with background
        Color fg = getPrimaryText();
        Color bg = getBackground();
        return blend(fg, bg, 0.6f);
    }

    public static Color getMutedText() {
        Color fg = getPrimaryText();
        Color bg = getBackground();
        return blend(fg, bg, 0.4f);
    }

    public static Color getAccentColor() {
        Color c = UIManager.getColor("Component.focusColor");
        if (c != null) return c;
        c = UIManager.getColor("textHighlight");
        if (c != null) return c;
        return new Color(0, 120, 215); // Default blue
    }

    public static Color getBorderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c != null) return c;
        c = UIManager.getColor("Separator.foreground");
        if (c != null) return c;
        // Fallback: slightly contrasted background
        Color bg = getBackground();
        return isDarkTheme() ? bg.brighter() : bg.darker();
    }

    public static Color getBackground() {
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : new Color(240, 240, 240);
    }

    public static boolean isDarkTheme() {
        Color bg = getBackground();
        // Standard perceived luminance formula
        double luminance = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return luminance < 0.5;
    }

    private static Color blend(Color c1, Color c2, float ratio) {
        float r = (c1.getRed() * ratio) + (c2.getRed() * (1 - ratio));
        float g = (c1.getGreen() * ratio) + (c2.getGreen() * (1 - ratio));
        float b = (c1.getBlue() * ratio) + (c2.getBlue() * (1 - ratio));
        return new Color((int)r, (int)g, (int)b);
    }

    // =========================================================================
    // COMPONENT FACTORIES (Theme Aware)
    // =========================================================================

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI);
        l.setForeground(getPrimaryText());
        return l;
    }

    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FONT_LABEL);
        l.setForeground(getSecondaryText());
        return l;
    }

    public static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_UI_SM);
        l.setForeground(getMutedText());
        return l;
    }

    public static JTextField textField() {
        JTextField f = new JTextField();
        f.setFont(FONT_UI);
        return f;
    }

    public static JTextField monoField() {
        JTextField f = textField();
        f.setFont(FONT_MONO);
        return f;
    }

    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_UI);
        b.setFocusPainted(false);
        return b;
    }

    public static JButton smallButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFont(FONT_UI_SM);
        b.setFocusPainted(false);
        b.setMargin(new Insets(1, 4, 1, 4));
        return b;
    }

    public static JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(getBorderColor());
        return s;
    }

    public static <T> JComboBox<T> comboBox() {
        JComboBox<T> c = new JComboBox<>();
        c.setFont(FONT_UI);
        return c;
    }
}

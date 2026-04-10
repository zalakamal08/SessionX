package com.burpext.sessionx.ui;

import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.util.ActivityLogger;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Root panel registered as the "SessionX" Burp Suite tab.
 *
 * Layout:
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  Header bar: brand + version                            │
 *  ├────────────────────┬────────────────────────────────────┤
 *  │  ProfileListPanel  │  ProfileEditorPanel                │
 *  │  (left sidebar)    │  (main area)                       │
 *  ├────────────────────┴────────────────────────────────────┤
 *  │  ActivityLogPanel (always visible at bottom)            │
 *  └─────────────────────────────────────────────────────────┘
 */
public class SessionXTab {

    private final JPanel root;

    public SessionXTab(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_DEEP);

        // ─── Activity log (shared across all panels) ──────────────────────────
        ActivityLogPanel logPanel = new ActivityLogPanel();

        // ─── Profile editor (right side) ─────────────────────────────────────
        ProfileEditorPanel editorPanel = new ProfileEditorPanel(api, profileManager, tokenStore);

        // ─── Profile list (left sidebar) ─────────────────────────────────────
        ProfileListPanel listPanel = new ProfileListPanel(profileManager, editorPanel);

        // ─── Header ──────────────────────────────────────────────────────────
        root.add(buildHeader(), BorderLayout.NORTH);

        // ─── Main split: list | editor ────────────────────────────────────────
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            listPanel.getPanel(), editorPanel.getPanel());
        mainSplit.setDividerLocation(230);
        mainSplit.setDividerSize(3);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setBorder(null);
        mainSplit.setBackground(UiTheme.BG_DEEP);

        // ─── Vertical split: main area | activity log ─────────────────────────
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            mainSplit, logPanel.getPanel());
        vertSplit.setResizeWeight(0.72);
        vertSplit.setDividerSize(3);
        vertSplit.setBorder(null);
        vertSplit.setBackground(UiTheme.BG_DEEP);

        root.add(vertSplit, BorderLayout.CENTER);

        // Log startup message
        ActivityLogger.getInstance().info("SessionX initialized — "
            + profileManager.getAllProfiles().size() + " profile(s) loaded");
    }

    // ─── Header bar ───────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.PAD_LG, 8));
        header.setBackground(UiTheme.BG_SURFACE);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));

        // Brand mark
        JLabel brand = new JLabel("⚡ SessionX");
        brand.setFont(UiTheme.FONT_HEADING);
        brand.setForeground(UiTheme.ACCENT_GREEN);
        header.add(brand);

        JLabel version = new JLabel("v1.0 — Unified Session Handler for Burp Suite");
        version.setFont(UiTheme.FONT_SMALL);
        version.setForeground(UiTheme.TEXT_MUTED);
        header.add(version);

        return header;
    }

    public JPanel getPanel() { return root; }
}

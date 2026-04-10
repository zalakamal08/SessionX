package com.burpext.sessionx.ui;

import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.util.ActivityLogger;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Root panel for the SessionX Burp Suite tab.
 *
 * Layout:
 *   +------------------------------------------+
 *   | [Header bar — brand + status]             |
 *   +-----------+------------------------------+
 *   | [Profile  | [Profile Editor — tabbed]    |
 *   |  Sidebar] |                              |
 *   |           |                              |
 *   +-----------+------------------------------+
 *   | [Activity Log — collapsible bottom strip] |
 *   +------------------------------------------+
 */
public class SessionXTab {

    private final JPanel root;

    public SessionXTab(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_BASE);

        ActivityLogPanel logPanel    = new ActivityLogPanel();
        ProfileEditorPanel editorPanel = new ProfileEditorPanel(api, profileManager, tokenStore);
        ProfileListPanel   listPanel   = new ProfileListPanel(profileManager, editorPanel);

        // Header
        root.add(buildHeader(profileManager), BorderLayout.NORTH);

        // Main horizontal split
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            listPanel.getPanel(), editorPanel.getPanel());
        mainSplit.setDividerLocation(220);
        mainSplit.setDividerSize(1);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setBorder(null);
        mainSplit.setBackground(UiTheme.BG_BASE);

        // Vertical split: editor above, log below
        JSplitPane vertSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            mainSplit, logPanel.getPanel());
        vertSplit.setResizeWeight(0.70);
        vertSplit.setDividerSize(1);
        vertSplit.setBorder(null);
        vertSplit.setBackground(UiTheme.BG_BASE);

        root.add(vertSplit, BorderLayout.CENTER);

        ActivityLogger.getInstance().info("SessionX ready  -  "
            + profileManager.getAllProfiles().size() + " profile(s) loaded");
    }

    private JPanel buildHeader(ProfileManager profileManager) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UiTheme.BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER_SUBTLE));
        bar.setPreferredSize(new Dimension(0, 42));

        // Left: brand
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.SP_LG, 0));
        left.setOpaque(false);

        JLabel brand = new JLabel("SessionX");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 15));
        brand.setForeground(UiTheme.TEXT_PRIMARY);
        left.add(brand);

        JLabel sep = new JLabel("|");
        sep.setFont(UiTheme.FONT_UI_MED);
        sep.setForeground(UiTheme.BORDER_STRONG);
        left.add(sep);

        JLabel subtitle = new JLabel("Session Token Manager  for Burp Suite");
        subtitle.setFont(UiTheme.FONT_UI_SM);
        subtitle.setForeground(UiTheme.TEXT_MUTED);
        left.add(subtitle);

        // Right: version badge
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SP_LG, 0));
        right.setOpaque(false);

        JLabel version = new JLabel("v1.0");
        version.setFont(UiTheme.FONT_UI_SM);
        version.setForeground(UiTheme.TEXT_MUTED);
        right.add(version);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        // Vertically center children
        bar.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int h = bar.getHeight();
                ((FlowLayout) left.getLayout()).setVgap((h - 22) / 2);
                ((FlowLayout) right.getLayout()).setVgap((h - 22) / 2);
                left.revalidate();
                right.revalidate();
            }
        });

        return bar;
    }

    public JPanel getPanel() { return root; }
}

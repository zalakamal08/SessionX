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
 * Matches Postman2Burp layout pattern:
 *   - Toolbar at NORTH with brand label + action buttons
 *   - Horizontal split: profile sidebar (left) + editor (right)
 *   - Activity log strip (status bar) at SOUTH
 */
public class SessionXTab {

    private final JPanel root;

    public SessionXTab(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        root = new JPanel(new BorderLayout(0, 0));

        ActivityLogPanel   logPanel    = new ActivityLogPanel();
        ProfileEditorPanel editorPanel = new ProfileEditorPanel(api, profileManager, tokenStore);
        ProfileListPanel   listPanel   = new ProfileListPanel(profileManager, editorPanel);

        root.add(buildToolbar(profileManager), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            listPanel.getPanel(), editorPanel.getPanel());
        split.setDividerLocation(230);
        split.setDividerSize(4);
        split.setResizeWeight(0.25);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        root.add(logPanel.getPanel(), BorderLayout.SOUTH);

        ActivityLogger.getInstance().info("SessionX ready  -  "
            + profileManager.getAllProfiles().size() + " profile(s) loaded");
    }

    // --- Toolbar (mirrors Postman2Burp.buildToolbar() style) ---

    private JPanel buildToolbar(ProfileManager profileManager) {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.getBorderColor()));

        // Brand
        JLabel brand = new JLabel("SessionX");
        brand.setFont(UiTheme.FONT_BRAND);
        brand.setForeground(UiTheme.getPrimaryText());
        toolbar.add(brand);

        // Vertical separator
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(UiTheme.getBorderColor());
        toolbar.add(sep);

        // Subtitle
        JLabel subtitle = new JLabel("Session Token Manager");
        subtitle.setFont(UiTheme.FONT_ITALIC);
        subtitle.setForeground(UiTheme.getMutedText());
        toolbar.add(subtitle);

        return toolbar;
    }

    public JPanel getPanel() { return root; }
}

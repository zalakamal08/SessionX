package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.SessionProfile;
import com.burpext.sessionx.engine.ProfileManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Left sidebar showing the list of session profiles.
 *
 * Each profile is shown as a card with status LED, name, and target host.
 * Clicking a card loads it in ProfileEditorPanel.
 * "New Profile" button at the bottom creates a blank profile.
 */
public class ProfileListPanel {

    private final JPanel          root;
    private final JPanel          listContainer;
    private final ProfileManager  profileManager;
    private final ProfileEditorPanel editorPanel;

    public ProfileListPanel(ProfileManager profileManager, ProfileEditorPanel editorPanel) {
        this.profileManager = profileManager;
        this.editorPanel    = editorPanel;

        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_SURFACE);
        root.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.BORDER));
        root.setMinimumSize(new Dimension(190, 0));
        root.setPreferredSize(new Dimension(220, 0));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.BG_SURFACE);
        header.setBorder(new EmptyBorder(UiTheme.PAD_MD, UiTheme.PAD_MD, UiTheme.PAD_SM, UiTheme.PAD_MD));

        JLabel title = UiTheme.label("PROFILES");
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(UiTheme.TEXT_MUTED);
        header.add(title, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);

        // Scrollable profile list
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(UiTheme.BG_SURFACE);

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setBorder(null);
        scroll.setBackground(UiTheme.BG_SURFACE);
        scroll.getViewport().setBackground(UiTheme.BG_SURFACE);
        root.add(scroll, BorderLayout.CENTER);

        // Footer: New Profile button
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UiTheme.BG_SURFACE);
        footer.setBorder(new EmptyBorder(UiTheme.PAD_SM, UiTheme.PAD_SM, UiTheme.PAD_SM, UiTheme.PAD_SM));

        JButton newBtn = UiTheme.primaryButton("+ New Profile");
        newBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        newBtn.addActionListener(e -> createNewProfile());
        footer.add(newBtn, BorderLayout.CENTER);

        root.add(footer, BorderLayout.SOUTH);

        refresh();
    }

    // --- Refresh the profile list ---

    public void refresh() {
        listContainer.removeAll();
        List<SessionProfile> profiles = profileManager.getAllProfiles();

        if (profiles.isEmpty()) {
            JLabel empty = UiTheme.mutedLabel("No profiles yet.");
            empty.setBorder(new EmptyBorder(16, 14, 0, 0));
            listContainer.add(empty);
        } else {
            for (SessionProfile p : profiles) {
                listContainer.add(buildProfileCard(p));
            }
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    // --- Profile card ---

    private JPanel buildProfileCard(SessionProfile profile) {
        JPanel card = new JPanel(new BorderLayout(UiTheme.PAD_SM, 0));
        card.setBackground(UiTheme.BG_SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
            new EmptyBorder(10, 12, 10, 12)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Status LED (circle indicator)
        JLabel led = new JLabel(profile.isEnabled() ? "( * )" : "(   )");
        led.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        led.setForeground(profile.isEnabled() ? UiTheme.ACCENT_GREEN : UiTheme.TEXT_MUTED);
        card.add(led, BorderLayout.WEST);

        // Name + host
        JPanel textBlock = new JPanel();
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        textBlock.setBackground(UiTheme.BG_SURFACE);

        JLabel nameLabel = UiTheme.label(profile.getName());
        nameLabel.setFont(UiTheme.FONT_BOLD);

        JLabel hostLabel = UiTheme.mutedLabel(
            profile.getTargetHost().isEmpty() ? "No host set" : profile.getTargetHost());

        textBlock.add(nameLabel);
        textBlock.add(hostLabel);
        card.add(textBlock, BorderLayout.CENTER);

        // Click handler - load in editor
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                highlightCard(card);
                editorPanel.loadProfile(profile);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                if (!card.getBackground().equals(UiTheme.BG_ELEVATED)) {
                    card.setBackground(new Color(0x1C, 0x22, 0x2A));
                    textBlock.setBackground(card.getBackground());
                }
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (!card.getBackground().equals(UiTheme.BG_ELEVATED)) {
                    card.setBackground(UiTheme.BG_SURFACE);
                    textBlock.setBackground(UiTheme.BG_SURFACE);
                }
            }
        });

        return card;
    }

    private void highlightCard(JPanel selected) {
        for (Component c : listContainer.getComponents()) {
            if (c instanceof JPanel p) {
                p.setBackground(UiTheme.BG_SURFACE);
                for (Component child : p.getComponents()) {
                    if (child instanceof JPanel) child.setBackground(UiTheme.BG_SURFACE);
                }
            }
        }
        selected.setBackground(UiTheme.BG_ELEVATED);
        for (Component c : selected.getComponents()) {
            if (c instanceof JPanel) c.setBackground(UiTheme.BG_ELEVATED);
        }
    }

    // --- Actions ---

    private void createNewProfile() {
        SessionProfile profile = new SessionProfile();
        profile.setName("New Profile");
        profileManager.addProfile(profile);
        refresh();
        editorPanel.loadProfile(profile);
    }

    public JPanel getPanel() { return root; }
}

package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.SessionProfile;
import com.burpext.sessionx.engine.ProfileManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Left sidebar: ordered list of session profiles.
 *
 * Design intent:
 *   - Each entry is a single row: [status dot] [name]  [host]
 *   - Selection is indicated by a left-edge highlight bar, not background fill
 *   - "New Profile" sits pinned at the bottom, visually separated
 *   - No icons, no badges, no decorative elements
 */
public class ProfileListPanel {

    private final JPanel          root;
    private final JPanel          listContainer;
    private final ProfileManager  profileManager;
    private final ProfileEditorPanel editorPanel;

    private JPanel selectedCard = null;

    public ProfileListPanel(ProfileManager profileManager, ProfileEditorPanel editorPanel) {
        this.profileManager = profileManager;
        this.editorPanel    = editorPanel;

        root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.BG_PANEL);
        root.setBorder(new MatteBorder(0, 0, 0, 1, UiTheme.BORDER_SUBTLE));
        root.setMinimumSize(new Dimension(180, 0));
        root.setPreferredSize(new Dimension(220, 0));

        // Column header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.BG_PANEL);
        header.setBorder(new EmptyBorder(UiTheme.SP_LG, UiTheme.SP_MD, UiTheme.SP_SM, UiTheme.SP_MD));
        header.add(UiTheme.sectionLabel("Profiles"), BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        // Profile entries (scrollable)
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(UiTheme.BG_PANEL);

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setBorder(null);
        scroll.setBackground(UiTheme.BG_PANEL);
        scroll.getViewport().setBackground(UiTheme.BG_PANEL);
        scroll.getVerticalScrollBar().setBackground(UiTheme.BG_PANEL);
        root.add(scroll, BorderLayout.CENTER);

        // Footer: new profile button
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UiTheme.BG_PANEL);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, UiTheme.BORDER_SUBTLE));

        JButton newBtn = new JButton("+ New Profile") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                if (getModel().isRollover()) {
                    g2.setColor(UiTheme.BG_HOVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        newBtn.setFont(UiTheme.FONT_UI_SM);
        newBtn.setForeground(UiTheme.TEXT_ACCENT);
        newBtn.setBackground(UiTheme.BG_PANEL);
        newBtn.setOpaque(false);
        newBtn.setContentAreaFilled(false);
        newBtn.setBorderPainted(false);
        newBtn.setFocusPainted(false);
        newBtn.setHorizontalAlignment(SwingConstants.LEFT);
        newBtn.setBorder(new EmptyBorder(UiTheme.SP_MD, UiTheme.SP_MD, UiTheme.SP_MD, UiTheme.SP_MD));
        newBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newBtn.addActionListener(e -> createNewProfile());
        footer.add(newBtn, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        refresh();
    }

    // --- Refresh the list ---

    public void refresh() {
        int scrollPos = 0;
        listContainer.removeAll();

        List<SessionProfile> profiles = profileManager.getAllProfiles();
        if (profiles.isEmpty()) {
            JLabel empty = UiTheme.mutedLabel("No profiles yet.");
            empty.setBorder(new EmptyBorder(UiTheme.SP_MD, UiTheme.SP_MD, 0, 0));
            listContainer.add(empty);
        } else {
            for (SessionProfile p : profiles) {
                listContainer.add(buildRow(p));
            }
        }

        listContainer.revalidate();
        listContainer.repaint();
    }

    // --- Profile row ---

    private JPanel buildRow(SessionProfile profile) {
        // Container: left-accent-bar + content
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBackground(UiTheme.BG_PANEL);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        wrapper.setOpaque(true);
        wrapper.setBorder(new MatteBorder(0, 0, 1, 0, UiTheme.BORDER_SUBTLE));

        // Left-edge accent bar (visible only when selected)
        JPanel accentBar = new JPanel();
        accentBar.setPreferredSize(new Dimension(3, 0));
        accentBar.setBackground(UiTheme.BG_PANEL); // hidden by default
        wrapper.add(accentBar, BorderLayout.WEST);

        // Status dot
        JLabel dot = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(profile.isEnabled() ? UiTheme.STATUS_OK : UiTheme.STATUS_OFF);
                g2.fillOval(0, (getHeight() - 7) / 2, 7, 7);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(7, 7));
        dot.setOpaque(false);

        // Name label
        JLabel nameLabel = new JLabel(profile.getName());
        nameLabel.setFont(UiTheme.FONT_UI);
        nameLabel.setForeground(UiTheme.TEXT_PRIMARY);

        // Host sublabel
        JLabel hostLabel = new JLabel(
            profile.getTargetHost().isEmpty() ? "no host set" : profile.getTargetHost());
        hostLabel.setFont(UiTheme.FONT_UI_SM);
        hostLabel.setForeground(UiTheme.TEXT_MUTED);

        // Text block
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.setBorder(new EmptyBorder(0, UiTheme.SP_SM, 0, 0));
        text.add(nameLabel);
        text.add(hostLabel);

        // Content area: dot + text
        JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.SP_SM, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(0, UiTheme.SP_SM, 0, UiTheme.SP_SM));
        content.add(dot);
        content.add(text);

        wrapper.add(content, BorderLayout.CENTER);

        // Mouse: hover + click
        wrapper.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectCard(wrapper, accentBar);
                editorPanel.loadProfile(profile);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (wrapper != selectedCard) {
                    wrapper.setBackground(UiTheme.BG_HOVER);
                    content.setBackground(UiTheme.BG_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (wrapper != selectedCard) {
                    wrapper.setBackground(UiTheme.BG_PANEL);
                    content.setBackground(UiTheme.BG_PANEL);
                }
            }
        });

        return wrapper;
    }

    private void selectCard(JPanel card, JPanel accentBar) {
        // Deselect current
        if (selectedCard != null) {
            selectedCard.setBackground(UiTheme.BG_PANEL);
            // Reset accent bar of old selection
            Component westComp = ((BorderLayout) selectedCard.getLayout()).getLayoutComponent(BorderLayout.WEST);
            if (westComp != null) westComp.setBackground(UiTheme.BG_PANEL);
        }
        // Select new
        selectedCard = card;
        card.setBackground(UiTheme.BG_HOVER);
        accentBar.setBackground(UiTheme.TEXT_ACCENT);
    }

    private void createNewProfile() {
        SessionProfile profile = new SessionProfile();
        profile.setName("New Profile");
        profileManager.addProfile(profile);
        refresh();
        editorPanel.loadProfile(profile);
    }

    public JPanel getPanel() { return root; }
}

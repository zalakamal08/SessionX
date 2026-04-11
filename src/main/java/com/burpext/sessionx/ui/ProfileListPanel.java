package com.burpext.sessionx.ui;

import com.burpext.sessionx.core.SessionProfile;
import com.burpext.sessionx.engine.ProfileManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Left sidebar: profile list matching the Postman2Burp CollectionTreePanel style.
 *
 * Design:
 *   - Stock JList-style rows with thin bottom border
 *   - Selected row uses default system selection background
 *   - Small green/gray dot = enabled/disabled status indicator
 *   - "New Profile" button at the bottom (same style as Postman2Burp smallButton)
 */
public class ProfileListPanel {

    private final JPanel           root;
    private final JPanel           listContainer;
    private final ProfileManager   profileManager;
    private final ProfileEditorPanel editorPanel;

    private JPanel selectedCard = null;

    public ProfileListPanel(ProfileManager profileManager, ProfileEditorPanel editorPanel) {
        this.profileManager = profileManager;
        this.editorPanel    = editorPanel;

        root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.getBorderColor()));

        // --- Header/toolbar (mirrors Postman2Burp CollectionTreePanel toolbar) ---
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        header.setBorder(new EmptyBorder(2, 4, 2, 4));

        JLabel title = UiTheme.sectionLabel("Profiles");
        header.add(title);
        root.add(header, BorderLayout.NORTH);

        // --- Profile entries ---
        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setBorder(null);
        root.add(scroll, BorderLayout.CENTER);

        // --- Footer: "New Profile" ---
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.getBorderColor()));

        JButton newBtn = UiTheme.smallButton("➕ New Profile", "Create a new session profile");
        newBtn.setFont(UiTheme.FONT_UI_SM);
        newBtn.setForeground(UiTheme.TEXT_ACCENT);
        newBtn.setFocusPainted(false);
        newBtn.setBorderPainted(false);
        newBtn.setContentAreaFilled(false);
        newBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newBtn.addActionListener(e -> createNewProfile());
        footer.add(newBtn);
        root.add(footer, BorderLayout.SOUTH);

        refresh();
    }

    public void refresh() {
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

    private JPanel buildRow(SessionProfile profile) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        wrapper.setPreferredSize(new Dimension(Integer.MAX_VALUE, 50));
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.getBorderColor()));

        // Content: uses GridBagLayout for proper vertical centering
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(0, UiTheme.SP_MD, 0, UiTheme.SP_SM));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        // Dot indicator
        gbc.gridx = 0;
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, UiTheme.SP_SM);
        gbc.weightx = 0;
        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(profile.isEnabled() ? UiTheme.STATUS_OK : UiTheme.STATUS_OFF);
                int d = 7;
                g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(10, 10));
        dot.setOpaque(false);
        content.add(dot, gbc);

        // Profile name
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.anchor = GridBagConstraints.SOUTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 1, 0);
        JLabel nameLabel = new JLabel(profile.getName());
        nameLabel.setFont(UiTheme.FONT_BOLD);
        nameLabel.setForeground(UiTheme.getPrimaryText());
        content.add(nameLabel, gbc);

        // Host sub-label
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(1, 0, 0, 0);
        String hostText = profile.getTargetHost() == null || profile.getTargetHost().isEmpty()
            ? "no host set" : profile.getTargetHost();
        JLabel hostLabel = new JLabel(hostText);
        hostLabel.setFont(UiTheme.FONT_UI_SM);
        hostLabel.setForeground(UiTheme.getMutedText());
        content.add(hostLabel, gbc);

        wrapper.add(content, BorderLayout.CENTER);

        // Mouse: selection highlight uses system default selection color
        Color defaultBg   = content.getBackground();
        Color selectionBg = UIManager.getColor("List.selectionBackground");
        Color selectionFg = UIManager.getColor("List.selectionForeground");

        wrapper.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selectCard(wrapper, content, nameLabel, hostLabel,
                    selectionBg, selectionFg, defaultBg);
                editorPanel.loadProfile(profile);
            }
            @Override public void mouseEntered(MouseEvent e) {
                if (wrapper != selectedCard) {
                    Color hover = UIManager.getColor("List.dropCellBackground");
                    if (hover == null) hover = new Color(220, 230, 240);
                    content.setBackground(hover);
                    wrapper.setBackground(hover);
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (wrapper != selectedCard) {
                    content.setBackground(defaultBg);
                    wrapper.setBackground(defaultBg);
                }
            }
        });

        return wrapper;
    }

    private void selectCard(JPanel card, JPanel content, JLabel name, JLabel host,
                            Color selBg, Color selFg, Color defaultBg) {
        // Reset previous
        if (selectedCard != null) {
            selectedCard.setBackground(defaultBg);
            Component center = ((BorderLayout) selectedCard.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
            if (center != null) center.setBackground(defaultBg);
        }
        // Apply new
        selectedCard = card;
        card.setBackground(selBg != null ? selBg : new Color(184, 207, 229));
        content.setBackground(card.getBackground());
        if (selFg != null) {
            name.setForeground(selFg);
            host.setForeground(selFg);
        }
    }

    private void createNewProfile() {
        SessionProfile p = new SessionProfile();
        p.setName("New Profile");
        profileManager.addProfile(p);
        refresh();
        editorPanel.loadProfile(p);
    }

    public JPanel getPanel() { return root; }
}

package com.burpext.sessionx.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.burpext.sessionx.core.*;
import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.TokenStore;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers a "Send to SessionX" right-click menu item in Burp's Proxy History,
 * Repeater, and any other tool that shows HTTP messages.
 *
 * When the user right-clicks a request and selects "Send to SessionX → Add as Login Step",
 * it opens the LoginStepBuilderDialog pre-populated with that request, allowing
 * interactive extraction of response values.
 */
public class SessionXContextMenu implements ContextMenuItemsProvider {

    private final MontoyaApi     api;
    private final ProfileManager profileManager;
    private final TokenStore     tokenStore;

    public SessionXContextMenu(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        this.api            = api;
        this.profileManager = profileManager;
        this.tokenStore     = tokenStore;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> items = new ArrayList<>();

        // Only show in tools that have an HTTP request
        if (!event.isFromTool(ToolType.PROXY, ToolType.REPEATER,
                              ToolType.INTRUDER, ToolType.SCANNER,
                              ToolType.TARGET, ToolType.EXTENSIONS)) {
            return items;
        }

        // Need a selected request to work with
        boolean hasRequest = event.messageEditorRequestResponse().isPresent()
            || !event.selectedRequestResponses().isEmpty();
        if (!hasRequest) return items;

        // ── Top-level menu: "Send to SessionX" ──
        JMenu menu = new JMenu("Send to SessionX");
        menu.setFont(UIManager.getFont("MenuItem.font"));

        // ── Sub-item: Add as New Login Step ──
        JMenuItem addStep = new JMenuItem("Add as Login Step…");
        addStep.setToolTipText("Open the interactive Step Builder for this request");
        addStep.addActionListener((ActionEvent e) -> SwingUtilities.invokeLater(() ->
            openStepBuilder(event)));
        menu.add(addStep);

        // ── Separator + per-profile sub-items ──
        List<SessionProfile> profiles = profileManager.getAllProfiles();
        if (!profiles.isEmpty()) {
            menu.addSeparator();
            for (SessionProfile profile : profiles) {
                JMenuItem profileItem = new JMenuItem("Append step to: " + profile.getName());
                profileItem.addActionListener((ActionEvent e) -> SwingUtilities.invokeLater(() ->
                    openStepBuilderForProfile(event, profile)));
                menu.add(profileItem);
            }
        }

        items.add(menu);
        return items;
    }

    // -------------------------------------------------------------------------
    // Dialog launchers
    // -------------------------------------------------------------------------

    /**
     * Opens the Step Builder with no pre-selected profile.
     * After the user finishes, they're prompted to pick or create a profile.
     */
    private void openStepBuilder(ContextMenuEvent event) {
        HttpRequest        req          = extractRequest(event);
        String             rawRequest   = req != null ? req.toString() : "";
        String             method       = req != null ? req.method()   : "POST";
        String             url          = req != null ? req.url()      : "";
        Map<String,String> headers      = req != null ? extractHeaders(req) : new LinkedHashMap<>();
        String             body         = req != null ? req.bodyToString() : "";

        // Show profile picker first
        List<SessionProfile> profiles = profileManager.getAllProfiles();
        SessionProfile target = pickOrCreateProfile(profiles);
        if (target == null) return; // user cancelled

        openBuilderAndCommit(target, method, url, headers, body);
    }

    /**
     * Opens the Step Builder targeted at a specific profile.
     */
    private void openStepBuilderForProfile(ContextMenuEvent event, SessionProfile profile) {
        HttpRequest        req     = extractRequest(event);
        String             method  = req != null ? req.method()      : "POST";
        String             url     = req != null ? req.url()         : "";
        Map<String,String> headers = req != null ? extractHeaders(req) : new LinkedHashMap<>();
        String             body    = req != null ? req.bodyToString() : "";

        openBuilderAndCommit(profile, method, url, headers, body);
    }

    /**
     * Core logic: builds a pre-populated LoginStep from the request, opens the dialog,
     * and on commit appends the step + tokens to the profile.
     */
    private void openBuilderAndCommit(SessionProfile profile,
                                       String method, String url,
                                       Map<String,String> headers, String body) {
        int stepIndex = profile.getLoginSteps().size(); // new step at end

        // Collect variable names already defined in prior steps
        List<String> availableVars = collectExistingVarNames(profile, stepIndex);

        // Pre-populate a LoginStep with the captured request
        LoginStep prePopulated = new LoginStep();
        prePopulated.setLabel("Step " + (stepIndex + 1));
        prePopulated.setMethod(method);
        prePopulated.setUrl(url);
        prePopulated.setHeaders(headers);
        prePopulated.setBody(body);

        // Build the token definitions already in this step slot (none for new step)
        List<TokenDefinition> existingTDs = new ArrayList<>();

        // Open dialog on the EDT
        Frame owner = findOwnerFrame();
        LoginStepBuilderDialog dialog = new LoginStepBuilderDialog(
            owner, api, stepIndex, prePopulated, existingTDs, availableVars);
        dialog.setVisible(true); // blocks until closed

        if (!dialog.wasCommitted()) return;

        // Append step to the profile
        LoginStep newStep = dialog.getResultStep();
        List<LoginStep> steps = new ArrayList<>(profile.getLoginSteps());
        steps.add(newStep);
        profile.setLoginSteps(steps);

        // Merge returned token definitions (for this step) into the profile token list
        List<TokenDefinition> tokens = new ArrayList<>(profile.getTokens());
        for (TokenDefinition td : dialog.getResultTokens()) {
            td.setLoginStepIndex(stepIndex);
            tokens.add(td);
        }
        profile.setTokens(tokens);

        // Save updated profile
        profileManager.updateProfile(profile);

        JOptionPane.showMessageDialog(owner,
            "Step " + (stepIndex + 1) + " added to profile \"" + profile.getName() + "\".\n"
            + dialog.getResultTokens().size() + " variable(s) configured.",
            "SessionX — Step Added", JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HttpRequest extractRequest(ContextMenuEvent event) {
        // Prefer the editor's current request
        if (event.messageEditorRequestResponse().isPresent()) {
            MessageEditorHttpRequestResponse mr = event.messageEditorRequestResponse().get();
            return mr.requestResponse().request();
        }
        // Fall back to first selected item in proxy table
        if (!event.selectedRequestResponses().isEmpty()) {
            return event.selectedRequestResponses().get(0).request();
        }
        return null;
    }

    private Map<String, String> extractHeaders(HttpRequest req) {
        Map<String, String> map = new LinkedHashMap<>();
        req.headers().forEach(h -> {
            // Skip pseudo-headers injected by Burp itself
            String name = h.name();
            if (!name.equalsIgnoreCase("host") && !name.startsWith(":")) {
                map.put(name, h.value());
            }
        });
        return map;
    }

    private List<String> collectExistingVarNames(SessionProfile profile, int upToStepIdx) {
        List<String> vars = new ArrayList<>();
        for (TokenDefinition td : profile.getTokens()) {
            if (td.getLoginStepIndex() < upToStepIdx) {
                String key = td.effectiveKey();
                if (!key.isBlank() && !vars.contains(key)) vars.add(key);
            }
        }
        return vars;
    }

    private SessionProfile pickOrCreateProfile(List<SessionProfile> profiles) {
        if (profiles.isEmpty()) {
            // Prompt to create one on the fly
            String name = JOptionPane.showInputDialog(findOwnerFrame(),
                "No profiles yet. Enter a name for a new profile:",
                "Create Profile", JOptionPane.QUESTION_MESSAGE);
            if (name == null || name.isBlank()) return null;
            SessionProfile p = new SessionProfile();
            p.setName(name.trim());
            profileManager.addProfile(p);
            return p;
        }

        // Let user pick from existing profiles
        String[] names = profiles.stream().map(SessionProfile::getName).toArray(String[]::new);
        Object chosen = JOptionPane.showInputDialog(findOwnerFrame(),
            "Select a profile to add this login step to:",
            "Send to SessionX", JOptionPane.QUESTION_MESSAGE,
            null, names, names[0]);

        if (chosen == null) return null;
        String chosenName = chosen.toString();
        return profiles.stream().filter(p -> p.getName().equals(chosenName)).findFirst().orElse(null);
    }

    private Frame findOwnerFrame() {
        for (Frame f : Frame.getFrames()) {
            if (f.isVisible()) return f;
        }
        return null;
    }
}

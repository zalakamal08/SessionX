package com.burpext.sessionx;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.SessionEngine;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.ui.SessionXContextMenu;
import com.burpext.sessionx.ui.SessionXTab;

/**
 * SessionX - Burp Suite extension entry point.
 *
 * Registers:
 *  1. The "SessionX" sidebar tab (profile editor + activity log)
 *  2. The HTTP pipeline handler (token injection on every request)
 *  3. The right-click context menu ("Send to SessionX") in Proxy / Repeater / Target
 */
public class SessionX implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("SessionX");
        api.logging().logToOutput("[SessionX] Starting up...");

        // Core engine components
        TokenStore     tokenStore     = new TokenStore();
        ProfileManager profileManager = new ProfileManager(api, tokenStore);
        SessionEngine  sessionEngine  = new SessionEngine(api, profileManager, tokenStore);

        // 1. HTTP handler — token injection across all Burp tools
        api.http().registerHttpHandler(sessionEngine);

        // 2. Main UI tab
        SessionXTab tab = new SessionXTab(api, profileManager, tokenStore);
        api.userInterface().registerSuiteTab("SessionX", tab.getPanel());

        // 3. Right-click context menu ("Send to SessionX") in Proxy, Repeater, etc.
        SessionXContextMenu contextMenu = new SessionXContextMenu(api, profileManager, tokenStore);
        api.userInterface().registerContextMenuItemsProvider(contextMenu);

        int profileCount = profileManager.getAllProfiles().size();
        api.logging().logToOutput(
            "[SessionX] Loaded successfully - " + profileCount + " profile(s) restored.");
    }
}

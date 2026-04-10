package com.burpext.sessionx;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.engine.ProfileManager;
import com.burpext.sessionx.engine.SessionEngine;
import com.burpext.sessionx.engine.TokenStore;
import com.burpext.sessionx.ui.SessionXTab;

/**
 * SessionX - Burp Suite extension entry point.
 *
 * Registers the "SessionX" tab and hooks the HTTP pipeline
 * for in-memory token injection across all Burp tools.
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

        // Register HTTP handler for token injection (all Burp tools)
        api.http().registerHttpHandler(sessionEngine);

        // Register the main UI tab
        SessionXTab tab = new SessionXTab(api, profileManager, tokenStore);
        api.userInterface().registerSuiteTab("SessionX", tab.getPanel());

        int profileCount = profileManager.getAllProfiles().size();
        api.logging().logToOutput(
            "[SessionX] Loaded successfully - " + profileCount + " profile(s) restored.");
    }
}

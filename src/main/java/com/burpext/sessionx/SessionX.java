package com.burpext.sessionx;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.engine.RequestReplayer;
import com.burpext.sessionx.io.SessionStore;
import com.burpext.sessionx.ui.MainPanel;

/**
 * SessionX — Header-Based Authorization Bypass Tester
 *
 * Entry point registered by Burp Suite via the BurpExtension interface.
 * Wires together the UI, data model, proxy listener, and silent session
 * persistence (~/session.json), so the extension resumes with its prior data.
 */
public class SessionX implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("SessionX");

        // Shared data model + replayer
        TestResultTableModel tableModel = new TestResultTableModel();
        RequestReplayer      replayer   = new RequestReplayer(api, tableModel);

        // Session persistence — decoupled from replayer internals via getters/setters
        SessionStore session = new SessionStore(
                api, tableModel,
                replayer::getRules, replayer::setRules,
                replayer::isInterceptProxy,    replayer::setInterceptProxy,
                replayer::isInterceptRepeater, replayer::setInterceptRepeater);

        // Restore prior state BEFORE building the UI so toggles/rules/results reflect it
        session.load();

        MainPanel mainPanel = new MainPanel(api, tableModel, replayer, session);

        // Start auto-saving only after the UI seeded itself from the loaded state
        session.attach();

        api.userInterface().registerSuiteTab("SessionX", mainPanel);
        api.http().registerHttpHandler(replayer);
        api.extension().registerUnloadingHandler(() -> {
            replayer.shutdown();
            session.shutdown();
        });

        api.logging().logToOutput("SessionX loaded — header-based authorization bypass tester ready.");
    }
}

package com.burpext.sessionx;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.core.TestResultTableModel;
import com.burpext.sessionx.engine.RequestReplayer;
import com.burpext.sessionx.ui.MainPanel;

/**
 * SessionX — Header-Based Authorization Bypass Tester
 *
 * Entry point registered by Burp Suite via the BurpExtension interface.
 * Wires together the UI, data model, and proxy listener.
 */
public class SessionX implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("SessionX");

        // Shared data model
        TestResultTableModel tableModel = new TestResultTableModel();

        // Proxy interceptor + replayer
        RequestReplayer replayer = new RequestReplayer(api, tableModel);

        // Root UI panel — registers itself as the Burp tab
        MainPanel mainPanel = new MainPanel(api, tableModel, replayer);

        api.userInterface().registerSuiteTab("SessionX", mainPanel);
        api.http().registerHttpHandler(replayer);

        api.logging().logToOutput("SessionX loaded — header-based authorization bypass tester ready.");
    }
}

package com.burpext.sessionx.engine;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.burpext.sessionx.core.HeaderRule;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResultTableModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RequestReplayer — the heart of SessionX.
 *
 * Implements Montoya's HttpHandler to intercept every proxied request.
 * For each request:
 *  1. Let the original request pass through normally (handled by Burp).
 *  2. In a background thread, modify the request headers per the active rules.
 *  3. Fire the modified request and unauthenticated request concurrently.
 *  4. Post the comparison result to the table model.
 */
public class RequestReplayer implements HttpHandler {

    private final MontoyaApi             api;
    private final TestResultTableModel   tableModel;
    private final List<HeaderRule>       rules;

    private volatile boolean interceptProxy    = false;
    private volatile boolean interceptRepeater = false;

    // Background thread pool for replaying requests
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public RequestReplayer(MontoyaApi api, TestResultTableModel tableModel) {
        this.api        = api;
        this.tableModel = tableModel;
        this.rules      = new CopyOnWriteArrayList<>();
    }

    // ─── State control ────────────────────────────────────────────────────────

    public void setInterceptProxy(boolean v)    { this.interceptProxy = v; }
    public boolean isInterceptProxy()           { return interceptProxy; }
    public void setInterceptRepeater(boolean v) { this.interceptRepeater = v; }
    public boolean isInterceptRepeater()        { return interceptRepeater; }

    /** Replace the rule list atomically. Called from ConfigPanel on EDT. */
    public void setRules(List<HeaderRule> newRules) {
        ((CopyOnWriteArrayList<HeaderRule>) rules).clear();
        rules.addAll(newRules);
    }

    public List<HeaderRule> getRules() { return rules; }

    // ─── HttpHandler ──────────────────────────────────────────────────────────

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        var tool = responseReceived.toolSource().toolType();
        boolean isProxy    = tool.name().equalsIgnoreCase("PROXY");
        boolean isRepeater = tool.name().equalsIgnoreCase("REPEATER");

        // Must be enabled for the source tool
        if (!((isProxy && interceptProxy) || (isRepeater && interceptRepeater))) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // ── In-scope check ────────────────────────────────────────────────────
        HttpRequest origRequest = responseReceived.initiatingRequest();
        String url = origRequest.url();
        if (!api.scope().isInScope(url)) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Skip static assets
        if (isStaticAsset(origRequest.path())) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Skip if no rules configured
        List<HeaderRule> activeRules = getActiveRules();
        if (activeRules.isEmpty()) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Capture original data
        HttpResponse origResponse = responseReceived;
        String method     = origRequest.method();
        int    origStatus = origResponse.statusCode();
        int    origLen    = origResponse.body().length();
        byte[] origReqBytes  = origRequest.toByteArray().getBytes();
        byte[] origRespBytes = origResponse.toByteArray().getBytes();

        // Create result row (PENDING state)
        TestResult result = new TestResult(method, url, origStatus, origLen, origReqBytes, origRespBytes);
        int rowIndex = tableModel.addResult(result);

        // Fire modified and unauth requests in background (both in same task, sequential)
        final List<HeaderRule> rulesCopy = new ArrayList<>(activeRules);
        executor.submit(() -> {
            replayModified(result, rowIndex, origRequest, rulesCopy);
            replayUnauth(result, rowIndex, origRequest, rulesCopy);
        });

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    // ─── Replay logic ─────────────────────────────────────────────────────────

    private void replayModified(TestResult result, int rowIndex,
                                HttpRequest origRequest, List<HeaderRule> activeRules) {
        try {
            HttpRequest modRequest = applyRules(origRequest, activeRules);
            byte[] modReqBytes = modRequest.toByteArray().getBytes();

            var modHttpResponse = api.http().sendRequest(modRequest);
            HttpResponse modResponse = modHttpResponse.response();

            int    modStatus    = modResponse != null ? modResponse.statusCode()   : -1;
            int    modLen       = modResponse != null ? modResponse.body().length() : -1;
            byte[] modRespBytes = modResponse != null ? modResponse.toByteArray().getBytes() : new byte[0];

            result.setModifiedResult(modStatus, modLen, modReqBytes, modRespBytes);
            tableModel.rowUpdated(rowIndex);

        } catch (Exception e) {
            api.logging().logToError("SessionX mod replay error for " + result.getUrl() + ": " + e.getMessage());
        }
    }

    private void replayUnauth(TestResult result, int rowIndex,
                              HttpRequest origRequest, List<HeaderRule> activeRules) {
        try {
            HttpRequest unauthRequest = applyUnauthRules(origRequest, activeRules);
            byte[] unauthReqBytes = unauthRequest.toByteArray().getBytes();

            var unauthHttpResponse = api.http().sendRequest(unauthRequest);
            HttpResponse unauthResponse = unauthHttpResponse.response();

            int    unauthStatus    = unauthResponse != null ? unauthResponse.statusCode()   : -1;
            int    unauthLen       = unauthResponse != null ? unauthResponse.body().length() : -1;
            byte[] unauthRespBytes = unauthResponse != null ? unauthResponse.toByteArray().getBytes() : new byte[0];

            result.setUnauthResult(unauthStatus, unauthLen, unauthReqBytes, unauthRespBytes);
            tableModel.rowUpdated(rowIndex);

        } catch (Exception e) {
            api.logging().logToError("SessionX unauth replay error for " + result.getUrl() + ": " + e.getMessage());
        }
    }

    /**
     * Apply all active rules to produce the modified request (User B token substitution).
     */
    private HttpRequest applyRules(HttpRequest request, List<HeaderRule> rules) {
        HttpRequest modified = request;
        for (HeaderRule rule : rules) {
            String name = rule.getHeaderName();
            switch (rule.getMode()) {
                case REMOVE -> modified = modified.withRemovedHeader(name);
                case REPLACE -> {
                    boolean found = modified.headers().stream()
                            .anyMatch(h -> h.name().equalsIgnoreCase(name));
                    if (found) {
                        modified = modified.withUpdatedHeader(name, rule.getReplacementValue());
                    } else {
                        modified = modified.withAddedHeader(name, rule.getReplacementValue());
                    }
                }
                case ADD -> modified = modified.withUpdatedHeader(name, rule.getReplacementValue());
            }
        }
        return modified;
    }

    /**
     * Apply unauthenticated rules: for each configured header, keep the name
     * but set the value to an empty string — resulting in "Header:" with no value.
     * This simulates a completely unauthenticated/logged-out request.
     */
    private HttpRequest applyUnauthRules(HttpRequest request, List<HeaderRule> rules) {
        HttpRequest unauth = request;
        for (HeaderRule rule : rules) {
            String name = rule.getHeaderName();
            // Only blank out headers that are actually present in the request
            boolean found = unauth.headers().stream()
                    .anyMatch(h -> h.name().equalsIgnoreCase(name));
            if (found) {
                // Remove and re-add with empty value to avoid trailing space
                unauth = unauth.withRemovedHeader(name);
                unauth = unauth.withAddedHeader(name, "");
            }
        }
        return unauth;
    }

    private List<HeaderRule> getActiveRules() {
        List<HeaderRule> active = new ArrayList<>();
        for (HeaderRule rule : rules) {
            if (rule.isEnabled()) active.add(rule);
        }
        return active;
    }

    /** Returns true for common static assets that should be skipped. */
    private boolean isStaticAsset(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.endsWith(".js")  || lower.endsWith(".css")   ||
               lower.endsWith(".png") || lower.endsWith(".jpg")   ||
               lower.endsWith(".jpeg")|| lower.endsWith(".gif")   ||
               lower.endsWith(".svg") || lower.endsWith(".ico")   ||
               lower.endsWith(".woff")|| lower.endsWith(".woff2") ||
               lower.endsWith(".ttf") || lower.endsWith(".map");
    }

    /** Shutdown background executor cleanly when extension is unloaded. */
    public void shutdown() { executor.shutdownNow(); }
}

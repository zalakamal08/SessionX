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
 *  3. Fire the modified request and capture the response.
 *  4. Post the comparison result to the table model.
 *
 * Note: we intentionally do NOT block the proxy thread (requestToBeSent returns
 * immediately). The modified replay is async via an ExecutorService.
 */
public class RequestReplayer implements HttpHandler {

    private final MontoyaApi             api;
    private final TestResultTableModel   tableModel;
    private final List<HeaderRule>       rules;

    private volatile boolean active           = false;
    private volatile boolean interceptRepeater = false;

    // Background thread pool for replaying requests
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public RequestReplayer(MontoyaApi api, TestResultTableModel tableModel) {
        this.api        = api;
        this.tableModel = tableModel;
        this.rules      = new CopyOnWriteArrayList<>();
    }

    // ─── State control ────────────────────────────────────────────────────────

    public void setActive(boolean active)            { this.active = active; }
    public boolean isActive()                        { return active; }
    public void setInterceptRepeater(boolean v)      { this.interceptRepeater = v; }

    /** Replace the rule list atomically. Called from ConfigPanel on EDT. */
    public void setRules(List<HeaderRule> newRules) {
        ((CopyOnWriteArrayList<HeaderRule>) rules).clear();
        rules.addAll(newRules);
    }

    public List<HeaderRule> getRules() { return rules; }

    // ─── HttpHandler ──────────────────────────────────────────────────────────

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // Pass through — we capture the original response in handleHttpResponseReceived
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        if (!active) return ResponseReceivedAction.continueWith(responseReceived);

        // Only intercept Proxy traffic (optionally Repeater)
        var initiator = responseReceived.toolSource();
        boolean isProxy   = initiator.toolType().name().equalsIgnoreCase("PROXY");
        boolean isRepeater = initiator.toolType().name().equalsIgnoreCase("REPEATER");

        if (!isProxy && !(interceptRepeater && isRepeater)) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Skip static assets
        String path = responseReceived.initiatingRequest().path();
        if (isStaticAsset(path)) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Skip if no rules configured
        List<HeaderRule> activeRules = getActiveRules();
        if (activeRules.isEmpty()) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Capture original data
        HttpRequest  origRequest  = responseReceived.initiatingRequest();
        HttpResponse origResponse = responseReceived;

        String method    = origRequest.method();
        String url       = origRequest.url();
        int    origStatus = origResponse.statusCode();
        int    origLen    = origResponse.body().length();
        byte[] origReqBytes  = origRequest.toByteArray().getBytes();
        byte[] origRespBytes = origResponse.toByteArray().getBytes();

        // Create result row (PENDING)
        TestResult result = new TestResult(method, url, origStatus, origLen, origReqBytes, origRespBytes);
        int rowIndex = tableModel.addResult(result);

        // Fire modified and unauth requests in background
        executor.submit(() -> {
            replayModified(result, rowIndex, origRequest, activeRules);
            replayUnauth(result, rowIndex, origRequest, activeRules);
        });

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    // ─── Replay logic ─────────────────────────────────────────────────────────

    private void replayModified(TestResult result,
                                int rowIndex,
                                HttpRequest origRequest,
                                List<HeaderRule> activeRules) {
        try {
            HttpRequest modRequest = applyRules(origRequest, activeRules);
            byte[] modReqBytes = modRequest.toByteArray().getBytes();

            var modHttpResponse = api.http().sendRequest(modRequest);
            HttpResponse modResponse = modHttpResponse.response();

            int    modStatus   = modResponse != null ? modResponse.statusCode()   : -1;
            int    modLen      = modResponse != null ? modResponse.body().length() : -1;
            byte[] modRespBytes = modResponse != null ? modResponse.toByteArray().getBytes() : new byte[0];

            result.setModifiedResult(modStatus, modLen, modReqBytes, modRespBytes);
            tableModel.rowUpdated(rowIndex);

        } catch (Exception e) {
            api.logging().logToError("SessionX mod replay error for " + result.getUrl() + ": " + e.getMessage());
        }
    }

    private void replayUnauth(TestResult result,
                              int rowIndex,
                              HttpRequest origRequest,
                              List<HeaderRule> activeRules) {
        try {
            HttpRequest unauthRequest = applyUnauthRules(origRequest, activeRules);
            byte[] unauthReqBytes = unauthRequest.toByteArray().getBytes();

            var unauthHttpResponse = api.http().sendRequest(unauthRequest);
            HttpResponse unauthResponse = unauthHttpResponse.response();

            int    unauthStatus   = unauthResponse != null ? unauthResponse.statusCode()   : -1;
            int    unauthLen      = unauthResponse != null ? unauthResponse.body().length() : -1;
            byte[] unauthRespBytes = unauthResponse != null ? unauthResponse.toByteArray().getBytes() : new byte[0];

            result.setUnauthResult(unauthStatus, unauthLen, unauthReqBytes, unauthRespBytes);
            tableModel.rowUpdated(rowIndex);

        } catch (Exception e) {
            api.logging().logToError("SessionX unauth replay error for " + result.getUrl() + ": " + e.getMessage());
        }
    }

    /**
     * Apply all active rules to produce the modified request.
     *
     * Rules are processed in order:
     *  REPLACE — update header value if present, add if missing
     *  REMOVE  — delete the header
     *  ADD     — always add the header (even if already present — replaces existing)
     */
    private HttpRequest applyRules(HttpRequest request, List<HeaderRule> rules) {
        HttpRequest modified = request;

        for (HeaderRule rule : rules) {
            String name = rule.getHeaderName();

            switch (rule.getMode()) {
                case REMOVE -> {
                    // Remove all headers with this name
                    List<HttpHeader> keepHeaders = new ArrayList<>();
                    for (HttpHeader h : modified.headers()) {
                        if (!h.name().equalsIgnoreCase(name)) keepHeaders.add(h);
                    }
                    modified = modified.withRemovedHeader(name);
                }
                case REPLACE -> {
                    // Replace existing value or add if not present
                    boolean found = modified.headers().stream()
                            .anyMatch(h -> h.name().equalsIgnoreCase(name));
                    if (found) {
                        modified = modified.withUpdatedHeader(name, rule.getReplacementValue());
                    } else {
                        modified = modified.withAddedHeader(name, rule.getReplacementValue());
                    }
                }
                case ADD -> {
                    // Always set (replace if exists, otherwise add)
                    modified = modified.withUpdatedHeader(name, rule.getReplacementValue());
                }
            }
        }
        return modified;
    }

    private HttpRequest applyUnauthRules(HttpRequest request, List<HeaderRule> rules) {
        HttpRequest unauth = request;
        for (HeaderRule rule : rules) {
            String name = rule.getHeaderName();
            boolean found = unauth.headers().stream()
                    .anyMatch(h -> h.name().equalsIgnoreCase(name));
            if (found) {
                unauth = unauth.withUpdatedHeader(name, "");
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

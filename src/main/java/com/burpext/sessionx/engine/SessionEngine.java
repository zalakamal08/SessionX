package com.burpext.sessionx.engine;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.burpext.sessionx.core.*;
import com.burpext.sessionx.util.ActivityLogger;

/**
 * Core HTTP handler - registered with Burp's HTTP pipeline.
 *
 * On every outgoing request:
 *  1. Checks if any enabled profile's scope matches the request URL
 *  2. Skips the refresh-excluded URL (loop prevention)
 *  3. Injects all configured tokens into the request (header/cookie/body)
 *
 * On every response:
 *  4. Checks if the response matches the profile's error condition
 *  5. If so, triggers RefreshController to re-run the login sequence
 */
public class SessionEngine implements HttpHandler {

    private final MontoyaApi        api;
    private final ProfileManager    profileManager;
    private final TokenStore        tokenStore;
    private final RefreshController refreshController;
    private final ActivityLogger    logger;

    public SessionEngine(MontoyaApi api, ProfileManager profileManager, TokenStore tokenStore) {
        this.api               = api;
        this.profileManager    = profileManager;
        this.tokenStore        = tokenStore;
        this.logger            = ActivityLogger.getInstance();

        LoginExecutor executor   = new LoginExecutor(api, tokenStore, this.logger);
        this.refreshController   = new RefreshController(executor, this.logger);
    }

    // --- Request interception ---

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // Fast-path: skip all processing if no profiles are active
        java.util.List<SessionProfile> enabled = profileManager.getEnabledProfiles();
        if (enabled.isEmpty()) return RequestToBeSentAction.continueWith(requestToBeSent);

        HttpRequest request = requestToBeSent;
        String url = requestToBeSent.url();

        for (SessionProfile profile : enabled) {
            // 1. Check scope
            if (!ScopeMatcher.shouldProcess(url, profile.getScope())) continue;

            // 2. Skip the login/refresh endpoint to prevent infinite loops
            String excludeUrl = profile.getErrorCondition().getRefreshExcludeUrl();
            if (excludeUrl != null && !excludeUrl.isBlank() && url.contains(excludeUrl)) {
                logger.scope("Skipped injection for refresh endpoint: " + url);
                continue;
            }

            // 3. Inject tokens
            for (TokenDefinition td : profile.getTokens()) {
                String tokenValue = tokenStore.getToken(profile.getId(), td.getTokenType());
                if (tokenValue == null || tokenValue.isBlank()) continue;
                request = injectToken(request, td, tokenValue);
            }
        }

        return RequestToBeSentAction.continueWith(request);
    }

    // --- Response inspection ---

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        // Fast-path: skip all processing if no profiles are active
        java.util.List<SessionProfile> enabled = profileManager.getEnabledProfiles();
        if (enabled.isEmpty()) return ResponseReceivedAction.continueWith(responseReceived);

        int    statusCode = responseReceived.statusCode();
        String url        = responseReceived.initiatingRequest().url();
        String body       = responseReceived.bodyToString();

        for (SessionProfile profile : enabled) {
            if (!ScopeMatcher.shouldProcess(url, profile.getScope())) continue;

            ErrorCondition ec = profile.getErrorCondition();
            if (ec.matchesStatus(statusCode) && ec.matchesBody(body)) {
                refreshController.triggerRefresh(profile,
                    statusCode + " from " + responseReceived.initiatingRequest().method()
                    + " " + url);
            }
        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    // --- Token injection logic ---

    private HttpRequest injectToken(HttpRequest request, TokenDefinition td, String value) {
        String key = td.getInjectKey();
        try {
            return switch (td.getInjectLocation()) {
                // FIX #3: Use withUpdatedHeader() not withHeader() to REPLACE
                // any existing header value rather than appending a duplicate.
                case AUTHORIZATION_HEADER ->
                    request.withUpdatedHeader("Authorization", "Bearer " + value);

                case CUSTOM_HEADER ->
                    request.withUpdatedHeader(key, value);

                case COOKIE -> {
                    // For cookies, parse and replace the specific key only,
                    // preserving all other cookies intact.
                    String existing = request.headerValue("Cookie");
                    String newCookie = mergeCookie(existing, key, value);
                    yield request.withUpdatedHeader("Cookie", newCookie);
                }

                case BODY_JSON -> {
                    String body    = request.bodyToString();
                    String updated = BodyInjector.injectJson(body, key, value);
                    yield request.withBody(updated);
                }

                case BODY_FORM -> {
                    String body    = request.bodyToString();
                    String updated = BodyInjector.injectForm(body, key, value);
                    yield request.withBody(updated);
                }

                case QUERY_PARAM -> {
                    String currentPath = request.path();
                    String separator   = currentPath.contains("?") ? "&" : "?";
                    yield request.withPath(currentPath + separator
                        + java.net.URLEncoder.encode(key,   java.nio.charset.StandardCharsets.UTF_8)
                        + "="
                        + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
                }
            };
        } catch (Exception e) {
            logger.error("Token injection failed [" + td.getTokenType() + "]: " + e.getMessage());
            return request; // fail-safe: return original request unmodified
        }
    }

    /**
     * Merges a single cookie key=value into an existing Cookie header string,
     * replacing the existing value if the key is already present.
     * Preserves all other cookies intact.
     */
    private static String mergeCookie(String existing, String key, String value) {
        String newPair = key + "=" + value;
        if (existing == null || existing.isBlank()) return newPair;

        StringBuilder result = new StringBuilder();
        boolean replaced = false;
        for (String part : existing.split(";")) {
            String trimmed = part.trim();
            if (result.length() > 0) result.append("; ");
            if (trimmed.startsWith(key + "=") || trimmed.equals(key)) {
                result.append(newPair);
                replaced = true;
            } else {
                result.append(trimmed);
            }
        }
        if (!replaced) result.append("; ").append(newPair);
        return result.toString();
    }
}

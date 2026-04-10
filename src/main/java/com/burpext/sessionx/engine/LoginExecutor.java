package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.*;
import com.burpext.sessionx.util.ActivityLogger;
import com.burpext.sessionx.util.RegexUtil;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a session profile's multi-step login sequence.
 *
 * Steps fire in order using Burp's built-in HTTP client.
 * Tokens extracted from each step's response are stored in TokenStore.
 *
 * Variable substitution: {{stepN:TOKENTYPE}} references in a step's body/headers
 * are replaced with the token value extracted from step N of this run.
 */
public class LoginExecutor {

    private final MontoyaApi    api;
    private final TokenStore    tokenStore;
    private final ActivityLogger logger;

    public LoginExecutor(MontoyaApi api, TokenStore tokenStore, ActivityLogger logger) {
        this.api        = api;
        this.tokenStore = tokenStore;
        this.logger     = logger;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Executes all login steps for the given profile.
     * Clears old tokens first, then extracts new tokens after each step.
     */
    public boolean execute(SessionProfile profile) {
        String profileId = profile.getId();
        tokenStore.clearProfile(profileId);

        List<LoginStep>       steps  = profile.getLoginSteps();
        List<TokenDefinition> tokens = profile.getTokens();

        // stepResponses[i] = raw response body from step i
        Map<Integer, String> stepResponseBodies  = new HashMap<>();
        Map<Integer, String> stepResponseHeaders = new HashMap<>();
        Map<Integer, String> stepResponseCookies = new HashMap<>();

        for (int i = 0; i < steps.size(); i++) {
            LoginStep step = steps.get(i);
            logger.info("Step " + (i + 1) + "/" + steps.size()
                + " — " + step.getMethod() + " " + step.getUrl()
                + " [" + step.getLabel() + "]");

            try {
                // Resolve {{stepN:TYPE}} variables in body and headers
                String resolvedBody    = resolveVars(step.getBody(), i, profileId);
                Map<String,String> resolvedHeaders = resolveHeaderVars(step.getHeaders(), i, profileId);

                // Build the HTTP request
                HttpRequest request = buildRequest(step.getMethod(), step.getUrl(),
                    resolvedHeaders, resolvedBody);

                // Fire it using Burp's HTTP client (goes through proxy)
                HttpRequestResponse response = api.http().sendRequest(request);

                int status = response.response().statusCode();
                logger.info("  → " + status + " " + response.response().reasonPhrase());

                // Store response parts for token extraction
                String bodyStr    = response.response().bodyToString();
                String headerStr  = response.response().toString();
                String cookieStr  = response.response().headerValue("Set-Cookie");

                stepResponseBodies.put(i, bodyStr != null ? bodyStr : "");
                stepResponseHeaders.put(i, headerStr != null ? headerStr : "");
                stepResponseCookies.put(i, cookieStr != null ? cookieStr : "");

                // Extract tokens that come from this step
                for (TokenDefinition td : tokens) {
                    if (td.getLoginStepIndex() != i) continue;

                    String source = selectSource(td.getExtractFrom(),
                        stepResponseBodies.get(i),
                        stepResponseHeaders.get(i),
                        stepResponseCookies.get(i));

                    String extracted = RegexUtil.extract(source, td.getExtractRegex());
                    if (extracted != null && !extracted.isBlank()) {
                        tokenStore.setToken(profileId, td.getTokenType(), extracted);
                        logger.token(td.getTokenType() + " extracted"
                            + " → " + preview(extracted));
                    } else {
                        logger.warn("Could not extract " + td.getTokenType()
                            + " from step " + (i + 1) + " — regex: " + td.getExtractRegex());
                    }
                }

            } catch (Exception e) {
                logger.error("Step " + (i + 1) + " failed: " + e.getMessage());
                return false;
            }
        }

        logger.info("Login sequence complete — token store updated");
        return true;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private HttpRequest buildRequest(String method, String url,
                                     Map<String, String> headers, String body) {
        HttpRequest req = HttpRequest.httpRequestFromUrl(url);
        req = req.withMethod(method);

        // Set Content-Type default for POST bodies
        if (body != null && !body.isBlank()) {
            if (!headers.containsKey("Content-Type")) {
                req = req.withHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            req = req.withBody(body);
        }

        // Apply custom headers
        for (Map.Entry<String, String> h : headers.entrySet()) {
            req = req.withHeader(h.getKey(), h.getValue());
        }

        return req;
    }

    /**
     * Resolves {{stepN:TOKENTYPE}} variables in a string using tokens already extracted.
     */
    private String resolveVars(String input, int currentStep, String profileId) {
        if (input == null || !input.contains("{{")) return input;
        String result = input;
        for (TokenType type : TokenType.values()) {
            for (int s = 0; s < currentStep; s++) {
                String placeholder = "{{step" + s + ":" + type.name() + "}}";
                if (result.contains(placeholder)) {
                    String val = tokenStore.getToken(profileId, type);
                    if (val != null) result = result.replace(placeholder, val);
                }
            }
        }
        return result;
    }

    private Map<String, String> resolveHeaderVars(Map<String, String> headers,
                                                   int step, String profileId) {
        if (headers == null) return new HashMap<>();
        Map<String, String> resolved = new HashMap<>();
        headers.forEach((k, v) -> resolved.put(k, resolveVars(v, step, profileId)));
        return resolved;
    }

    private String selectSource(ExtractSource from, String body, String headers, String cookies) {
        return switch (from) {
            case RESPONSE_BODY_JSON, RESPONSE_BODY_HTML, RESPONSE_BODY_XML -> body;
            case RESPONSE_HEADER -> headers;
            case RESPONSE_COOKIE -> cookies;
        };
    }

    /** Returns first 40 chars of a token for safe logging. */
    private String preview(String value) {
        if (value == null) return "(null)";
        return value.length() > 40 ? value.substring(0, 40) + "..." : value;
    }
}

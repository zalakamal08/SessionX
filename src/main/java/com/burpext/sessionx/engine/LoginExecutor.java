package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.*;
import com.burpext.sessionx.util.ActivityLogger;
import com.burpext.sessionx.util.JsonPathUtil;
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
 * Tokens extracted from each step's response are stored in TokenStore
 * under BOTH the named variable key and the legacy TokenType key.
 *
 * Variable substitution: {{stepN:varName}} in a step's body/headers
 * is replaced with the named variable value extracted from step N.
 * Legacy {{stepN:TOKENTYPE}} references also still work.
 */
public class LoginExecutor {

    private final MontoyaApi     api;
    private final TokenStore     tokenStore;
    private final ActivityLogger logger;

    public LoginExecutor(MontoyaApi api, TokenStore tokenStore, ActivityLogger logger) {
        this.api        = api;
        this.tokenStore = tokenStore;
        this.logger     = logger;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Executes all login steps for the given profile.
     * Clears old tokens first, then extracts new tokens after each step.
     */
    public boolean execute(SessionProfile profile) {
        String profileId = profile.getId();
        tokenStore.clearProfile(profileId);

        List<LoginStep>       steps  = profile.getLoginSteps();
        List<TokenDefinition> tokens = profile.getTokens();

        Map<Integer, String> stepResponseBodies  = new HashMap<>();
        Map<Integer, String> stepResponseHeaders = new HashMap<>();
        Map<Integer, String> stepResponseCookies = new HashMap<>();

        for (int i = 0; i < steps.size(); i++) {
            LoginStep step = steps.get(i);
            logger.info("Step " + (i + 1) + "/" + steps.size()
                + " - " + step.getMethod() + " " + step.getUrl()
                + " [" + step.getLabel() + "]");

            try {
                String resolvedBody = resolveVars(step.getBody(), i, profileId);
                Map<String, String> resolvedHeaders = resolveHeaderVars(step.getHeaders(), i, profileId);

                HttpRequest request = buildRequest(step.getMethod(), step.getUrl(),
                    resolvedHeaders, resolvedBody);

                HttpRequestResponse response = api.http().sendRequest(request);

                int status = response.response().statusCode();
                logger.info("  -> " + status + " " + response.response().reasonPhrase());

                String bodyStr   = response.response().bodyToString();
                String headerStr = response.response().toString();
                String cookieStr = response.response().headers().stream()
                    .filter(h -> h.name().equalsIgnoreCase("Set-Cookie"))
                    .map(h -> h.value())
                    .collect(java.util.stream.Collectors.joining("; "));

                stepResponseBodies.put(i, bodyStr   != null ? bodyStr   : "");
                stepResponseHeaders.put(i, headerStr != null ? headerStr : "");
                stepResponseCookies.put(i, cookieStr);

                for (TokenDefinition td : tokens) {
                    if (td.getLoginStepIndex() != i) continue;

                    String source = selectSource(td.getExtractFrom(),
                        stepResponseBodies.get(i),
                        stepResponseHeaders.get(i),
                        stepResponseCookies.get(i));

                    String extracted = extractValue(td, source);

                    if (extracted != null && !extracted.isBlank()) {
                        String varKey = td.effectiveKey();
                        // Store under named variable key (primary)
                        tokenStore.setVariable(profileId, varKey, extracted);
                        // Also store under legacy TokenType key for SessionEngine injection
                        if (td.getTokenType() != null) {
                            tokenStore.setToken(profileId, td.getTokenType(), extracted);
                        }
                        logger.token(varKey + " extracted -> " + preview(extracted));
                    } else {
                        logger.warn("Could not extract " + td.effectiveKey()
                            + " from step " + (i + 1)
                            + " - jsonpath: " + td.getExtractJsonPath()
                            + "  regex: " + td.getExtractRegex());
                    }
                }

            } catch (Exception e) {
                logger.error("Step " + (i + 1) + " failed: " + e.getMessage());
                return false;
            }
        }

        logger.info("Login sequence complete - token store updated");
        return true;
    }

    // -------------------------------------------------------------------------
    // Extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts a value from the source string using JSONPath first (if configured),
     * then falls back to regex.
     */
    private String extractValue(TokenDefinition td, String source) {
        String jsonPath = td.getExtractJsonPath();
        if (jsonPath != null && !jsonPath.isBlank()
                && td.getExtractFrom() == ExtractSource.RESPONSE_BODY_JSON) {
            String result = JsonPathUtil.extract(source, jsonPath);
            if (result != null) return result;
            logger.warn("JSONPath '" + jsonPath + "' returned null, falling back to regex");
        }
        return RegexUtil.extract(source, td.getExtractRegex());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HttpRequest buildRequest(String method, String url,
                                     Map<String, String> headers, String body) {
        HttpRequest req = HttpRequest.httpRequestFromUrl(url);
        req = req.withMethod(method);

        if (body != null && !body.isBlank()) {
            if (!headers.containsKey("Content-Type")) {
                req = req.withHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            req = req.withBody(body);
        }

        for (Map.Entry<String, String> h : headers.entrySet()) {
            req = req.withHeader(h.getKey(), h.getValue());
        }

        return req;
    }

    /**
     * Resolves {{stepN:varName}} and legacy {{stepN:TOKENTYPE}} placeholders.
     * Named variables are looked up first; TokenType enum names are a fallback.
     */
    private String resolveVars(String input, int currentStep, String profileId) {
        if (input == null || !input.contains("{{")) return input;
        String result = input;

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\{\\{step(\\d+):([^}]+)\\}\\}")
            .matcher(input);

        while (m.find()) {
            int    stepIdx = Integer.parseInt(m.group(1));
            String varName = m.group(2).trim();
            if (stepIdx >= currentStep) continue; // can only reference prior steps

            // 1. Try named variable store
            String val = tokenStore.getVariable(profileId, varName);

            // 2. Fallback: legacy TokenType enum
            if (val == null) {
                try {
                    TokenType tt = TokenType.valueOf(varName.toUpperCase());
                    val = tokenStore.getToken(profileId, tt);
                } catch (IllegalArgumentException ignore) {}
            }

            if (val != null) {
                result = result.replace(m.group(0), val);
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

    private String preview(String value) {
        if (value == null) return "(null)";
        return value.length() > 40 ? value.substring(0, 40) + "..." : value;
    }
}

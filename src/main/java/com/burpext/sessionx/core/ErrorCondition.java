package com.burpext.sessionx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines when a session is considered expired and a token refresh should trigger.
 * Also contains the refresh endpoint URL to exclude from injection (loop prevention).
 */
public class ErrorCondition {

    private List<Integer> triggerOnStatusCodes;  // e.g. [401, 403]
    private String        triggerOnBodyKeyword;   // optional body substring check
    private String        refreshExcludeUrl;      // URL to skip injection on (prevents loop)

    // ─── Jackson no-arg constructor ───────────────────────────────────────────
    public ErrorCondition() {
        this.triggerOnStatusCodes = new ArrayList<>(List.of(401));
        this.triggerOnBodyKeyword = "";
        this.refreshExcludeUrl    = "";
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public List<Integer> getTriggerOnStatusCodes() { return triggerOnStatusCodes; }
    public void          setTriggerOnStatusCodes(List<Integer> codes) { this.triggerOnStatusCodes = codes; }

    public String        getTriggerOnBodyKeyword() { return triggerOnBodyKeyword; }
    public void          setTriggerOnBodyKeyword(String kw) { this.triggerOnBodyKeyword = kw; }

    public String        getRefreshExcludeUrl()    { return refreshExcludeUrl; }
    public void          setRefreshExcludeUrl(String url) { this.refreshExcludeUrl = url; }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the given response status code is in the trigger list.
     */
    public boolean matchesStatus(int statusCode) {
        return triggerOnStatusCodes != null && triggerOnStatusCodes.contains(statusCode);
    }

    /**
     * Returns true if the given response body contains the trigger keyword (if configured).
     */
    public boolean matchesBody(String body) {
        if (triggerOnBodyKeyword == null || triggerOnBodyKeyword.isBlank()) return true;
        return body != null && body.contains(triggerOnBodyKeyword);
    }
}

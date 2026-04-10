package com.burpext.sessionx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines when a session is considered expired and a token refresh should trigger.
 * Also contains the refresh endpoint URL to exclude from injection (loop prevention).
 */
public class ErrorCondition {

    private List<Integer> triggerOnStatusCodes;
    private String        triggerOnBodyKeyword;
    private String        refreshExcludeUrl;

    // Jackson no-arg constructor
    public ErrorCondition() {
        this.triggerOnStatusCodes = new ArrayList<>(List.of(401));
        this.triggerOnBodyKeyword = "";
        this.refreshExcludeUrl    = "";
    }

    // Getters / Setters

    public List<Integer> getTriggerOnStatusCodes() { return triggerOnStatusCodes; }
    public void          setTriggerOnStatusCodes(List<Integer> codes) { this.triggerOnStatusCodes = codes; }

    public String        getTriggerOnBodyKeyword() { return triggerOnBodyKeyword; }
    public void          setTriggerOnBodyKeyword(String kw) { this.triggerOnBodyKeyword = kw; }

    public String        getRefreshExcludeUrl()    { return refreshExcludeUrl; }
    public void          setRefreshExcludeUrl(String url) { this.refreshExcludeUrl = url; }

    /**
     * Returns true if the given response status code is in the trigger list.
     */
    public boolean matchesStatus(int statusCode) {
        return triggerOnStatusCodes != null && triggerOnStatusCodes.contains(statusCode);
    }

    /**
     * Returns true if response body matches the trigger keyword (or no keyword configured).
     */
    public boolean matchesBody(String body) {
        if (triggerOnBodyKeyword == null || triggerOnBodyKeyword.isBlank()) return true;
        return body != null && body.contains(triggerOnBodyKeyword);
    }
}

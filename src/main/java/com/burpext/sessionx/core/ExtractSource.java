package com.burpext.sessionx.core;

/**
 * Where in a response to extract the token value from.
 */
public enum ExtractSource {
    RESPONSE_BODY_JSON("Response Body (JSON)"),
    RESPONSE_BODY_HTML("Response Body (HTML/Regex)"),
    RESPONSE_BODY_XML("Response Body (XML)"),
    RESPONSE_HEADER("Response Header"),
    RESPONSE_COOKIE("Response Cookie (Set-Cookie)");

    private final String displayName;

    ExtractSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

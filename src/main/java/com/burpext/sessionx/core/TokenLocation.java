package com.burpext.sessionx.core;

/**
 * Where in an outgoing request to inject the token value.
 */
public enum TokenLocation {
    AUTHORIZATION_HEADER("Authorization Header (Bearer)"),
    CUSTOM_HEADER("Custom Header"),
    COOKIE("Cookie"),
    BODY_JSON("Body — JSON key"),
    BODY_FORM("Body — Form field"),
    QUERY_PARAM("Query Parameter");

    private final String displayName;

    TokenLocation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

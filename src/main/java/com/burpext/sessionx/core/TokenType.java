package com.burpext.sessionx.core;

/**
 * The type of authentication token this definition handles.
 */
public enum TokenType {
    BEARER("Bearer Token"),
    SESSION_COOKIE("Session Cookie"),
    CSRF("CSRF Token"),
    REFRESH("Refresh Token"),
    CUSTOM("Custom");

    private final String displayName;

    TokenType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

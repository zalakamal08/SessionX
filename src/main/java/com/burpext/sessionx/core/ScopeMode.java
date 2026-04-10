package com.burpext.sessionx.core;

/**
 * Scope list operating mode.
 * WHITELIST — extension only acts on URLs matching a rule.
 * BLACKLIST — extension acts on everything EXCEPT URLs matching a rule.
 */
public enum ScopeMode {
    WHITELIST("Whitelist (only process matching URLs)"),
    BLACKLIST("Blacklist (skip matching URLs)");

    private final String displayName;

    ScopeMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}

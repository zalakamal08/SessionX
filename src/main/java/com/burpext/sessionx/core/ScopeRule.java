package com.burpext.sessionx.core;

/**
 * A single URL pattern rule inside a profile's scope list.
 * Supports '*' as a wildcard matching any characters.
 *
 * Examples:
 *   *.target.com/api/*   → match all API calls on any subdomain
 *   */logout             → match any logout endpoint
 */
public class ScopeRule {

    private String  pattern;
    private boolean enabled;
    private String  comment;

    // ─── Jackson no-arg constructor ───────────────────────────────────────────
    public ScopeRule() {
        this.enabled = true;
        this.comment = "";
    }

    public ScopeRule(String pattern, boolean enabled, String comment) {
        this.pattern = pattern;
        this.enabled = enabled;
        this.comment = comment;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public String getPattern()  { return pattern; }
    public void   setPattern(String pattern)   { this.pattern = pattern; }

    public boolean isEnabled()  { return enabled; }
    public void    setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getComment()  { return comment; }
    public void   setComment(String comment)   { this.comment = comment; }
}

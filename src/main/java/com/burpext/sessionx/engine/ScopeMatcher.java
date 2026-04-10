package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.ScopeList;
import com.burpext.sessionx.core.ScopeMode;
import com.burpext.sessionx.core.ScopeRule;

/**
 * Evaluates whether a given URL should be processed by the extension
 * based on a profile's whitelist or blacklist scope rules.
 *
 * Pattern syntax:  '*' matches any sequence of characters.
 * Examples:
 *   *.target.com/api/*  →  matches all API paths on any subdomain
 *   */logout            →  matches any logout endpoint
 */
public class ScopeMatcher {

    /**
     * Returns true if the extension should inject tokens into this URL.
     */
    public static boolean shouldProcess(String url, ScopeList scope) {
        if (scope == null || scope.getRules() == null || scope.getRules().isEmpty()) {
            // No rules configured — process everything by default
            return true;
        }

        boolean anyMatch = scope.getRules().stream()
            .filter(ScopeRule::isEnabled)
            .anyMatch(rule -> matches(url, rule.getPattern()));

        return scope.getMode() == ScopeMode.WHITELIST ? anyMatch : !anyMatch;
    }

    /**
     * Glob-style match: '*' matches any sequence of characters.
     * Case-insensitive to handle URL variations.
     */
    static boolean matches(String url, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        // Convert glob pattern to regex: escape dots, replace * with .*
        String regex = "(?i)" + pattern.trim()
            .replace(".", "\\.")
            .replace("*", ".*");
        return url.matches(regex);
    }
}

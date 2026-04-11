package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.ScopeList;
import com.burpext.sessionx.core.ScopeMode;
import com.burpext.sessionx.core.ScopeRule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates whether a given URL should be processed by the extension
 * based on a profile's whitelist or blacklist scope rules.
 *
 * Pattern syntax: '*' is a wildcard matching any character sequence.
 * Examples:
 *   *.target.com/api/*   - all API paths on any subdomain
 *   192.168.1.* /admin/* - admin panel on any 192.168.1.x host
 *
 * FIX #4 (Regex Injection / Crash):
 *   - User-supplied glob patterns are now split on '*', each literal segment
 *     is escaped with Pattern.quote(), then rejoined with ".*". This prevents
 *     any special regex characters in the user's input (e.g. [ ] ( ) ? +)
 *     from being interpreted as regex metacharacters and crashing the handler.
 *   - PatternSyntaxException is caught defensively (belt-and-suspenders).
 *
 * Performance:
 *   - Compiled patterns are cached per rule string — patterns are only compiled
 *     once even if millions of requests pass through.
 */
public class ScopeMatcher {

    // Cache compiled patterns to avoid re-compiling on every request
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private ScopeMatcher() {}

    /**
     * Returns true if the extension should inject tokens into this URL.
     */
    public static boolean shouldProcess(String url, ScopeList scope) {
        if (scope == null || scope.getRules() == null || scope.getRules().isEmpty()) {
            return true; // no rules = process everything by default
        }

        boolean anyMatch = scope.getRules().stream()
            .filter(ScopeRule::isEnabled)
            .anyMatch(rule -> matches(url, rule.getPattern()));

        return scope.getMode() == ScopeMode.WHITELIST ? anyMatch : !anyMatch;
    }

    /**
     * Safe glob match: '*' is the only special character accepted.
     * All other input is treated as a literal string.
     *
     * FIX: Uses Pattern.quote() on each segment between '*' wildcards,
     * so input like "*.example.com/api[v1]/*" will never cause a
     * PatternSyntaxException — the brackets are safely quoted.
     */
    static boolean matches(String url, String pattern) {
        if (pattern == null || pattern.isBlank() || url == null) return false;

        Pattern compiled = PATTERN_CACHE.computeIfAbsent(pattern, p -> {
            try {
                // Split on '*', quote each segment, rejoin with '.*'
                String[] parts = p.split("\\*", -1);
                StringBuilder regex = new StringBuilder("(?i)");
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) regex.append(".*");
                    regex.append(Pattern.quote(parts[i]));
                }
                return Pattern.compile(regex.toString());
            } catch (PatternSyntaxException e) {
                // Defensive fallback: if somehow still fails, never match
                return Pattern.compile("(?!)"); // never-matching pattern
            }
        });

        return compiled.matcher(url).matches();
    }

    /** Clears the pattern cache — useful after bulk scope rule edits. */
    public static void clearCache() {
        PATTERN_CACHE.clear();
    }
}

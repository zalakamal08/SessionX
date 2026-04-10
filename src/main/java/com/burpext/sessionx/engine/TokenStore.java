package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.TokenType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for extracted token values.
 *
 * Key structure:  profileId → TokenType → tokenValue
 *
 * Using ConcurrentHashMap so the SessionEngine (HTTP thread) and
 * LoginExecutor (background thread) can both access tokens safely.
 */
public class TokenStore {

    // profileId → (tokenType → value)
    private final Map<String, Map<TokenType, String>> store = new ConcurrentHashMap<>();

    // ─── Write ────────────────────────────────────────────────────────────────

    public void setToken(String profileId, TokenType type, String value) {
        store.computeIfAbsent(profileId, k -> new ConcurrentHashMap<>())
             .put(type, value);
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns the stored token value, or null if not yet extracted.
     */
    public String getToken(String profileId, TokenType type) {
        Map<TokenType, String> profileTokens = store.get(profileId);
        if (profileTokens == null) return null;
        return profileTokens.get(type);
    }

    /**
     * Returns true if at least one token is stored for the given profile.
     */
    public boolean hasTokens(String profileId) {
        Map<TokenType, String> profileTokens = store.get(profileId);
        return profileTokens != null && !profileTokens.isEmpty();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Clears all tokens for a profile — called before re-executing login.
     */
    public void clearProfile(String profileId) {
        store.remove(profileId);
    }

    /**
     * Returns a snapshot of all token types for a profile (for display in the UI).
     */
    public Map<TokenType, String> getAllTokens(String profileId) {
        return store.getOrDefault(profileId, Map.of());
    }
}

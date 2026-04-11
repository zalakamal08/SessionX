package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.TokenType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for extracted token values.
 *
 * Two parallel keying structures:
 *   profileId → TokenType  → value  (legacy / backward-compat)
 *   profileId → varName    → value  (new — named variable store)
 *
 * Uses ConcurrentHashMap so the SessionEngine (HTTP thread) and
 * LoginExecutor (background thread) can both access tokens safely.
 */
public class TokenStore {

    // profileId -> (tokenType -> value)    [legacy]
    private final Map<String, Map<TokenType, String>> store = new ConcurrentHashMap<>();

    // profileId -> (variableName -> value) [named variable store]
    private final Map<String, Map<String, String>> varStore = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Legacy TokenType-keyed API (kept for compat with SessionEngine injection)
    // -------------------------------------------------------------------------

    public void setToken(String profileId, TokenType type, String value) {
        store.computeIfAbsent(profileId, k -> new ConcurrentHashMap<>()).put(type, value);
    }

    public String getToken(String profileId, TokenType type) {
        Map<TokenType, String> profileTokens = store.get(profileId);
        if (profileTokens == null) return null;
        return profileTokens.get(type);
    }

    public boolean hasTokens(String profileId) {
        Map<TokenType, String> profileTokens = store.get(profileId);
        boolean hasLegacy = profileTokens != null && !profileTokens.isEmpty();
        Map<String, String> namedTokens = varStore.get(profileId);
        boolean hasNamed  = namedTokens  != null && !namedTokens.isEmpty();
        return hasLegacy || hasNamed;
    }

    public Map<TokenType, String> getAllTokens(String profileId) {
        return store.getOrDefault(profileId, Map.of());
    }

    // -------------------------------------------------------------------------
    // Named variable API  (used by LoginExecutor + cross-step resolution)
    // -------------------------------------------------------------------------

    /**
     * Stores a value under a free-text variable name.
     * e.g. setVariable(profileId, "access_token", "eyJhbGci...")
     */
    public void setVariable(String profileId, String variableName, String value) {
        varStore.computeIfAbsent(profileId, k -> new ConcurrentHashMap<>())
                .put(variableName, value);
    }

    /**
     * Retrieves a value by variable name.
     * Returns null if not yet extracted.
     */
    public String getVariable(String profileId, String variableName) {
        Map<String, String> profileVars = varStore.get(profileId);
        if (profileVars == null) return null;
        return profileVars.get(variableName);
    }

    /**
     * Returns a snapshot of all named variables for a profile.
     * Used by the UI to show "live" extracted values.
     */
    public Map<String, String> getAllVariables(String profileId) {
        return varStore.getOrDefault(profileId, Map.of());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void clearProfile(String profileId) {
        store.remove(profileId);
        varStore.remove(profileId);
    }
}


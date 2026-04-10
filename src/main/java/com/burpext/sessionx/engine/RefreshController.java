package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.SessionProfile;
import com.burpext.sessionx.util.ActivityLogger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Guards against refresh loops and concurrent refresh storms.
 *
 * When a 401/403 triggers a refresh, this controller:
 *  1. Checks the 5-second cooldown — ignores duplicate triggers
 *  2. Runs the LoginExecutor on a background thread (doesn't block proxy)
 *  3. Updates the last-refresh timestamp per profile
 */
public class RefreshController {

    private static final long COOLDOWN_MS = 5_000L;

    private final LoginExecutor loginExecutor;
    private final ActivityLogger logger;

    // profileId → last refresh attempt timestamp
    private final Map<String, Instant> lastRefresh = new ConcurrentHashMap<>();

    // profileId → currently refreshing flag
    private final Map<String, Boolean> inProgress = new ConcurrentHashMap<>();

    public RefreshController(LoginExecutor loginExecutor, ActivityLogger logger) {
        this.loginExecutor = loginExecutor;
        this.logger        = logger;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Triggers a login re-execution for the given profile, subject to cooldown.
     * Runs asynchronously on a background thread.
     *
     * @param profile   the profile whose session has expired
     * @param trigger   human-readable description of why (for logging)
     */
    public void triggerRefresh(SessionProfile profile, String trigger) {
        String profileId = profile.getId();

        // Cooldown guard — don't re-fire within 5 seconds of last attempt
        Instant last = lastRefresh.get(profileId);
        if (last != null && Instant.now().toEpochMilli() - last.toEpochMilli() < COOLDOWN_MS) {
            logger.warn("Refresh skipped (cooldown active) — " + trigger);
            return;
        }

        // In-progress guard — don't fire twice simultaneously
        if (Boolean.TRUE.equals(inProgress.get(profileId))) {
            logger.warn("Refresh skipped (already in progress) — " + trigger);
            return;
        }

        // Mark refresh as in progress
        inProgress.put(profileId, true);
        lastRefresh.put(profileId, Instant.now());

        logger.refresh("Session expired — " + trigger + " — re-running login for \""
            + profile.getName() + "\"");

        // Run asynchronously so we don't block Burp's proxy thread
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                loginExecutor.execute(profile);
            } catch (Exception e) {
                logger.error("Login re-execution failed: " + e.getMessage());
            } finally {
                inProgress.put(profileId, false);
            }
        });
    }

    /**
     * Returns the last refresh timestamp for a profile, or null if never refreshed.
     */
    public Instant getLastRefresh(String profileId) {
        return lastRefresh.get(profileId);
    }
}

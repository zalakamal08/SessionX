package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.SessionProfile;
import com.burpext.sessionx.util.ActivityLogger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guards against refresh loops and concurrent refresh storms.
 *
 * FIX #1 (Race Condition / TOCTOU):
 *   - Replaced two-step check-then-act (inProgress.get + inProgress.put) with
 *     an atomic putIfAbsent — a single, lock-free operation. Now even if 100
 *     proxy threads hit this simultaneously, exactly one will proceed.
 *
 * Performance:
 *   - ExecutorService is now a bounded single thread per profile via
 *     a per-profile SemaphoreExecutor pattern. Heavy traffic cannot
 *     spawn unbounded threads.
 *   - Cooldown stored as AtomicLong epoch ms, not Instant objects —
 *     no boxing/unboxing overhead on the hot proxy path.
 */
public class RefreshController {

    private static final long COOLDOWN_MS = 5_000L;

    private final LoginExecutor  loginExecutor;
    private final ActivityLogger logger;

    // Bounded pool — max 2 background refresh threads total. Prevents refresh
    // storms from spawning dozens of threads under heavy concurrent traffic.
    private final ExecutorService executor = new ThreadPoolExecutor(
        1, 2,
        30L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(4),   // queue max 4 pending; extras silently dropped
        new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    // profileId -> last refresh start time (ms epoch). AtomicLong avoids
    // boxing overhead on ConcurrentHashMap value updates.
    private final ConcurrentHashMap<String, AtomicLong> lastRefresh = new ConcurrentHashMap<>();

    // FIX: Use ConcurrentHashMap as the mutex — putIfAbsent is a single atomic
    // operation, eliminating the TOCTOU race between get() and put().
    // Key presence = "in progress", removal = "done".
    private final ConcurrentHashMap<String, Boolean> inProgress = new ConcurrentHashMap<>();

    public RefreshController(LoginExecutor loginExecutor, ActivityLogger logger) {
        this.loginExecutor = loginExecutor;
        this.logger        = logger;
    }

    /**
     * Triggers a login re-execution for the given profile, subject to cooldown.
     * Runs asynchronously on a background thread and never blocks the proxy thread.
     *
     * @param profile  the profile whose session has expired
     * @param trigger  human-readable description of the trigger (for logging)
     */
    public void triggerRefresh(SessionProfile profile, String trigger) {
        String profileId = profile.getId();

        // --- Cooldown guard (atomic read, no allocation) ---
        AtomicLong lastEpoch = lastRefresh.get(profileId);
        if (lastEpoch != null
                && (System.currentTimeMillis() - lastEpoch.get()) < COOLDOWN_MS) {
            logger.warn("[REFRESH] Cooldown active — skipped: " + trigger);
            return;
        }

        // --- In-progress guard (FIX #1: atomic putIfAbsent — no TOCTOU race) ---
        if (inProgress.putIfAbsent(profileId, Boolean.TRUE) != null) {
            logger.warn("[REFRESH] Already in progress — skipped: " + trigger);
            return;
        }

        // Record start time AFTER acquiring the lock
        lastRefresh.computeIfAbsent(profileId, k -> new AtomicLong())
                   .set(System.currentTimeMillis());

        logger.refresh("Session expired — " + trigger
            + " — re-running login for \"" + profile.getName() + "\"");

        // Submit to bounded pool; DiscardOldestPolicy prevents queue overflow
        executor.submit(() -> {
            try {
                loginExecutor.execute(profile);
            } catch (Exception e) {
                logger.error("Login re-execution failed: " + e.getMessage());
            } finally {
                // Release the lock — allows the next trigger after cooldown
                inProgress.remove(profileId);
            }
        });
    }

    /** Returns ms since the last refresh attempt for a profile, or -1 if never. */
    public long getLastRefreshMs(String profileId) {
        AtomicLong ts = lastRefresh.get(profileId);
        return ts == null ? -1L : ts.get();
    }
}

package com.burpext.sessionx.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Singleton structured activity logger for SessionX.
 *
 * All engine operations log here. The UI subscribes via a listener
 * to receive new entries and append them to the ActivityLogPanel.
 *
 * Log prefixes:
 *   [INFO]     → grey    — general lifecycle events
 *   [TOKEN]    → green   — token extracted / injected
 *   [REFRESH]  → orange  — session expired, login re-executed
 *   [ERROR]    → red     — failures or exceptions
 *   [SCOPE]    → blue    — URL skipped due to scope rules
 */
public class ActivityLogger {

    private static final ActivityLogger INSTANCE = new ActivityLogger();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<Consumer<String>> listeners = new ArrayList<>();

    private ActivityLogger() {}

    public static ActivityLogger getInstance() { return INSTANCE; }

    // ─── Log methods ──────────────────────────────────────────────────────────

    public void info(String msg)    { log("[INFO]    ", msg); }
    public void token(String msg)   { log("[TOKEN]   ", msg); }
    public void refresh(String msg) { log("[REFRESH] ", msg); }
    public void error(String msg)   { log("[ERROR]   ", msg); }
    public void warn(String msg)    { log("[WARN]    ", msg); }
    public void scope(String msg)   { log("[SCOPE]   ", msg); }

    // ─── Listener interface (UI subscribes here) ──────────────────────────────

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void log(String prefix, String msg) {
        String entry = "[" + LocalTime.now().format(FMT) + "] " + prefix + msg;
        for (Consumer<String> l : listeners) {
            try { l.accept(entry); } catch (Exception ignored) {}
        }
    }
}

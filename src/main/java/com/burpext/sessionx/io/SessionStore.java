package com.burpext.sessionx.io;

import burp.api.montoya.MontoyaApi;
import com.burpext.sessionx.core.HeaderRule;
import com.burpext.sessionx.core.HeaderRule.Mode;
import com.burpext.sessionx.core.TestResult;
import com.burpext.sessionx.core.TestResultTableModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Silent, self-contained session persistence.
 *
 * Everything the extension produces — every original / modified / unauthenticated
 * request &amp; response, the configured header rules, and the interception toggle
 * state — is mirrored to {@code ~/session.json}. Saves are debounced and run on a
 * background daemon thread so they never block the UI. On startup the file is
 * loaded so the extension resumes with exactly the data it had last time.
 */
public class SessionStore {

    private final Path file = Paths.get(System.getProperty("user.home"), "session.json");

    private final MontoyaApi         api;
    private final TestResultTableModel store;

    // Suppliers/consumers so the store stays decoupled from RequestReplayer specifics
    private final java.util.function.Supplier<List<HeaderRule>> rulesGetter;
    private final Consumer<List<HeaderRule>>                    rulesSetter;
    private final BooleanSupplier proxyGetter;
    private final BooleanSupplier repeaterGetter;
    private final Consumer<Boolean> proxySetter;
    private final Consumer<Boolean> repeaterSetter;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ScheduledExecutorService saver =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SessionX-saver");
                t.setDaemon(true);
                return t;
            });
    private volatile ScheduledFuture<?> pending;

    public SessionStore(MontoyaApi api,
                        TestResultTableModel store,
                        java.util.function.Supplier<List<HeaderRule>> rulesGetter,
                        Consumer<List<HeaderRule>> rulesSetter,
                        BooleanSupplier proxyGetter,
                        Consumer<Boolean> proxySetter,
                        BooleanSupplier repeaterGetter,
                        Consumer<Boolean> repeaterSetter) {
        this.api            = api;
        this.store          = store;
        this.rulesGetter    = rulesGetter;
        this.rulesSetter    = rulesSetter;
        this.proxyGetter    = proxyGetter;
        this.proxySetter    = proxySetter;
        this.repeaterGetter = repeaterGetter;
        this.repeaterSetter = repeaterSetter;
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    /** Load persisted state into the model, rules, and toggles. Safe to call once at startup. */
    public void load() {
        try {
            if (!Files.exists(file)) return;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            SessionData data = gson.fromJson(json, SessionData.class);
            if (data == null) return;

            // Rules + toggles
            if (data.rules != null) {
                List<HeaderRule> rules = new ArrayList<>();
                for (RuleDto d : data.rules) {
                    if (d == null || d.header == null || d.header.isBlank()) continue;
                    HeaderRule r = new HeaderRule(d.header, Mode.fromString(d.mode), d.value == null ? "" : d.value);
                    r.setEnabled(d.enabled);
                    rules.add(r);
                }
                if (!rules.isEmpty()) rulesSetter.accept(rules);
            }
            proxySetter.accept(data.interceptProxy);
            repeaterSetter.accept(data.interceptRepeater);

            // Results
            if (data.results != null && !data.results.isEmpty()) {
                List<TestResult> restored = new ArrayList<>();
                for (ResultDto d : data.results) {
                    if (d == null) continue;
                    restored.add(TestResult.restore(
                            d.id, d.method, d.url,
                            d.origStatus, d.origLength, dec(d.origReq), dec(d.origResp),
                            d.modStatus, d.modLength, dec(d.modReq), dec(d.modResp),
                            d.unauthStatus, d.unauthLength, dec(d.unauthReq), dec(d.unauthResp)));
                }
                SwingUtilities.invokeLater(() -> restored.forEach(store::addResult));
            }

            api.logging().logToOutput("SessionX: restored session from " + file);
        } catch (Exception e) {
            api.logging().logToError("SessionX: failed to load session.json — " + e.getMessage());
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    /** Register auto-save on any change to the results model. */
    public void attach() {
        store.addTableModelListener(e -> requestSave());
    }

    /** Debounced save — coalesces bursts of updates into a single write. */
    public void requestSave() {
        ScheduledFuture<?> prev = pending;
        if (prev != null) prev.cancel(false);
        try {
            pending = saver.schedule(this::writeNow, 700, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Executor shutting down — nothing to do.
        }
    }

    private void writeNow() {
        try {
            SessionData data = new SessionData();
            data.interceptProxy    = proxyGetter.getAsBoolean();
            data.interceptRepeater = repeaterGetter.getAsBoolean();

            for (HeaderRule r : rulesGetter.get()) {
                RuleDto d = new RuleDto();
                d.enabled = r.isEnabled();
                d.header  = r.getHeaderName();
                d.mode    = r.getMode().name();
                d.value   = r.getReplacementValue();
                data.rules.add(d);
            }

            for (TestResult r : store.getAll()) {
                ResultDto d = new ResultDto();
                d.id = r.getId(); d.method = r.getMethod(); d.url = r.getUrl();
                d.origStatus = r.getOrigStatus(); d.origLength = r.getOrigLength();
                d.origReq = enc(r.getOrigRequestBytes()); d.origResp = enc(r.getOrigResponseBytes());
                d.modStatus = r.getModStatus(); d.modLength = r.getModLength();
                d.modReq = enc(r.getModRequestBytes()); d.modResp = enc(r.getModResponseBytes());
                d.unauthStatus = r.getUnauthStatus(); d.unauthLength = r.getUnauthLength();
                d.unauthReq = enc(r.getUnauthRequestBytes()); d.unauthResp = enc(r.getUnauthResponseBytes());
                data.results.add(d);
            }

            String json = gson.toJson(data);
            Path tmp = file.resolveSibling("session.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            api.logging().logToError("SessionX: failed to save session.json — " + e.getMessage());
        }
    }

    /** Flush any pending write and stop the background thread. */
    public void shutdown() {
        try {
            ScheduledFuture<?> prev = pending;
            if (prev != null) prev.cancel(false);
            writeNow();
        } finally {
            saver.shutdownNow();
        }
    }

    // ── Base64 helpers ─────────────────────────────────────────────────────────

    private static String enc(byte[] b) {
        return (b == null || b.length == 0) ? "" : Base64.getEncoder().encodeToString(b);
    }

    private static byte[] dec(String s) {
        if (s == null || s.isEmpty()) return new byte[0];
        try { return Base64.getDecoder().decode(s); } catch (Exception e) { return new byte[0]; }
    }

    // ── Serialization DTOs (public fields → clean JSON, no reflection surprises) ──

    static class SessionData {
        List<ResultDto> results = new ArrayList<>();
        List<RuleDto>   rules   = new ArrayList<>();
        boolean interceptProxy;
        boolean interceptRepeater;
    }

    static class ResultDto {
        int id; String method; String url;
        int origStatus; int origLength; String origReq; String origResp;
        int modStatus;  int modLength;  String modReq;  String modResp;
        int unauthStatus; int unauthLength; String unauthReq; String unauthResp;
    }

    static class RuleDto {
        boolean enabled; String header; String mode; String value;
    }
}

package com.burpext.sessionx.core;

/**
 * Immutable snapshot of test results for a single intercepted request.
 * Holds both the original and modified request/response data.
 */
public class TestResult {

    public enum VulnerabilityStatus {
        PENDING("⏳ Pending"),
        VULNERABLE("🔴 Vulnerable"),
        ENFORCED("🟢 Enforced"),
        INTERESTING("🟡 Interesting");

        private final String label;
        VulnerabilityStatus(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }

    // ─── Identity ─────────────────────────────────────────────────────────────
    private final int    id;
    private final String method;
    private final String url;

    // ─── Original request/response ────────────────────────────────────────────
    private final int    origStatus;
    private final int    origLength;
    private final byte[] origRequestBytes;
    private final byte[] origResponseBytes;

    // ─── Modified request/response ────────────────────────────────────────────
    private volatile int    modStatus;
    private volatile int    modLength;
    private volatile byte[] modRequestBytes;
    private volatile byte[] modResponseBytes;

    // ─── Derived ──────────────────────────────────────────────────────────────
    private volatile VulnerabilityStatus status;

    // Used as a running counter
    private static volatile int counter = 0;

    public TestResult(String method,
                      String url,
                      int origStatus,
                      int origLength,
                      byte[] origRequestBytes,
                      byte[] origResponseBytes) {
        this.id                = ++counter;
        this.method            = method;
        this.url               = url;
        this.origStatus        = origStatus;
        this.origLength        = origLength;
        this.origRequestBytes  = origRequestBytes != null ? origRequestBytes : new byte[0];
        this.origResponseBytes = origResponseBytes != null ? origResponseBytes : new byte[0];
        this.modStatus         = -1;
        this.modLength         = -1;
        this.modRequestBytes   = new byte[0];
        this.modResponseBytes  = new byte[0];
        this.status            = VulnerabilityStatus.PENDING;
    }

    /** Called by the replayer once the modified response arrives. */
    public void setModifiedResult(int modStatus,
                                   int modLength,
                                   byte[] modRequestBytes,
                                   byte[] modResponseBytes) {
        this.modStatus        = modStatus;
        this.modLength        = modLength;
        this.modRequestBytes  = modRequestBytes != null ? modRequestBytes : new byte[0];
        this.modResponseBytes = modResponseBytes != null ? modResponseBytes : new byte[0];
        this.status           = computeStatus();
    }

    /**
     * Vulnerability heuristic:
     *   VULNERABLE  — same (or very similar) status code AND content-length within 5%
     *   INTERESTING — status matches but length differs moderately (5-20% diff)
     *   ENFORCED    — significant difference in status or length (>20% diff, or 4xx/5xx vs 2xx)
     */
    private VulnerabilityStatus computeStatus() {
        if (modStatus == -1) return VulnerabilityStatus.PENDING;

        boolean statusMatch = (origStatus / 100) == (modStatus / 100);   // same class (2xx, 4xx, etc.)

        double lengthDiff = origLength == 0 ? 0.0
                : Math.abs(origLength - modLength) / (double) origLength;

        if (statusMatch && lengthDiff <= 0.05) {
            return VulnerabilityStatus.VULNERABLE;
        } else if (statusMatch && lengthDiff <= 0.20) {
            return VulnerabilityStatus.INTERESTING;
        } else {
            return VulnerabilityStatus.ENFORCED;
        }
    }

    public static void resetCounter() { counter = 0; }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public int                  getId()               { return id; }
    public String               getMethod()           { return method; }
    public String               getUrl()              { return url; }
    public int                  getOrigStatus()       { return origStatus; }
    public int                  getOrigLength()       { return origLength; }
    public byte[]               getOrigRequestBytes() { return origRequestBytes; }
    public byte[]               getOrigResponseBytes(){ return origResponseBytes; }
    public int                  getModStatus()        { return modStatus; }
    public int                  getModLength()        { return modLength; }
    public byte[]               getModRequestBytes()  { return modRequestBytes; }
    public byte[]               getModResponseBytes() { return modResponseBytes; }
    public VulnerabilityStatus  getStatus()           { return status; }
}

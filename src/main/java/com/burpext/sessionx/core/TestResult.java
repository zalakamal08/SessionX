package com.burpext.sessionx.core;

/**
 * Immutable snapshot of test results for a single intercepted request.
 * Holds original, modified, and unauthenticated request/response data.
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
    private volatile VulnerabilityStatus modVulnStatus;

    // ─── Unauthenticated request/response ─────────────────────────────────────
    private volatile int    unauthStatus;
    private volatile int    unauthLength;
    private volatile byte[] unauthRequestBytes;
    private volatile byte[] unauthResponseBytes;
    private volatile VulnerabilityStatus unauthVulnStatus;

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
        this.modVulnStatus     = VulnerabilityStatus.PENDING;

        this.unauthStatus        = -1;
        this.unauthLength        = -1;
        this.unauthRequestBytes  = new byte[0];
        this.unauthResponseBytes = new byte[0];
        this.unauthVulnStatus    = VulnerabilityStatus.PENDING;
    }

    public synchronized void setModifiedResult(int status, int length, byte[] req, byte[] resp) {
        this.modStatus        = status;
        this.modLength        = length;
        this.modRequestBytes  = req != null ? req : new byte[0];
        this.modResponseBytes = resp != null ? resp : new byte[0];
        this.modVulnStatus    = computeStatus(modStatus, modLength);
    }

    public synchronized void setUnauthResult(int status, int length, byte[] req, byte[] resp) {
        this.unauthStatus        = status;
        this.unauthLength        = length;
        this.unauthRequestBytes  = req != null ? req : new byte[0];
        this.unauthResponseBytes = resp != null ? resp : new byte[0];
        this.unauthVulnStatus    = computeStatus(unauthStatus, unauthLength);
    }

    private VulnerabilityStatus computeStatus(int targetStatus, int targetLength) {
        if (targetStatus == -1) return VulnerabilityStatus.PENDING;

        boolean statusMatch = (origStatus / 100) == (targetStatus / 100);

        double lengthDiff = origLength == 0 ? 0.0
                : Math.abs(origLength - targetLength) / (double) origLength;

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

    public int                  getId()                { return id; }
    public String               getMethod()            { return method; }
    public String               getUrl()               { return url; }
    
    public int                  getOrigStatus()        { return origStatus; }
    public int                  getOrigLength()        { return origLength; }
    public byte[]               getOrigRequestBytes()  { return origRequestBytes; }
    public byte[]               getOrigResponseBytes() { return origResponseBytes; }
    
    public int                  getModStatus()         { return modStatus; }
    public int                  getModLength()         { return modLength; }
    public byte[]               getModRequestBytes()   { return modRequestBytes; }
    public byte[]               getModResponseBytes()  { return modResponseBytes; }
    public VulnerabilityStatus  getModVulnStatus()     { return modVulnStatus; }

    public int                  getUnauthStatus()         { return unauthStatus; }
    public int                  getUnauthLength()         { return unauthLength; }
    public byte[]               getUnauthRequestBytes()   { return unauthRequestBytes; }
    public byte[]               getUnauthResponseBytes()  { return unauthResponseBytes; }
    public VulnerabilityStatus  getUnauthVulnStatus()     { return unauthVulnStatus; }

    // Helper for table combined status
    public String getCombinedStatus() {
        if (modVulnStatus == VulnerabilityStatus.PENDING && unauthVulnStatus == VulnerabilityStatus.PENDING) {
            return "⏳ Pending";
        }
        return String.format("MOD: %s | UNAUTH: %s", modVulnStatus.toString(), unauthVulnStatus.toString());
    }
}

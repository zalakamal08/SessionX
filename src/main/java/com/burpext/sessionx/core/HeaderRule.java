package com.burpext.sessionx.core;

/**
 * Represents a single header interception rule.
 *
 * Three modes (set by the user in the Config panel):
 *   REPLACE  — swap the header value with replacementValue
 *   REMOVE   — delete the header entirely from the modified request
 *   ADD      — add the header (even if not present in the original request)
 */
public class HeaderRule {

    public enum Mode {
        REPLACE("Replace"),
        REMOVE("Remove"),
        ADD("Add");

        private final String label;

        Mode(String label) { this.label = label; }

        @Override
        public String toString() { return label; }

        public static Mode fromString(String s) {
            for (Mode m : values()) {
                if (m.label.equalsIgnoreCase(s)) return m;
            }
            return REPLACE;
        }
    }

    private String  headerName;
    private Mode    mode;
    private String  replacementValue;   // used for REPLACE and ADD
    private boolean enabled;

    public HeaderRule(String headerName, Mode mode, String replacementValue) {
        this.headerName       = headerName;
        this.mode             = mode;
        this.replacementValue = replacementValue == null ? "" : replacementValue;
        this.enabled          = true;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public String getHeaderName()       { return headerName; }
    public void   setHeaderName(String v) { this.headerName = v; }

    public Mode getMode()               { return mode; }
    public void setMode(Mode m)         { this.mode = m; }

    public String getReplacementValue() { return replacementValue; }
    public void   setReplacementValue(String v) { this.replacementValue = v == null ? "" : v; }

    public boolean isEnabled()          { return enabled; }
    public void    setEnabled(boolean b){ this.enabled = b; }

    @Override
    public String toString() {
        return "[" + mode + "] " + headerName +
               (mode != Mode.REMOVE ? ": " + replacementValue : "");
    }
}

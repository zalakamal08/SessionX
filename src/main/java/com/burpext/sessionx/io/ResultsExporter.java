package com.burpext.sessionx.io;

import com.burpext.sessionx.core.HeaderRule;
import com.burpext.sessionx.core.HeaderRule.Mode;
import com.burpext.sessionx.core.TestResult;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for exporting results to CSV and importing/exporting configuration rules.
 * No external dependencies — uses plain Java IO.
 */
public class ResultsExporter {

    // ─── Results Export ───────────────────────────────────────────────────────

    /**
     * Exports all test results to a CSV file.
     * Columns: #, Method, URL, Orig.Status, Orig.Len, Mod.Status, Mod.Len, Mod.Result,
     *          Unauth.Status, Unauth.Len, Unauth.Result
     */
    public static void exportResultsCsv(List<TestResult> results, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("#,Method,URL,Orig.Status,Orig.Len,Mod.Status,Mod.Len,Mod.Result,Unauth.Status,Unauth.Len,Unauth.Result");
            for (TestResult r : results) {
                pw.printf("%d,%s,%s,%d,%d,%s,%s,%s,%s,%s,%s%n",
                        r.getId(),
                        esc(r.getMethod()),
                        esc(r.getUrl()),
                        r.getOrigStatus(),
                        r.getOrigLength(),
                        r.getModStatus() == -1 ? "-" : String.valueOf(r.getModStatus()),
                        r.getModLength() == -1 ? "-" : String.valueOf(r.getModLength()),
                        esc(r.getModVulnStatus().toString()),
                        r.getUnauthStatus() == -1 ? "-" : String.valueOf(r.getUnauthStatus()),
                        r.getUnauthLength() == -1 ? "-" : String.valueOf(r.getUnauthLength()),
                        esc(r.getUnauthVulnStatus().toString())
                );
            }
        }
    }

    // ─── Settings Export / Import ─────────────────────────────────────────────

    /**
     * Exports header rules to a CSV settings file.
     * Format per line: enabled|headerName|mode|value
     * Uses pipe '|' as separator since header values can contain commas.
     */
    public static void exportSettings(List<HeaderRule> rules, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("enabled|headerName|mode|value");
            for (HeaderRule r : rules) {
                pw.printf("%s|%s|%s|%s%n",
                        r.isEnabled(),
                        sanitize(r.getHeaderName()),
                        r.getMode().name(),
                        sanitize(r.getReplacementValue())
                );
            }
        }
    }

    /**
     * Imports header rules from a CSV settings file previously exported by exportSettings().
     */
    public static List<HeaderRule> importSettings(File file) throws IOException {
        List<HeaderRule> rules = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (first) { first = false; continue; } // skip header row
                String[] parts = line.split("\\|", 4);
                if (parts.length < 3) continue;
                boolean enabled = Boolean.parseBoolean(parts[0].trim());
                String  name    = parts[1].trim();
                Mode    mode    = Mode.fromString(parts[2].trim());
                String  value   = parts.length > 3 ? parts[3].trim() : "";
                HeaderRule rule = new HeaderRule(name, mode, value);
                rule.setEnabled(enabled);
                rules.add(rule);
            }
        }
        return rules;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Escape a value for CSV embedding: wrap in quotes if it contains commas/quotes/newlines. */
    private static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Strip pipe chars from settings fields to avoid format corruption. */
    private static String sanitize(String s) {
        return s == null ? "" : s.replace("|", "_");
    }
}

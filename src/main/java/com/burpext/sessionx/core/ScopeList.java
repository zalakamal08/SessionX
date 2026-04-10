package com.burpext.sessionx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The URL scope configuration for a session profile.
 * Holds a mode (whitelist vs blacklist) and the list of URL pattern rules.
 */
public class ScopeList {

    private ScopeMode       mode;
    private List<ScopeRule> rules;

    // ─── Jackson no-arg constructor ───────────────────────────────────────────
    public ScopeList() {
        this.mode  = ScopeMode.WHITELIST;
        this.rules = new ArrayList<>();
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public ScopeMode getMode()  { return mode; }
    public void      setMode(ScopeMode mode) { this.mode = mode; }

    public List<ScopeRule> getRules()  { return rules; }
    public void            setRules(List<ScopeRule> rules) { this.rules = rules; }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public void addRule(ScopeRule rule) {
        rules.add(rule);
    }

    public void removeRule(int index) {
        if (index >= 0 && index < rules.size()) rules.remove(index);
    }
}

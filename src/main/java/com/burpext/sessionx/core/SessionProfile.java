package com.burpext.sessionx.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Top-level configuration unit for one target engagement.
 *
 * A profile groups together:
 *  - the multi-step login sequence
 *  - token extraction + injection definitions
 *  - URL scope rules (whitelist or blacklist)
 *  - the error condition that triggers a token refresh
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionProfile {

    private String         id;
    private String         name;
    private String         targetHost;
    private boolean        enabled;

    private List<LoginStep>        loginSteps;
    private List<TokenDefinition>  tokens;
    private ScopeList              scope;
    private ErrorCondition         errorCondition;

    private Instant createdAt;

    // Jackson no-arg constructor
    public SessionProfile() {
        this.id             = UUID.randomUUID().toString();
        this.name           = "New Profile";
        this.targetHost     = "";
        this.enabled        = false;
        this.loginSteps     = new ArrayList<>();
        this.tokens         = new ArrayList<>();
        this.scope          = new ScopeList();
        this.errorCondition = new ErrorCondition();
        this.createdAt      = Instant.now();
    }

    // Getters / Setters

    public String         getId()             { return id; }
    public void           setId(String id)    { this.id = id; }

    public String         getName()           { return name; }
    public void           setName(String n)   { this.name = n; }

    public String         getTargetHost()     { return targetHost; }
    public void           setTargetHost(String h) { this.targetHost = h; }

    public boolean        isEnabled()         { return enabled; }
    public void           setEnabled(boolean e) { this.enabled = e; }

    public List<LoginStep>       getLoginSteps()     { return loginSteps; }
    public void                  setLoginSteps(List<LoginStep> s)  { this.loginSteps = s; }

    public List<TokenDefinition> getTokens()         { return tokens; }
    public void                  setTokens(List<TokenDefinition> t) { this.tokens = t; }

    public ScopeList             getScope()          { return scope; }
    public void                  setScope(ScopeList s) { this.scope = s; }

    public ErrorCondition        getErrorCondition() { return errorCondition; }
    public void                  setErrorCondition(ErrorCondition ec) { this.errorCondition = ec; }

    public Instant               getCreatedAt()      { return createdAt; }
    public void                  setCreatedAt(Instant i) { this.createdAt = i; }

    @Override
    public String toString() {
        return name + (enabled ? " [ACTIVE]" : " [DISABLED]");
    }
}

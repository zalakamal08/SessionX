package com.burpext.sessionx.core;

/**
 * Defines a single token within a session profile:
 * where to extract it from a login response and where to inject it into requests.
 *
 * loginStepIndex refers to which LoginStep's response to extract the token from (0-based).
 */
public class TokenDefinition {

    private TokenType     tokenType;
    private ExtractSource extractFrom;
    private String        extractRegex;     // regex with one capture group: the token value
    private int           loginStepIndex;   // which login step response to read from
    private TokenLocation injectLocation;
    private String        injectKey;        // header name / cookie name / JSON key / param name

    // ─── Jackson no-arg constructor ───────────────────────────────────────────
    public TokenDefinition() {
        this.tokenType      = TokenType.BEARER;
        this.extractFrom    = ExtractSource.RESPONSE_BODY_JSON;
        this.extractRegex   = "";
        this.loginStepIndex = 0;
        this.injectLocation = TokenLocation.AUTHORIZATION_HEADER;
        this.injectKey      = "Authorization";
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public TokenType     getTokenType()      { return tokenType; }
    public void          setTokenType(TokenType t)   { this.tokenType = t; }

    public ExtractSource getExtractFrom()    { return extractFrom; }
    public void          setExtractFrom(ExtractSource s) { this.extractFrom = s; }

    public String        getExtractRegex()   { return extractRegex; }
    public void          setExtractRegex(String r)   { this.extractRegex = r; }

    public int           getLoginStepIndex() { return loginStepIndex; }
    public void          setLoginStepIndex(int i)    { this.loginStepIndex = i; }

    public TokenLocation getInjectLocation() { return injectLocation; }
    public void          setInjectLocation(TokenLocation l) { this.injectLocation = l; }

    public String        getInjectKey()      { return injectKey; }
    public void          setInjectKey(String k)      { this.injectKey = k; }
}

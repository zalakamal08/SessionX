package com.burpext.sessionx.core;

/**
 * Defines a single token within a session profile:
 * where to extract it from a login response and where to inject it into requests.
 *
 * loginStepIndex refers to which LoginStep's response to extract from (0-based).
 *
 * variableName is the primary user-defined identifier (e.g. "access_token", "csrf").
 * It is used for cross-step referencing: {{step0:access_token}}.
 * If blank, the tokenType enum name is used as a fallback key.
 *
 * extractJsonPath (optional): a JSONPath expression (e.g. "$.data.tokens.access")
 * used instead of regex when extractFrom is RESPONSE_BODY_JSON and jsonPath is set.
 */
public class TokenDefinition {

    private String        variableName;       // user-defined free-text name (primary key)
    private TokenType     tokenType;          // injection type-hint (Bearer/Cookie/Custom…)
    private ExtractSource extractFrom;
    private String        extractRegex;
    private String        extractJsonPath;    // optional JSONPath (takes priority over regex for JSON)
    private int           loginStepIndex;
    private TokenLocation injectLocation;
    private String        injectKey;

    // Jackson no-arg constructor
    public TokenDefinition() {
        this.variableName   = "";
        this.tokenType      = TokenType.BEARER;
        this.extractFrom    = ExtractSource.RESPONSE_BODY_JSON;
        this.extractRegex   = "";
        this.extractJsonPath = "";
        this.loginStepIndex = 0;
        this.injectLocation = TokenLocation.AUTHORIZATION_HEADER;
        this.injectKey      = "Authorization";
    }

    // Getters / Setters

    public String        getVariableName()   { return variableName; }
    public void          setVariableName(String n)   { this.variableName = n; }

    /**
     * Returns the effective key used in TokenStore lookups.
     * Uses variableName when set, otherwise falls back to tokenType.name().
     */
    public String        effectiveKey() {
        return (variableName != null && !variableName.isBlank())
            ? variableName.trim()
            : (tokenType != null ? tokenType.name() : "UNKNOWN");
    }

    public TokenType     getTokenType()      { return tokenType; }
    public void          setTokenType(TokenType t)   { this.tokenType = t; }

    public ExtractSource getExtractFrom()    { return extractFrom; }
    public void          setExtractFrom(ExtractSource s) { this.extractFrom = s; }

    public String        getExtractRegex()   { return extractRegex; }
    public void          setExtractRegex(String r)   { this.extractRegex = r; }

    public String        getExtractJsonPath() { return extractJsonPath; }
    public void          setExtractJsonPath(String p) { this.extractJsonPath = p; }

    public int           getLoginStepIndex() { return loginStepIndex; }
    public void          setLoginStepIndex(int i)    { this.loginStepIndex = i; }

    public TokenLocation getInjectLocation() { return injectLocation; }
    public void          setInjectLocation(TokenLocation l) { this.injectLocation = l; }

    public String        getInjectKey()      { return injectKey; }
    public void          setInjectKey(String k)      { this.injectKey = k; }
}

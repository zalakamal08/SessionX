package com.burpext.sessionx.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One step in a multi-step login sequence.
 *
 * Steps execute in order. Values extracted in step N can be referenced
 * in step N+1's body/headers using the syntax:  {{stepN:tokenType}}
 * Example:  {{step0:CSRF}}  injects the CSRF token extracted in step 0.
 */
public class LoginStep {

    private String              method;
    private String              url;
    private Map<String, String> headers;
    private String              body;
    private String              label;

    // Jackson no-arg constructor
    public LoginStep() {
        this.method  = "POST";
        this.url     = "";
        this.headers = new LinkedHashMap<>();
        this.body    = "";
        this.label   = "Login Step";
    }

    // Getters / Setters

    public String              getMethod()  { return method; }
    public void                setMethod(String m)   { this.method = m; }

    public String              getUrl()     { return url; }
    public void                setUrl(String u)      { this.url = u; }

    public Map<String, String> getHeaders() { return headers; }
    public void                setHeaders(Map<String,String> h) { this.headers = h; }

    public String              getBody()    { return body; }
    public void                setBody(String b)     { this.body = b; }

    public String              getLabel()   { return label; }
    public void                setLabel(String l)    { this.label = l; }
}

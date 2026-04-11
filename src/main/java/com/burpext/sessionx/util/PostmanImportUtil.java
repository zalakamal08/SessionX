package com.burpext.sessionx.util;

import com.burpext.sessionx.core.LoginStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Postman Collection JSON file, flattening all requests into
 * a sequential series of LoginStep entries for SessionX profiles.
 */
public class PostmanImportUtil {

    private PostmanImportUtil() {}

    /**
     * Parses the given Postman collection file and returns a flat list
     * of all embedded HTTP requests as LoginStep objects.
     */
    public static List<LoginStep> parse(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);

        List<LoginStep> steps = new ArrayList<>();
        JsonNode items = root.get("item");
        if (items != null && items.isArray()) {
            processItems(items, steps);
        }
        return steps;
    }

    private static void processItems(JsonNode items, List<LoginStep> steps) {
        for (JsonNode item : items) {
            if (item.has("item")) {
                // It's a folder, traverse into it
                processItems(item.get("item"), steps);
            } else {
                // It's a request
                LoginStep step = buildStep(item);
                if (step != null) {
                    steps.add(step);
                }
            }
        }
    }

    private static LoginStep buildStep(JsonNode item) {
        try {
            LoginStep step = new LoginStep();

            // Name / Label
            String name = item.has("name") ? item.get("name").asText() : "Unnamed Request";
            step.setLabel(name);

            JsonNode request = item.get("request");
            if (request == null) return null;

            // Method
            step.setMethod(request.has("method") ? request.get("method").asText().toUpperCase() : "GET");

            // URL
            JsonNode urlNode = request.get("url");
            if (urlNode != null) {
                step.setUrl(parseUrl(urlNode));
            }

            // Headers
            Map<String, String> headers = new LinkedHashMap<>();
            JsonNode headerArray = request.get("header");
            if (headerArray != null && headerArray.isArray()) {
                for (JsonNode h : headerArray) {
                    if (h.has("disabled") && h.get("disabled").asBoolean()) continue;
                    String key = h.has("key") ? h.get("key").asText() : "";
                    String value = h.has("value") ? h.get("value").asText() : "";
                    if (!key.isEmpty()) {
                        headers.put(key, value);
                    }
                }
            }

            // Authentication Handing (Bearer/Basic mapped to Headers)
            if (request.has("auth")) {
                JsonNode auth = request.get("auth");
                String authType = auth.has("type") ? auth.get("type").asText().toLowerCase() : "none";
                
                if ("bearer".equals(authType) && auth.has("bearer") && auth.get("bearer").isArray()) {
                    for (JsonNode bearer : auth.get("bearer")) {
                        String tokenVal = bearer.has("value") ? bearer.get("value").asText() : "";
                        headers.put("Authorization", "Bearer " + tokenVal);
                        break;
                    }
                } else if ("basic".equals(authType) && auth.has("basic") && auth.get("basic").isArray()) {
                    String user = "", pass = "";
                    for (JsonNode entry : auth.get("basic")) {
                        String k = entry.has("key") ? entry.get("key").asText() : "";
                        String v = entry.has("value") ? entry.get("value").asText() : "";
                        if ("username".equals(k)) user = v;
                        if ("password".equals(k)) pass = v;
                    }
                    String b64 = java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                    headers.put("Authorization", "Basic " + b64);
                }
            }
            
            // Body Handling
            JsonNode body = request.get("body");
            if (body != null) {
                String mode = body.has("mode") ? body.get("mode").asText() : "";
                switch (mode) {
                    case "raw" -> {
                        step.setBody(body.has("raw") ? body.get("raw").asText() : "");
                        // infer Content-Type
                        JsonNode opts = body.get("options");
                        if (opts != null && opts.has("raw")) {
                            String lang = opts.get("raw").has("language") ? opts.get("raw").get("language").asText() : "";
                            if ("json".equalsIgnoreCase(lang)) {
                                headers.putIfAbsent("Content-Type", "application/json");
                            }
                        }
                    }
                    case "urlencoded" -> {
                        step.setBody(buildUrlEncoded(body.get("urlencoded")));
                        headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
                    }
                    case "formdata" -> {
                        String boundary = "----SessionXBoundary" + System.currentTimeMillis();
                        step.setBody(buildFormData(body.get("formdata"), boundary));
                        headers.putIfAbsent("Content-Type", "multipart/form-data; boundary=" + boundary);
                    }
                    case "graphql" -> {
                        JsonNode gql = body.get("graphql");
                        if (gql != null) {
                            Map<String, Object> gqlMap = new LinkedHashMap<>();
                            if (gql.has("query"))     gqlMap.put("query", gql.get("query").asText());
                            if (gql.has("variables")) gqlMap.put("variables", gql.get("variables").asText());
                            step.setBody(new ObjectMapper().writeValueAsString(gqlMap));
                        }
                        headers.putIfAbsent("Content-Type", "application/json");
                    }
                }
            }

            step.setHeaders(headers);
            return step;

        } catch (Exception e) {
            return null; // Skip invalid elements without failing block
        }
    }

    private static String parseUrl(JsonNode urlNode) {
        if (urlNode.isTextual()) {
            return urlNode.asText();
        }

        // Postman object-form URL
        String protocol = urlNode.has("protocol") ? urlNode.get("protocol").asText() : "https";
        
        StringBuilder hostBuilder = new StringBuilder();
        if (urlNode.has("host") && urlNode.get("host").isArray()) {
            Iterator<JsonNode> parts = urlNode.get("host").elements();
            while (parts.hasNext()) {
                hostBuilder.append(parts.next().asText());
                if (parts.hasNext()) hostBuilder.append(".");
            }
        } else {
            hostBuilder.append("{{host}}");
        }

        StringBuilder pathBuilder = new StringBuilder();
        if (urlNode.has("path") && urlNode.get("path").isArray()) {
            for (JsonNode seg : urlNode.get("path")) {
                String s = seg.isTextual() ? seg.asText() : (seg.has("value") ? seg.get("value").asText() : "");
                pathBuilder.append("/").append(s);
            }
        }
        if (pathBuilder.length() == 0) pathBuilder.append("/");

        StringBuilder query = new StringBuilder();
        if (urlNode.has("query") && urlNode.get("query").isArray()) {
            for (JsonNode q : urlNode.get("query")) {
                if (q.has("disabled") && q.get("disabled").asBoolean()) continue;
                String k = q.has("key") ? q.get("key").asText() : "";
                String v = q.has("value") ? q.get("value").asText() : "";
                if (!k.isEmpty()) {
                    if (query.length() > 0) query.append("&");
                    query.append(k).append("=").append(v);
                }
            }
        }

        String port = urlNode.has("port") && !urlNode.get("port").asText().isEmpty() 
            ? ":" + urlNode.get("port").asText() 
            : "";

        String fullUrl = protocol + "://" + hostBuilder.toString() + port + pathBuilder.toString();
        if (query.length() > 0) {
            fullUrl += "?" + query.toString();
        }

        return fullUrl;
    }

    private static String buildUrlEncoded(JsonNode node) {
        if (node == null || !node.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : node) {
            if (p.has("disabled") && p.get("disabled").asBoolean()) continue;
            String k = p.has("key") ? p.get("key").asText() : "";
            String v = p.has("value") ? p.get("value").asText() : "";
            if (sb.length() > 0) sb.append("&");
            sb.append(k).append("=").append(v);
        }
        return sb.toString();
    }

    private static String buildFormData(JsonNode node, String boundary) {
        if (node == null || !node.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : node) {
            if (p.has("disabled") && p.get("disabled").asBoolean()) continue;
            String key = p.has("key") ? p.get("key").asText() : "";
            boolean isFile = p.has("type") && "file".equals(p.get("type").asText());
            String value = p.has("value") ? p.get("value").asText() : "";
            sb.append("--").append(boundary).append("\r\n");
            
            if (isFile) {
                String fname = p.has("src") ? p.get("src").asText() : "file";
                sb.append("Content-Disposition: form-data; name=\"").append(key)
                  .append("\"; filename=\"").append(fname).append("\"\r\n");
                sb.append("Content-Type: application/octet-stream\r\n\r\n");
                sb.append("<... binary content ...>\r\n");
            } else {
                sb.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n");
                sb.append(value).append("\r\n");
            }
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }
}

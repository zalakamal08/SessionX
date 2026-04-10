package com.burpext.sessionx.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles token replacement inside HTTP request bodies.
 *
 * Supports:
 *  - JSON bodies    (parsed with Jackson, key-based replacement)
 *  - XML bodies     (parsed with javax.xml DOM, element text replacement)
 *  - Form-encoded   (URL-decoded key=value&key2=value2 strings)
 */
public class BodyInjector {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ─── JSON ─────────────────────────────────────────────────────────────────

    /**
     * Sets a key to a value in a JSON body string.
     * Supports nested keys using dot notation: "data.token".
     * Returns the modified JSON string, or the original if parsing fails.
     */
    public static String injectJson(String body, String key, String value) {
        if (body == null || body.isBlank()) return body;
        try {
            JsonNode root = mapper.readTree(body);
            if (!root.isObject()) return body;

            String[] parts = key.split("\\.", 2);
            if (parts.length == 2 && root.has(parts[0]) && root.get(parts[0]).isObject()) {
                // Nested key
                ((ObjectNode) root.get(parts[0])).put(parts[1], value);
            } else {
                // Top-level key
                ((ObjectNode) root).put(key, value);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return body; // Return unchanged on parse error
        }
    }

    // ─── Form-encoded ─────────────────────────────────────────────────────────

    /**
     * Replaces or adds a key=value pair in a URL-encoded form body.
     * Returns the modified form string.
     */
    public static String injectForm(String body, String key, String value) {
        if (body == null) return body;
        Map<String, String> params = parseForm(body);
        params.put(key, value);
        return buildForm(params);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (body.isBlank()) return result;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            result.put(k, v);
        }
        return result;
    }

    private static String buildForm(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    // ─── XML ──────────────────────────────────────────────────────────────────

    /**
     * Replaces the text content of the first matching element in an XML body.
     * Returns the modified XML string, or the original if parsing fails.
     */
    public static String injectXml(String body, String elementName, String value) {
        if (body == null || body.isBlank()) return body;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

            org.w3c.dom.NodeList nodes = doc.getElementsByTagName(elementName);
            if (nodes.getLength() > 0) {
                nodes.item(0).setTextContent(value);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return body; // Return unchanged on parse error
        }
    }
}

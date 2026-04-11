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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles token replacement inside HTTP request bodies.
 *
 * Supports:
 *  - JSON bodies      (parsed with Jackson, dot-notation key support)
 *  - Form-encoded     (URL-decoded key=value pairs)
 *  - XML bodies       (DOM element text replacement)
 *
 * FIX #2 (Destructive Parameter Parsing):
 *   - Form parsing now uses List<Map.Entry<String,String>> instead of Map.
 *   - This preserves ALL duplicate keys (e.g. checkbox=a&checkbox=b) and
 *     their exact original ordering. The old LinkedHashMap silently dropped
 *     duplicate keys, corrupting multi-value form submissions.
 *
 * Performance:
 *   - ObjectMapper is shared (thread-safe singleton) — avoids repeated init.
 *   - DocumentBuilderFactory XXE hardening retained.
 */
public class BodyInjector {

    // Reuse across calls — ObjectMapper is thread-safe after configuration
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // JSON
    // -------------------------------------------------------------------------

    /**
     * Sets a key to a value in a JSON body string.
     * Supports one level of dot notation: "data.token"
     * Returns the original body unchanged if parsing fails.
     */
    public static String injectJson(String body, String key, String value) {
        if (body == null || body.isBlank()) return body;
        try {
            JsonNode root = MAPPER.readTree(body);
            if (!root.isObject()) return body;

            String[] parts = key.split("\\.", 2);
            if (parts.length == 2
                    && root.has(parts[0])
                    && root.get(parts[0]).isObject()) {
                ((ObjectNode) root.get(parts[0])).put(parts[1], value);
            } else {
                ((ObjectNode) root).put(key, value);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return body; // fail-safe: return original
        }
    }

    // -------------------------------------------------------------------------
    // Form-encoded  (FIX #2)
    // -------------------------------------------------------------------------

    /**
     * Replaces or adds a key=value pair in a URL-encoded form body.
     *
     * Uses a List to preserve parameter order and duplicate keys.
     * If the key already exists, the FIRST occurrence is updated and all
     * subsequent duplicate keys are removed (standard replacement semantics).
     */
    public static String injectForm(String body, String key, String value) {
        if (body == null) return body;

        List<Map.Entry<String, String>> params = parseForm(body);

        // Replace first match; remove later duplicates of the same key
        boolean replaced = false;
        List<Map.Entry<String, String>> result = new ArrayList<>(params.size() + 1);
        for (Map.Entry<String, String> e : params) {
            if (e.getKey().equals(key)) {
                if (!replaced) {
                    result.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
                    replaced = true;
                }
                // Skip subsequent duplicates — consistent with browser behaviour
            } else {
                result.add(e);
            }
        }
        if (!replaced) {
            result.add(new AbstractMap.SimpleImmutableEntry<>(key, value));
        }

        return buildForm(result);
    }

    /**
     * Parses a URL-encoded body preserving all duplicate keys and original order.
     */
    private static List<Map.Entry<String, String>> parseForm(String body) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        if (body == null || body.isBlank()) return result;

        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k, v;
            if (eq < 0) {
                k = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                v = "";
            } else {
                k = URLDecoder.decode(pair.substring(0, eq),  StandardCharsets.UTF_8);
                v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
            result.add(new AbstractMap.SimpleImmutableEntry<>(k, v));
        }
        return result;
    }

    private static String buildForm(List<Map.Entry<String, String>> params) {
        StringBuilder sb = new StringBuilder(params.size() * 16);
        for (Map.Entry<String, String> e : params) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(),   StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // XML
    // -------------------------------------------------------------------------

    /**
     * Replaces the text content of the first matching element in an XML body.
     * XXE-hardened: DOCTYPE declarations throw a fatal parse error.
     */
    public static String injectXml(String body, String elementName, String value) {
        if (body == null || body.isBlank()) return body;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE hardening
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

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
            return body;
        }
    }
}

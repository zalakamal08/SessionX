package com.burpext.sessionx.util;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;

/**
 * JSONPath extraction helper using Jayway JsonPath library.
 *
 * Provides two modes:
 *   extract(json, path) — returns the matched value as a String, or null on miss/error.
 *   buildJsonPathFromKey(key) — generates a simple default JSONPath for a known JSON key.
 */
public class JsonPathUtil {

    private static final Configuration SAFE_CONFIG = Configuration.defaultConfiguration()
        .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    private JsonPathUtil() {}

    /**
     * Reads a JSONPath expression against a JSON string.
     * Returns the matched value coerced to String, or null if the path
     * did not match, the JSON was invalid, or an exception occurred.
     *
     * Example:
     *   extract("{\"access_token\":\"abc\"}", "$.access_token")  ->  "abc"
     */
    public static String extract(String json, String jsonPath) {
        if (json == null || json.isBlank() || jsonPath == null || jsonPath.isBlank()) return null;
        try {
            Object result = JsonPath.using(SAFE_CONFIG).parse(json).read(jsonPath);
            if (result == null) return null;
            return result.toString();
        } catch (PathNotFoundException | IllegalArgumentException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generates a simple top-level JSONPath from a bare key name.
     * e.g.  "access_token"  ->  "$.access_token"
     *
     * Used by the interactive dialog's auto-complete when the user selects a JSON field.
     */
    public static String buildJsonPathFromKey(String key) {
        if (key == null || key.isBlank()) return "";
        return "$." + key.trim();
    }

    /**
     * Derives a JSONPath expression by examining the surrounding context of a
     * selected substring within a (potentially nested) JSON body.
     *
     * Tries to find the nearest enclosing "key": "VALUE" pair and builds a
     * dot-notation path like "$.data.token".
     *
     * Returns a simple "$.key" guess when full path resolution is not possible.
     */
    public static String deriveJsonPath(String json, String selectedValue) {
        if (json == null || selectedValue == null || selectedValue.isBlank()) return "";
        try {
            // Walk through all possible paths returned by JsonPath scanning
            // and find which one returns a value that matches the selected text.
            Object parsed = Configuration.defaultConfiguration().jsonProvider().parse(json);
            return findPathForValue(parsed, selectedValue, "$");
        } catch (Exception e) {
            return "";
        }
    }

    // --- private recursive path walker ---

    @SuppressWarnings("unchecked")
    private static String findPathForValue(Object node, String target, String currentPath) {
        if (node instanceof java.util.Map) {
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) node;
            for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                String childPath = currentPath + "." + entry.getKey();
                String found = findPathForValue(entry.getValue(), target, childPath);
                if (found != null) return found;
            }
        } else if (node instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                String found = findPathForValue(list.get(i), target, currentPath + "[" + i + "]");
                if (found != null) return found;
            }
        } else if (node != null && node.toString().equals(target)) {
            return currentPath;
        }
        return null;
    }
}

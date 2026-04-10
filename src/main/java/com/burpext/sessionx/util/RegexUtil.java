package com.burpext.sessionx.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe compiled regex pattern cache.
 * Avoids recompiling the same pattern on every HTTP request.
 */
public class RegexUtil {

    private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();

    private RegexUtil() {}

    /**
     * Extracts the value of the first capture group from the input string.
     * Returns null if no match or the pattern is blank.
     */
    public static String extract(String input, String regex) {
        if (regex == null || regex.isBlank() || input == null) return null;
        Pattern p = CACHE.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.DOTALL));
        Matcher m = p.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Tests if the regex matches anywhere in the input.
     */
    public static boolean matches(String input, String regex) {
        if (regex == null || regex.isBlank() || input == null) return false;
        Pattern p = CACHE.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.DOTALL));
        return p.matcher(input).find();
    }
}

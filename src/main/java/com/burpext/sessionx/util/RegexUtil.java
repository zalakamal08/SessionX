package com.burpext.sessionx.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe compiled regex pattern cache.
 * Avoids recompiling the same pattern on every HTTP request.
 *
 * Also provides context-aware regex generation for the interactive
 * Login Step Builder dialog (generateFromContext).
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

    // -------------------------------------------------------------------------
    // Context-aware regex generation (used by Login Step Builder dialog)
    // -------------------------------------------------------------------------

    /**
     * Auto-generates a regex pattern for extracting {@code selectedValue} from
     * {@code fullText}, using the selection position to identify context.
     *
     * Detection priority:
     *  1. JSON string value:   "key": "VALUE"       ->  "key"\s*:\s*"([^"]+)"
     *  2. JSON numeric value:  "key": 123           ->  "key"\s*:\s*(\d+)
     *  3. Set-Cookie header:   key=VALUE;           ->  key=([^;]+)
     *  4. Header value:        Header-Name: VALUE   ->  Header-Name:\s*(.+)
     *  5. Fallback:            literal escaped match
     *
     * @param fullText      The entire response string (headers + body)
     * @param selStart      Selection start offset in fullText
     * @param selEnd        Selection end offset in fullText
     * @return A regex string with exactly one capture group wrapping the selected value
     */
    public static String generateFromContext(String fullText, int selStart, int selEnd) {
        if (fullText == null || selStart < 0 || selEnd > fullText.length() || selStart >= selEnd) {
            return "";
        }

        String selected = fullText.substring(selStart, selEnd);
        String escaped  = Pattern.quote(selected);

        // Window of text before and after the selection for context detection
        int    winStart  = Math.max(0, selStart - 120);
        int    winEnd    = Math.min(fullText.length(), selEnd + 30);
        String before    = fullText.substring(winStart, selStart);
        String afterSnip = fullText.substring(selEnd, winEnd);

        // 1. JSON string value: look for "someKey": "SELECTED"
        Pattern jsonStr = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"$");
        Matcher m = jsonStr.matcher(before);
        if (m.find()) {
            String key = Pattern.quote(m.group(1));
            return "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        }

        // 2. JSON numeric value: "someKey": 123
        Pattern jsonNum = Pattern.compile("\"([^\"]+)\"\\s*:\\s*$");
        m = jsonNum.matcher(before);
        if (m.find() && selected.matches("-?\\d+(\\.\\d+)?")) {
            String key = Pattern.quote(m.group(1));
            return "\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)";
        }

        // 3. Set-Cookie: cookieName=SELECTED
        Pattern cookie = Pattern.compile("([\\w-]+)=$");
        m = cookie.matcher(before);
        if (m.find() && before.toLowerCase().contains("set-cookie")) {
            String cookieKey = Pattern.quote(m.group(1));
            return cookieKey + "=([^;\\s]+)";
        }

        // 4. HTTP response header: Header-Name: SELECTED  (on its own line)
        Pattern header = Pattern.compile("([\\w-]+):\\s?$");
        m = header.matcher(before);
        if (m.find()) {
            String headerName = Pattern.quote(m.group(1));
            return headerName + ":\\s*(.+)";
        }

        // 5. Fallback: literal escaped match, value captured
        return "(" + escaped + ")";
    }
}


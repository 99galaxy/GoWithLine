package com.gowithline.mock.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small semantic-version helper for the "check for updates" action.
 */
public final class VersionUtils {
    /** First version-like sequence, e.g. "release-1.0" or "GoWithLine v1.0" -> "1.0". */
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)*");

    private VersionUtils() {
    }

    /** Returns &lt;0, 0, or &gt;0 when {@code left} is older, equal, or newer. */
    public static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return av < bv ? -1 : 1;
            }
        }
        return 0;
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return new int[]{0};
        }
        // Extract the version core from the raw tag so word prefixes survive
        // ("release-1.0" must not parse to 0) while pre-release/build
        // suffixes are dropped ("1.0.0-beta+3" -> "1.0.0").
        Matcher matcher = VERSION_PATTERN.matcher(
                version.trim().toLowerCase(Locale.US));
        if (!matcher.find()) {
            return new int[]{0};
        }
        String[] parts = matcher.group().split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int value = 0;
            int j = 0;
            while (j < parts[i].length() && Character.isDigit(parts[i].charAt(j))) {
                value = value * 10 + (parts[i].charAt(j) - '0');
                j++;
            }
            result[i] = value;
        }
        return result;
    }
}

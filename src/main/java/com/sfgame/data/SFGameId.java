package com.sfgame.data;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared validation for SFGame-owned identifiers. */
public final class SFGameId {
    private static final Pattern STANDARD = Pattern.compile("[a-z0-9][a-z0-9_]{0,31}");
    private static final Pattern CLASS = Pattern.compile("[a-z0-9][a-z0-9_]{0,63}");

    public static String normalize(String value) {
        return normalize(value, STANDARD, "SFGame");
    }

    public static String normalizeClass(String value) {
        return normalize(value, CLASS, "class");
    }

    public static boolean isValid(String value) {
        return matches(value, STANDARD);
    }

    public static boolean isValidClass(String value) {
        return matches(value, CLASS);
    }

    private static String normalize(String value, Pattern pattern, String type) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + type + " id: " + value);
        }
        return normalized;
    }

    private static boolean matches(String value, Pattern pattern) {
        return value != null && pattern.matcher(value.toLowerCase(Locale.ROOT)).matches();
    }

    private SFGameId() {
    }
}

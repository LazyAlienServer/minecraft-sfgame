package com.sfgame.data;

public enum CtfVariant {
    CLASSIC,
    ASSAULT,
    TERRITORY;

    public static CtfVariant fromId(String value) {
        if (value == null) return CLASSIC;
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return CLASSIC; }
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}

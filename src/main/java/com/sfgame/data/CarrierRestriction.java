package com.sfgame.data;

public enum CarrierRestriction {
    NORMAL,
    MOVEMENT_LIMITED,
    NO_WEAPONS;

    public static CarrierRestriction fromId(String value) {
        if (value == null) return NORMAL;
        try { return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return NORMAL; }
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}

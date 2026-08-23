package com.sfgame.game;

import com.sfgame.data.SFGameId;

import java.util.Locale;

/** Canonical parent reference for map-owned rule and class documents. */
public record MapParentRef(String modeId, String mapId) {
    public static MapParentRef parse(String value, String currentMode) {
        String normalizedMode = normalizeMode(currentMode);
        String raw = value == null || value.isBlank() ? "base" : value.trim().toLowerCase(Locale.ROOT);
        int separator = raw.indexOf('/');
        if (separator < 0) return new MapParentRef(normalizedMode, normalizeMap(raw));
        if (separator == 0 || separator == raw.length() - 1 || raw.indexOf('/', separator + 1) >= 0) {
            throw new IllegalArgumentException("Invalid map parent: " + value);
        }
        return new MapParentRef(normalizeMode(raw.substring(0, separator)), normalizeMap(raw.substring(separator + 1)));
    }

    public String canonical() {
        return modeId + "/" + mapId;
    }

    public boolean isBase() {
        return "base".equals(mapId);
    }

    private static String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (GameModeRegistry.get(normalized).isEmpty()) throw new IllegalArgumentException("Unknown game mode: " + value);
        return normalized;
    }

    private static String normalizeMap(String value) {
        if ("base".equals(value)) return value;
        if (!SFGameId.isValid(value)) throw new IllegalArgumentException("Invalid map id: " + value);
        return SFGameId.normalize(value);
    }
}

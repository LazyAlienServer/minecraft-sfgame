package com.sfgame.data;

import java.util.Locale;

/** Controls which blocks inside a map build box belong to its snapshot. */
public enum MapSnapshotMode {
    /** Save, clear and restore only blocks present in the map allowlist. */
    ALLOWLIST,
    /** Save and restore the complete selected build box. */
    FULL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MapSnapshotMode byId(String id) {
        if (id != null) {
            for (MapSnapshotMode mode : values()) {
                if (mode.id().equals(id.toLowerCase(Locale.ROOT))) return mode;
            }
        }
        return ALLOWLIST;
    }

    public static MapSnapshotMode parse(String id) {
        if (id != null) {
            for (MapSnapshotMode mode : values()) {
                if (mode.id().equals(id.trim().toLowerCase(Locale.ROOT))) return mode;
            }
        }
        throw new IllegalArgumentException("Expected allowlist or full");
    }
}

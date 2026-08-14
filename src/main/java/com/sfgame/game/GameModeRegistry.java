package com.sfgame.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class GameModeRegistry {
    public static final String TEAM_DEATHMATCH = "tdm";
    public static final String DOMINATION = "domination";
    public static final String BREAKTHROUGH = "breakthrough";
    private static final Map<String, GameModeDefinition> MODES = new LinkedHashMap<>();

    static {
        register(new GameModeDefinition(TEAM_DEATHMATCH, "Team Deathmatch"));
        register(new GameModeDefinition(DOMINATION, "Domination"));
        register(new GameModeDefinition(BREAKTHROUGH, "Breakthrough"));
    }

    public static void register(GameModeDefinition mode) {
        if (!mode.id().matches("[a-z][a-z0-9_]{0,31}")) {
            throw new IllegalArgumentException("Invalid game mode id: " + mode.id());
        }
        if (MODES.putIfAbsent(mode.id(), mode) != null) {
            throw new IllegalArgumentException("Duplicate game mode id: " + mode.id());
        }
    }

    public static Optional<GameModeDefinition> get(String id) {
        return Optional.ofNullable(MODES.get(id));
    }

    public static Collection<GameModeDefinition> all() {
        return java.util.Collections.unmodifiableCollection(MODES.values());
    }

    private GameModeRegistry() {}
}

package com.sfgame.game;

import com.sfgame.data.SFGameId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class GameModeRegistry {
    public static final String TEAM_DEATHMATCH = "tdm";
    public static final String DOMINATION = "domination";
    public static final String BREAKTHROUGH = "breakthrough";
    public static final String CAPTURE_THE_FLAG = "ctf";
    private static final Map<String, GameModeDefinition> MODES = new LinkedHashMap<>();

    static {
        register(new GameModeDefinition(TEAM_DEATHMATCH, "Team Deathmatch"));
        register(new GameModeDefinition(DOMINATION, "Domination"));
        register(new GameModeDefinition(BREAKTHROUGH, "Breakthrough"));
        register(new GameModeDefinition(CAPTURE_THE_FLAG, "Capture the Flag"));
    }

    public static void register(GameModeDefinition mode) {
        if (!SFGameId.isValid(mode.id())) {
            throw new IllegalArgumentException("Invalid game mode id: " + mode.id());
        }
        String id = SFGameId.normalize(mode.id());
        GameModeDefinition normalized = id.equals(mode.id()) ? mode : new GameModeDefinition(id, mode.displayName());
        if (MODES.putIfAbsent(id, normalized) != null) {
            throw new IllegalArgumentException("Duplicate game mode id: " + mode.id());
        }
    }

    public static Optional<GameModeDefinition> get(String id) {
        return SFGameId.isValid(id) ? Optional.ofNullable(MODES.get(SFGameId.normalize(id))) : Optional.empty();
    }

    public static Collection<GameModeDefinition> all() {
        return java.util.Collections.unmodifiableCollection(MODES.values());
    }

    private GameModeRegistry() {}
}

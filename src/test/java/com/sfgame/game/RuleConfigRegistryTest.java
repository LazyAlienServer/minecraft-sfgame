package com.sfgame.game;

import com.sfgame.data.SFGameSavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuleConfigRegistryTest {
    @TempDir Path directory;

    @Test
    void createsProfilesFromLegacyRulesAndPersistsMapOverrides() {
        SFGameSavedData data = new SFGameSavedData();
        data.rules(GameModeRegistry.TEAM_DEATHMATCH).scoreLimit(66);
        RuleConfigRegistry registry = new RuleConfigRegistry(directory);

        assertTrue(registry.reload(data).isEmpty());
        assertEquals(66, registry.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());

        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "arena_a", "scoreLimit", 75);
        registry.setParent(GameModeRegistry.TEAM_DEATHMATCH, "arena_b", "arena_a");
        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "arena_b", "timeLimitSeconds", 900);

        RuleConfigRegistry restored = new RuleConfigRegistry(directory);
        assertTrue(restored.reload(data).isEmpty());
        assertEquals(75, restored.rules(GameModeRegistry.TEAM_DEATHMATCH, "arena_b",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
        assertEquals(900, restored.rules(GameModeRegistry.TEAM_DEATHMATCH, "arena_b",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).timeLimitSeconds());
    }

    @Test
    void resetClearsOnlyCurrentMapOverridesAndKeepsItsParent() {
        SFGameSavedData data = new SFGameSavedData();
        RuleConfigRegistry registry = new RuleConfigRegistry(directory);
        assertTrue(registry.reload(data).isEmpty());
        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "parent", "scoreLimit", 80);
        registry.setParent(GameModeRegistry.TEAM_DEATHMATCH, "child", "parent");
        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "child", "scoreLimit", 90);

        registry.resetMap(GameModeRegistry.TEAM_DEATHMATCH, "child");

        assertEquals("parent", registry.parent(GameModeRegistry.TEAM_DEATHMATCH, "child"));
        assertEquals(80, registry.rules(GameModeRegistry.TEAM_DEATHMATCH, "child",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
    }

    @Test
    void rejectsMapInheritanceCycles() {
        SFGameSavedData data = new SFGameSavedData();
        RuleConfigRegistry registry = new RuleConfigRegistry(directory);
        assertTrue(registry.reload(data).isEmpty());
        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "a", "scoreLimit", 60);
        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "b", "scoreLimit", 70);
        registry.setParent(GameModeRegistry.TEAM_DEATHMATCH, "b", "a");

        assertThrows(IllegalArgumentException.class,
                () -> registry.setParent(GameModeRegistry.TEAM_DEATHMATCH, "a", "b"));
    }
}

package com.sfgame.game;

import com.sfgame.data.SFGameSavedData;
import com.sfgame.data.MapSnapshotMode;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.CarrierRestriction;
import com.sfgame.data.PointActivationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void lazilyLoadsCurrentModeWhenGuiMutatesBeforeExplicitReload() {
        SFGameSavedData data = new SFGameSavedData();
        RuleConfigRegistry seed = new RuleConfigRegistry(directory);
        assertTrue(seed.reload(data).isEmpty());

        RuleConfigRegistry lazy = new RuleConfigRegistry(directory);
        lazy.setInt(GameModeRegistry.TEAM_DEATHMATCH, "default", "scoreLimit", 77);

        assertEquals(77, lazy.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
    }

    @Test
    void malformedModeDoesNotUnloadOtherValidProfiles() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        RuleConfigRegistry seed = new RuleConfigRegistry(directory);
        assertTrue(seed.reload(data).isEmpty());
        Files.writeString(directory.resolve(GameModeRegistry.DOMINATION + ".json"), "{ broken json");

        RuleConfigRegistry registry = new RuleConfigRegistry(directory);
        assertFalse(registry.reload(data).isEmpty());
        registry.setInt(GameModeRegistry.TEAM_DEATHMATCH, "default", "scoreLimit", 88);

        assertEquals(88, registry.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
    }

    @Test
    void snapshotModeDefaultsToAllowlistAndSupportsMapInheritance() {
        SFGameSavedData data = new SFGameSavedData();
        RuleConfigRegistry registry = new RuleConfigRegistry(directory);
        assertTrue(registry.reload(data).isEmpty());
        assertEquals(MapSnapshotMode.ALLOWLIST, registry.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).mapSnapshotMode());

        registry.setString(GameModeRegistry.TEAM_DEATHMATCH, "parent", "mapSnapshotMode", "full");
        registry.setParent(GameModeRegistry.TEAM_DEATHMATCH, "child", "parent");

        assertEquals(MapSnapshotMode.FULL, registry.rules(GameModeRegistry.TEAM_DEATHMATCH, "child",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).mapSnapshotMode());
        assertThrows(IllegalArgumentException.class, () -> registry.setString(
                GameModeRegistry.TEAM_DEATHMATCH, "child", "mapSnapshotMode", "unknown"));
    }

    @Test
    void modeOptionsArePerMapRulesAndSupportInheritance() {
        SFGameSavedData data = new SFGameSavedData();
        RuleConfigRegistry registry = new RuleConfigRegistry(directory);
        assertTrue(registry.reload(data).isEmpty());

        registry.setString(GameModeRegistry.DOMINATION, "sync_parent", "dominationStrategy", "sync");
        registry.setParent(GameModeRegistry.DOMINATION, "child", "sync_parent");
        assertEquals(PointActivationStrategy.SYNC, registry.rules(GameModeRegistry.DOMINATION, "child",
                data.rules(GameModeRegistry.DOMINATION)).dominationStrategy());

        registry.setString(GameModeRegistry.BREAKTHROUGH, "arena", "breakthroughVariant", "captain");
        registry.setInt(GameModeRegistry.BREAKTHROUGH, "arena", "breakthroughLegs", 2);
        registry.setString(GameModeRegistry.BREAKTHROUGH, "arena", "breakthroughAttacker", "yellow");
        assertEquals(BreakthroughVariant.CAPTAIN, registry.rules(GameModeRegistry.BREAKTHROUGH, "arena",
                data.rules(GameModeRegistry.BREAKTHROUGH)).breakthroughVariant());
        assertEquals(2, registry.rules(GameModeRegistry.BREAKTHROUGH, "arena",
                data.rules(GameModeRegistry.BREAKTHROUGH)).breakthroughLegs());
        assertEquals(TeamSide.YELLOW, registry.rules(GameModeRegistry.BREAKTHROUGH, "arena",
                data.rules(GameModeRegistry.BREAKTHROUGH)).breakthroughAttacker());

        registry.setString(GameModeRegistry.CAPTURE_THE_FLAG, "arena", "ctfCarrierRestriction", "no_weapons");
        assertEquals(CarrierRestriction.NO_WEAPONS, registry.rules(GameModeRegistry.CAPTURE_THE_FLAG, "arena",
                data.rules(GameModeRegistry.CAPTURE_THE_FLAG)).ctfCarrierRestriction());
        assertThrows(IllegalArgumentException.class, () -> registry.setString(
                GameModeRegistry.TEAM_DEATHMATCH, "arena", "ctfVariant", "classic"));
    }
}

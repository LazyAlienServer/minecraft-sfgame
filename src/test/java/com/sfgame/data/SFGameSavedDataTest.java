package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SFGameSavedDataTest {
    private static final ArenaPosition LOBBY = new ArenaPosition("minecraft:overworld", 1, 64, 2, 90, 0);
    private static final ArenaPosition RED = new ArenaPosition("minecraft:overworld", 10, 65, 10, 0, 0);
    private static final ArenaPosition BLUE = new ArenaPosition("minecraft:the_nether", -10, 70, -10, 180, 0);

    @Test
    void migratesLegacySingleArenaToTdmDefault() {
        CompoundTag legacy = new CompoundTag();
        legacy.put("Lobby", LOBBY.save());
        legacy.put("RedSpawn", RED.save());
        legacy.put("BlueSpawn", BLUE.save());

        SFGameSavedData data = SFGameSavedData.load(legacy);

        assertEquals("tdm", data.selectedMode());
        assertEquals("default", data.selectedMap());
        assertTrue(data.isArenaConfigured());
        assertEquals(LOBBY, data.lobby());
        assertEquals(List.of(RED), data.spawns(com.sfgame.game.TeamSide.RED));
        assertEquals(List.of(BLUE), data.spawns(com.sfgame.game.TeamSide.BLUE));
    }

    @Test
    void persistsMultipleMapsAndSelection() {
        SFGameSavedData source = new SFGameSavedData();
        source.lobby(LOBBY);
        source.addSpawn(com.sfgame.game.TeamSide.RED, RED);
        source.addSpawn(com.sfgame.game.TeamSide.BLUE, BLUE);
        assertTrue(source.createMap("desert"));
        source.lobby(RED);
        source.addSpawn(com.sfgame.game.TeamSide.RED, BLUE);
        source.addSpawn(com.sfgame.game.TeamSide.BLUE, LOBBY);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertEquals("desert", restored.selectedMap());
        assertEquals(2, restored.maps().size());
        assertTrue(restored.isArenaConfigured());
        assertEquals(RED, restored.lobby());
        assertEquals(1, restored.spawns(com.sfgame.game.TeamSide.RED).size());
        assertEquals(1, restored.spawns(com.sfgame.game.TeamSide.BLUE).size());
        assertTrue(restored.selectMap("default"));
        assertEquals(LOBBY, restored.lobby());
    }

    @Test
    void refusesToRemoveLastMapAndFallsBackAfterRemovingSelectedMap() {
        SFGameSavedData data = new SFGameSavedData();
        assertFalse(data.removeMap("default"));
        assertTrue(data.createMap("arena2"));
        assertTrue(data.removeMap("arena2"));
        assertEquals("default", data.selectedMap());
        assertNotNull(data.activeMap());
    }

    @Test
    void savesMultipleTeamSpawnsAndAllowsIndexedRemoval() {
        SFGameSavedData source = new SFGameSavedData();
        source.addSpawn(com.sfgame.game.TeamSide.RED, RED);
        source.addSpawn(com.sfgame.game.TeamSide.RED, LOBBY);
        source.addSpawn(com.sfgame.game.TeamSide.BLUE, BLUE);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertEquals(List.of(RED, LOBBY), restored.spawns(com.sfgame.game.TeamSide.RED));
        assertTrue(restored.removeSpawn(com.sfgame.game.TeamSide.RED, 0));
        assertEquals(List.of(LOBBY), restored.spawns(com.sfgame.game.TeamSide.RED));
        assertFalse(restored.removeSpawn(com.sfgame.game.TeamSide.RED, 5));
    }

    @Test
    void enablesTwoToFourTeamsFromConfiguredSpawnLists() {
        SFGameSavedData data = new SFGameSavedData();
        data.lobby(LOBBY);
        data.addSpawn(com.sfgame.game.TeamSide.RED, RED);
        assertFalse(data.isArenaConfigured());
        data.addSpawn(com.sfgame.game.TeamSide.BLUE, BLUE);
        assertTrue(data.isArenaConfigured());
        assertEquals(List.of(com.sfgame.game.TeamSide.RED, com.sfgame.game.TeamSide.BLUE), data.enabledTeams());
        data.addSpawn(com.sfgame.game.TeamSide.YELLOW, LOBBY);
        data.addSpawn(com.sfgame.game.TeamSide.GREEN, RED);
        assertEquals(List.of(com.sfgame.game.TeamSide.RED, com.sfgame.game.TeamSide.BLUE,
                com.sfgame.game.TeamSide.YELLOW, com.sfgame.game.TeamSide.GREEN), data.enabledTeams());
    }

    @Test
    void persistsYellowAndGreenTeamBindingsAndSpawns() {
        SFGameSavedData source = new SFGameSavedData();
        source.yellowTeam("custom_yellow");
        source.greenTeam("custom_green");
        source.addSpawn(com.sfgame.game.TeamSide.YELLOW, LOBBY);
        source.addSpawn(com.sfgame.game.TeamSide.GREEN, BLUE);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertEquals("custom_yellow", restored.yellowTeam());
        assertEquals("custom_green", restored.greenTeam());
        assertEquals(List.of(LOBBY), restored.spawns(com.sfgame.game.TeamSide.YELLOW));
        assertEquals(List.of(BLUE), restored.spawns(com.sfgame.game.TeamSide.GREEN));
    }
}

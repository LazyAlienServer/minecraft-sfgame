package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

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
        assertEquals(RED, data.redSpawn());
        assertEquals(BLUE, data.blueSpawn());
    }

    @Test
    void persistsMultipleMapsAndSelection() {
        SFGameSavedData source = new SFGameSavedData();
        source.lobby(LOBBY);
        source.redSpawn(RED);
        source.blueSpawn(BLUE);
        assertTrue(source.createMap("desert"));
        source.lobby(RED);
        source.redSpawn(BLUE);
        source.blueSpawn(LOBBY);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertEquals("desert", restored.selectedMap());
        assertEquals(2, restored.maps().size());
        assertTrue(restored.isArenaConfigured());
        assertEquals(RED, restored.lobby());
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
}

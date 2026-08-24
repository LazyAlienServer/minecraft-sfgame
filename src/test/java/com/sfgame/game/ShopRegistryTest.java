package com.sfgame.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShopRegistryTest {
    @TempDir Path directory;

    @Test
    void loadsIndependentDefaultsForEveryEconomyMode() {
        ShopRegistry registry = new ShopRegistry(false);
        registry.useConfigRoot(directory);

        assertTrue(registry.reload().isEmpty());
        assertEquals(20, registry.items(GameModeRegistry.BREAKTHROUGH).size());
        assertEquals(20, registry.items(GameModeRegistry.CAPTURE_THE_FLAG).size());
        assertEquals(20, registry.items(GameModeRegistry.DOMINATION).size());
        assertEquals("minecraft:golden_apple",
                registry.item(GameModeRegistry.CAPTURE_THE_FLAG, "medkit").item());
    }

    @Test
    void keepsEachModesLastKnownGoodItemsOnInvalidReload() throws Exception {
        ShopRegistry registry = new ShopRegistry(false);
        registry.useConfigRoot(directory);
        assertTrue(registry.reload().isEmpty());

        Files.writeString(registry.path(GameModeRegistry.BREAKTHROUGH), """
                {"items":[{"id":"breakthrough_only","name":"Only","icon":"minecraft:bread","price":1,
                "item":"minecraft:bread","count":1,"nbt":""}]}
                """);
        assertTrue(registry.reload().isEmpty());
        assertEquals(1, registry.items(GameModeRegistry.BREAKTHROUGH).size());
        assertNull(registry.item(GameModeRegistry.DOMINATION, "breakthrough_only"));

        Files.writeString(registry.path(GameModeRegistry.CAPTURE_THE_FLAG), """
                {"items":[
                {"id":"duplicate","item":"minecraft:bread","count":1,"price":1},
                {"id":"duplicate","item":"minecraft:apple","count":1,"price":1}]}
                """);
        assertFalse(registry.reload().isEmpty());
        assertEquals(20, registry.items(GameModeRegistry.CAPTURE_THE_FLAG).size());
        assertEquals(1, registry.items(GameModeRegistry.BREAKTHROUGH).size());

        Files.writeString(registry.path(GameModeRegistry.DOMINATION), """
                {"items":[{"id":"broken","item":"not an item","count":1,"price":1}]}
                """);
        assertFalse(registry.reload().isEmpty());
        assertEquals(20, registry.items(GameModeRegistry.DOMINATION).size());
    }
}

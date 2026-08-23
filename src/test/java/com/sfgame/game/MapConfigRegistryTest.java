package com.sfgame.game;

import com.sfgame.classsystem.ClassRegistry;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.SFGameSavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapConfigRegistryTest {
    @TempDir Path directory;

    @Test
    void storesEachMapInItsModeFolderAndReloadsTopology() throws Exception {
        SFGameSavedData source = new SFGameSavedData();
        source.lobby(new ArenaPosition("minecraft:overworld", 1, 64, 2, 90, 0));
        source.addSpawn(TeamSide.RED, new ArenaPosition("minecraft:overworld", 10, 65, 10, 0, 0));
        source.addSpawn(TeamSide.BLUE, new ArenaPosition("minecraft:overworld", -10, 65, -10, 180, 0));

        MapConfigRegistry registry = new MapConfigRegistry();
        registry.useConfigRoot(directory);
        assertTrue(registry.reload(source).isEmpty());

        Path mapPath = directory.resolve("maps").resolve("tdm").resolve("default").resolve("map.json");
        assertTrue(Files.isRegularFile(mapPath));
        assertTrue(Files.readString(mapPath).contains("\"redSpawns\""));

        SFGameSavedData restored = new SFGameSavedData();
        MapConfigRegistry second = new MapConfigRegistry();
        second.useConfigRoot(directory);
        assertTrue(second.reload(restored).isEmpty());
        assertEquals(1, restored.spawns(TeamSide.RED).size());
        assertEquals(1, restored.spawns(TeamSide.BLUE).size());
    }

    @Test
    void roundTripsModeSpecificTopologyInsideMapJson() {
        SFGameSavedData source = new SFGameSavedData();
        assertTrue(source.selectMode(GameModeRegistry.DOMINATION));
        source.lobby(new com.sfgame.data.ArenaPosition("minecraft:overworld", 0, 64, 0, 0, 0));
        source.addSpawn(TeamSide.RED, new com.sfgame.data.ArenaPosition("minecraft:overworld", 1, 64, 1, 0, 0));
        source.addSpawn(TeamSide.BLUE, new com.sfgame.data.ArenaPosition("minecraft:overworld", -1, 64, -1, 180, 0));
        source.activeMap().domination().add(new com.sfgame.data.CapturePointDefinition("a",
                new com.sfgame.data.SquareCaptureRegion("minecraft:overworld", 0, 0, 3, null, null), 1));

        MapConfigRegistry registry = new MapConfigRegistry();
        registry.useConfigRoot(directory);
        assertTrue(registry.reload(source).isEmpty());

        SFGameSavedData restored = new SFGameSavedData();
        MapConfigRegistry second = new MapConfigRegistry();
        second.useConfigRoot(directory);
        assertTrue(second.reload(restored).isEmpty());
        assertTrue(restored.selectMode(GameModeRegistry.DOMINATION));
        assertEquals("a", restored.activeMap().domination().points().get(0).id());
    }

    @Test
    void keepsRulesAndLongClassProfilesBesideMapJson() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        MapConfigRegistry maps = new MapConfigRegistry();
        maps.useConfigRoot(directory);
        assertTrue(maps.reload(data).isEmpty());

        RuleConfigRegistry rules = new RuleConfigRegistry();
        rules.useConfigRoot(directory);
        assertTrue(rules.reload(data).isEmpty());
        rules.setInt(GameModeRegistry.TEAM_DEATHMATCH, "default", "scoreLimit", 77);

        ClassRegistry classes = new ClassRegistry();
        classes.useConfigRoot(directory);
        assertTrue(classes.reload(data).isEmpty());

        Path mapDirectory = directory.resolve("maps").resolve("tdm").resolve("default");
        assertTrue(Files.isRegularFile(mapDirectory.resolve("map.json")));
        assertTrue(Files.isRegularFile(mapDirectory.resolve("classes.json")));
        assertTrue(Files.readString(mapDirectory.resolve("map.json")).contains("\"rules\""));
        assertEquals(77, rules.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
        Files.writeString(mapDirectory.resolve("classes.json"), """
                {
                  "classes": [
                    {
                      "id": "map_scout",
                      "displayName": "Map Scout",
                      "gunId": "tacz:hk416d",
                      "ammoId": "tacz:556x45"
                    }
                  ],
                  "teams": {}
                }
                """);
        assertTrue(classes.reload(data).isEmpty());
        assertTrue(classes.containsForTeam(GameModeRegistry.TEAM_DEATHMATCH, "default", TeamSide.RED, "map_scout"));
        RuleConfigRegistry restoredRules = new RuleConfigRegistry();
        restoredRules.useConfigRoot(directory);
        SFGameSavedData restoredData = new SFGameSavedData();
        assertTrue(restoredRules.reload(restoredData).isEmpty());
        assertEquals(77, restoredRules.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                restoredData.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
        assertTrue(Files.isRegularFile(directory.resolve("maps").resolve("tdm").resolve("defaults.json")));
        assertFalse(Files.exists(directory.resolve("rules").resolve("tdm.json")));
        assertFalse(Files.exists(directory.resolve("classes").resolve("tdm.json")));
    }
}

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
        assertTrue(rules.reload(data).isEmpty(), () -> rules.errors().toString());
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
        Files.createDirectories(directory.resolve("rules"));
        Files.writeString(directory.resolve("rules").resolve("tdm.json"),
                "{\"rules\":{\"scoreLimit\":999},\"maps\":{}}\n");
        Files.createDirectories(directory.resolve("classes"));
        Files.writeString(directory.resolve("classes").resolve("tdm.json"),
                "{\"classes\":[{\"id\":\"legacy_only\",\"displayName\":\"Legacy\",\"gunId\":\"tacz:hk416d\",\"ammoId\":\"tacz:556x45\"}]}\n");
        assertTrue(classes.reload(data).isEmpty());
        assertFalse(classes.containsForTeam(GameModeRegistry.TEAM_DEATHMATCH, "default", TeamSide.RED, "legacy_only"));
        RuleConfigRegistry restoredRules = new RuleConfigRegistry();
        restoredRules.useConfigRoot(directory);
        SFGameSavedData restoredData = new SFGameSavedData();
        assertTrue(restoredRules.reload(restoredData).isEmpty());
        assertEquals(77, restoredRules.rules(GameModeRegistry.TEAM_DEATHMATCH, "default",
                restoredData.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
        assertTrue(Files.readString(mapDirectory.resolve("map.json")).contains("\"parent\""));
        assertTrue(Files.readString(mapDirectory.resolve("classes.json")).contains("\"parent\""));
        assertFalse(Files.exists(directory.resolve("maps").resolve("tdm").resolve("defaults.json")));
    }
    @Test
    void mapParentFieldsControlRuleAndClassInheritance() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        MapConfigRegistry maps = new MapConfigRegistry();
        maps.useConfigRoot(directory);
        assertTrue(maps.reload(data).isEmpty());

        RuleConfigRegistry rules = new RuleConfigRegistry();
        rules.useConfigRoot(directory);
        assertTrue(rules.reload(data).isEmpty());
        rules.setInt(GameModeRegistry.TEAM_DEATHMATCH, "default", "scoreLimit", 77);
        assertTrue(data.createMap("child"));
        maps.saveMap(GameModeRegistry.TEAM_DEATHMATCH, data.activeMap());
        assertTrue(rules.reload(data).isEmpty());
        rules.setParent(GameModeRegistry.TEAM_DEATHMATCH, "child", "default");
        rules.resetMap(GameModeRegistry.TEAM_DEATHMATCH, "child");
        assertEquals(77, rules.rules(GameModeRegistry.TEAM_DEATHMATCH, "child",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
        assertTrue(Files.readString(directory.resolve("maps").resolve("tdm").resolve("child").resolve("map.json"))
                .contains("\"parent\": \"default\""));

        ClassRegistry classes = new ClassRegistry();
        classes.useConfigRoot(directory);
        assertTrue(classes.reload(data).isEmpty());
        Path defaultClasses = directory.resolve("maps").resolve("tdm").resolve("default").resolve("classes.json");
        Files.writeString(defaultClasses, """
                {
                  "parent": "base",
                  "classes": [
                    {
                      "id": "default_only",
                      "displayName": "Default Only",
                      "gunId": "tacz:hk416d",
                      "ammoId": "tacz:556x45"
                    }
                  ],
                  "teams": {}
                }
                """);
        Path childClasses = directory.resolve("maps").resolve("tdm").resolve("child").resolve("classes.json");
        Files.writeString(childClasses, """
                {
                  "parent": "default",
                  "classes": [
                    {
                      "id": "child_only",
                      "displayName": "Child Only",
                      "gunId": "tacz:hk416d",
                      "ammoId": "tacz:556x45"
                    }
                  ],
                  "teams": {}
                }
                """);
        assertTrue(classes.reload(data).isEmpty());
        assertTrue(classes.containsForTeam(GameModeRegistry.TEAM_DEATHMATCH, "child", TeamSide.RED, "default_only"),
                Files.readString(defaultClasses));
        assertTrue(classes.containsForTeam(GameModeRegistry.TEAM_DEATHMATCH, "child", TeamSide.RED, "child_only"));
    }
}

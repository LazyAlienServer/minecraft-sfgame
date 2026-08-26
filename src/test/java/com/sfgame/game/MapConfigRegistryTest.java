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
        source.activeMap().displayName("默认");
        source.lobby(new ArenaPosition("minecraft:overworld", 1, 64, 2, 90, 0));
        source.addSpawn(TeamSide.RED, new ArenaPosition("minecraft:overworld", 10, 65, 10, 0, 0));
        source.addSpawn(TeamSide.BLUE, new ArenaPosition("minecraft:overworld", -10, 65, -10, 180, 0));
        MapConfigRegistry registry = new MapConfigRegistry();
        registry.useConfigRoot(directory);
        assertTrue(registry.reload(source).isEmpty());

        Path mapPath = directory.resolve("maps").resolve("tdm").resolve("default").resolve("map.json");
        assertTrue(Files.isRegularFile(mapPath));
        assertTrue(Files.readString(mapPath).contains("\"redSpawns\""));
        assertTrue(Files.readString(mapPath).contains("\"displayName\""));

        SFGameSavedData restored = new SFGameSavedData();
        MapConfigRegistry second = new MapConfigRegistry();
        second.useConfigRoot(directory);
        assertTrue(second.reload(restored).isEmpty());
        assertEquals(1, restored.spawns(TeamSide.RED).size());
        assertEquals(1, restored.spawns(TeamSide.BLUE).size());
        assertEquals("默认", restored.activeMap().displayName());
    }

    @Test
    void generatesBaseDocumentsAndParentOnlyMapDocuments() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        MapConfigRegistry maps = new MapConfigRegistry();
        maps.useConfigRoot(directory);
        assertTrue(maps.reload(data).isEmpty());

        RuleConfigRegistry rules = new RuleConfigRegistry();
        rules.useConfigRoot(directory);
        assertTrue(rules.reload(data).isEmpty());
        ClassRegistry classes = new ClassRegistry();
        classes.useConfigRoot(directory);
        assertTrue(classes.reload(data).isEmpty());

        Path modeDirectory = directory.resolve("maps").resolve("tdm");
        com.google.gson.JsonObject mapDocument = com.google.gson.JsonParser.parseString(
                Files.readString(modeDirectory.resolve("default").resolve("map.json"))).getAsJsonObject();
        com.google.gson.JsonObject classDocument = com.google.gson.JsonParser.parseString(
                Files.readString(modeDirectory.resolve("default").resolve("classes.json"))).getAsJsonObject();
        assertEquals(1, mapDocument.size());
        assertEquals("tdm/base", mapDocument.get("parent").getAsString());
        assertEquals(5, classDocument.size());
        assertEquals("tdm/base", classDocument.get("parent").getAsString());
        assertTrue(classDocument.has("eliteClasses"));
        assertTrue(Files.readString(modeDirectory.resolve("base").resolve("map.json")).contains("\"rules\""));
        assertTrue(Files.readString(modeDirectory.resolve("base").resolve("classes.json")).contains("\"classes\""));
        assertFalse(data.maps(GameModeRegistry.TEAM_DEATHMATCH).stream()
                .anyMatch(map -> "base".equals(map.id())));
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
                .contains("\"parent\": \"tdm/default\""));

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
    @Test
    void supportsCrossModeRuleAndClassParents() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        MapConfigRegistry maps = new MapConfigRegistry();
        maps.useConfigRoot(directory);
        assertTrue(maps.reload(data).isEmpty());
        assertTrue(data.createMap("source"));
        maps.createMap(GameModeRegistry.TEAM_DEATHMATCH, "source");
        assertTrue(data.selectMode(GameModeRegistry.DOMINATION));
        assertTrue(data.createMap("child"));
        maps.createMap(GameModeRegistry.DOMINATION, "child");

        RuleConfigRegistry rules = new RuleConfigRegistry();
        rules.useConfigRoot(directory);
        assertTrue(rules.reload(data).isEmpty());
        rules.setInt(GameModeRegistry.TEAM_DEATHMATCH, "source", "scoreLimit", 77);
        rules.setParent(GameModeRegistry.DOMINATION, "child", "tdm/source");
        rules.resetMap(GameModeRegistry.DOMINATION, "child");
        assertEquals(77, rules.rules(GameModeRegistry.DOMINATION, "child",
                data.rules(GameModeRegistry.DOMINATION)).scoreLimit());

        ClassRegistry classes = new ClassRegistry();
        classes.useConfigRoot(directory);
        assertTrue(classes.reload(data).isEmpty());
        Path sourceClasses = directory.resolve("maps").resolve("tdm").resolve("source").resolve("classes.json");
        Files.writeString(sourceClasses, """
                {
                  "parent": "tdm/base",
                  "classes": [
                    {
                      "id": "cross_mode",
                      "displayName": "Cross Mode",
                      "gunId": "tacz:hk416d",
                      "ammoId": "tacz:556x45"
                    }
                  ]
                }
                """);
        Path childClasses = directory.resolve("maps").resolve("domination").resolve("child").resolve("classes.json");
        Files.writeString(childClasses, "{\"parent\":\"tdm/source\"}\n");
        assertTrue(classes.reload(data).isEmpty());
        assertTrue(classes.containsForTeam(GameModeRegistry.DOMINATION, "child", TeamSide.RED, "cross_mode"));
    }
    @Test
    void commandStyleCreationRefreshesBaseRulesAndClassesImmediately() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        MapConfigRegistry maps = new MapConfigRegistry();
        maps.useConfigRoot(directory);
        assertTrue(maps.reload(data).isEmpty());
        RuleConfigRegistry rules = new RuleConfigRegistry();
        rules.useConfigRoot(directory);
        assertTrue(rules.reload(data).isEmpty());
        ClassRegistry classes = new ClassRegistry();
        classes.useConfigRoot(directory);
        assertTrue(classes.reload(data).isEmpty());

        Path baseMap = directory.resolve("maps").resolve("tdm").resolve("base").resolve("map.json");
        com.google.gson.JsonObject baseRules = com.google.gson.JsonParser.parseString(Files.readString(baseMap)).getAsJsonObject();
        baseRules.getAsJsonObject("rules").addProperty("scoreLimit", 83);
        Files.writeString(baseMap, baseRules.toString());
        Path baseClasses = directory.resolve("maps").resolve("tdm").resolve("base").resolve("classes.json");
        Files.writeString(baseClasses, """
                {
                  "classes": [
                    {
                      "id": "base_live",
                      "displayName": "Base Live",
                      "gunId": "tacz:hk416d",
                      "ammoId": "tacz:556x45"
                    }
                  ]
                }
                """);
        assertTrue(rules.reload(data).isEmpty());
        assertTrue(classes.reload(data).isEmpty());

        assertTrue(data.createMap("fresh"));
        maps.createMap(GameModeRegistry.TEAM_DEATHMATCH, "fresh");
        classes.createMapProfile(GameModeRegistry.TEAM_DEATHMATCH, "fresh");
        // MatchManager.createMapConfiguration performs these refreshes itself.
        assertTrue(rules.reload(data).isEmpty());
        assertTrue(classes.reload(data).isEmpty());
        assertEquals(83, rules.rules(GameModeRegistry.TEAM_DEATHMATCH, "fresh",
                data.rules(GameModeRegistry.TEAM_DEATHMATCH)).scoreLimit());
        assertTrue(classes.containsForTeam(GameModeRegistry.TEAM_DEATHMATCH, "fresh",
                TeamSide.RED, "base_live"));
    }

    @Test
    void parentReferencesBlockDeletionAndRemovedScopesDisappearAfterRefresh() {
        SFGameSavedData data = new SFGameSavedData();
        MapConfigRegistry maps = new MapConfigRegistry();
        maps.useConfigRoot(directory);
        assertTrue(maps.reload(data).isEmpty());
        RuleConfigRegistry rules = new RuleConfigRegistry();
        rules.useConfigRoot(directory);
        assertTrue(rules.reload(data).isEmpty());
        ClassRegistry classes = new ClassRegistry();
        classes.useConfigRoot(directory);
        assertTrue(classes.reload(data).isEmpty());

        assertTrue(data.createMap("parent"));
        maps.createMap(GameModeRegistry.TEAM_DEATHMATCH, "parent");
        classes.createMapProfile(GameModeRegistry.TEAM_DEATHMATCH, "parent");
        assertTrue(data.createMap("child"));
        maps.createMap(GameModeRegistry.TEAM_DEATHMATCH, "child");
        classes.createMapProfile(GameModeRegistry.TEAM_DEATHMATCH, "child");
        assertTrue(rules.reload(data).isEmpty());
        assertTrue(classes.reload(data).isEmpty());
        rules.setParent(GameModeRegistry.TEAM_DEATHMATCH, "child", "parent");
        assertEquals(java.util.List.of("tdm/child"),
                rules.referencesTo(GameModeRegistry.TEAM_DEATHMATCH, "parent"));

        rules.setParent(GameModeRegistry.TEAM_DEATHMATCH, "child", "base");
        assertTrue(data.removeMap("child"));
        maps.deleteMap(GameModeRegistry.TEAM_DEATHMATCH, "child");
        assertTrue(rules.reload(data).isEmpty());
        assertTrue(classes.reload(data).isEmpty());
        assertEquals("base", rules.parent(GameModeRegistry.TEAM_DEATHMATCH, "child"));
        assertTrue(classes.allForTeam(GameModeRegistry.TEAM_DEATHMATCH, "child", TeamSide.RED).isEmpty());
    }
    @Test
    void malformedMapReloadKeepsLastKnownGoodModeMaps() throws Exception {
        SFGameSavedData data = new SFGameSavedData();
        data.lobby(new ArenaPosition("minecraft:overworld", 1, 64, 2, 0, 0));
        data.addSpawn(TeamSide.RED, new ArenaPosition("minecraft:overworld", 1, 64, 1, 0, 0));
        data.addSpawn(TeamSide.BLUE, new ArenaPosition("minecraft:overworld", -1, 64, -1, 0, 0));
        MapConfigRegistry registry = new MapConfigRegistry();
        registry.useConfigRoot(directory);
        assertTrue(registry.reload(data).isEmpty());
        ArenaPosition expected = data.lobby();

        Files.writeString(registry.mapPath(GameModeRegistry.TEAM_DEATHMATCH, "default"), "{broken");
        assertFalse(registry.reload(data).isEmpty());
        assertEquals(expected, data.lobby());
        assertEquals(1, data.maps(GameModeRegistry.TEAM_DEATHMATCH).size());
    }

}

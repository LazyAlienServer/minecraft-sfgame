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
    void persistsMapWideBuildRegionAndSnapshotHint() {
        SFGameSavedData source = new SFGameSavedData();
        source.activeMap().build().region(new BoxCaptureRegion("minecraft:overworld", 0, 31, 0, 31, null, null));
        source.activeMap().build().snapshotSaved(true);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertNotNull(restored.activeMap().build().region());
        assertTrue(restored.activeMap().build().snapshotSaved());
    }

    @Test
    void acceptsSingleDigitMapIdsAndPersistsThem() {
        SFGameSavedData source = new SFGameSavedData();
        assertTrue(source.createMap("1"));
        assertEquals("1", source.selectedMap());

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertEquals("1", restored.selectedMap());
        assertTrue(restored.selectMap("1"));
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
    void devModeAllowsAOneTeamSandboxMap() {
        SFGameSavedData data = new SFGameSavedData();
        data.lobby(LOBBY);
        data.addSpawn(com.sfgame.game.TeamSide.RED, RED);
        assertFalse(data.isArenaConfigured());

        data.devMode(true);

        assertTrue(data.isArenaConfigured());
        assertEquals(List.of(com.sfgame.game.TeamSide.RED), data.enabledTeams());

        SFGameSavedData restored = SFGameSavedData.load(data.save(new CompoundTag()));
        assertTrue(restored.devMode());
        assertTrue(restored.isArenaConfigured());
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

    @Test
    void persistsDominationPointsStrategyAndModeRules() {
        SFGameSavedData source = new SFGameSavedData();
        assertTrue(source.selectMode(com.sfgame.game.GameModeRegistry.DOMINATION));
        source.lobby(LOBBY);
        source.addSpawn(com.sfgame.game.TeamSide.RED, RED);
        source.addSpawn(com.sfgame.game.TeamSide.BLUE, BLUE);
        source.activeMap().domination().strategy(PointActivationStrategy.SYNC);
        source.activeMap().domination().add(new CapturePointDefinition("a",
                new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1));
        source.rules().captureDifferenceCoefficient(1.75);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertEquals(com.sfgame.game.GameModeRegistry.DOMINATION, restored.selectedMode());
        assertTrue(restored.isArenaConfigured());
        assertEquals(PointActivationStrategy.SYNC, restored.activeMap().domination().strategy());
        assertEquals("a", restored.activeMap().domination().points().get(0).id());
        assertEquals(1.75, restored.rules().captureDifferenceCoefficient());
        assertEquals(50, restored.rules(com.sfgame.game.GameModeRegistry.TEAM_DEATHMATCH).scoreLimit());
    }

    @Test
    void rejectsOverlappingCapturePoints() {
        DominationMapConfig config = new DominationMapConfig();
        config.add(new CapturePointDefinition("a", new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> config.add(
                new CapturePointDefinition("b", new BoxCaptureRegion("minecraft:overworld", 4, 10, 4, 10, null, null), 2)));
    }

    @Test
    void acceptsSingleLetterAndDigitPointIdsAndNormalizesLookups() {
        DominationMapConfig config = new DominationMapConfig();
        config.add(new CapturePointDefinition("A",
                new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1));
        config.add(new CapturePointDefinition("1",
                new SquareCaptureRegion("minecraft:overworld", 20, 0, 5, null, null), 2));

        assertEquals("a", config.points().get(0).id());
        assertEquals("1", config.points().get(1).id());
        assertTrue(config.point("A").isPresent());
        assertTrue(config.point("1").isPresent());
        config.replace("A", config.point("a").orElseThrow().withOrder(2));
        assertEquals(2, config.point("A").orElseThrow().order());
        assertTrue(config.remove("A"));
    }

    @Test
    void mapLobbyOverridesDefaultLobbyAndClearRestoresFallback() {
        SFGameSavedData source = new SFGameSavedData();
        source.defaultLobby(LOBBY);
        assertEquals(LOBBY, source.lobby());

        source.lobby(RED);
        assertEquals(RED, source.lobby());
        source.clearLobby();
        assertEquals(LOBBY, source.lobby());
        source.addSpawn(com.sfgame.game.TeamSide.RED, RED);
        source.addSpawn(com.sfgame.game.TeamSide.BLUE, BLUE);
        assertTrue(source.isArenaConfigured());

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));
        assertEquals(LOBBY, restored.defaultLobby());
        assertEquals(LOBBY, restored.lobby());
    }

    @Test
    void migratesDominationScoringIntervalToOneSecond() {
        SFGameSavedData source = new SFGameSavedData();
        source.rules(com.sfgame.game.GameModeRegistry.DOMINATION).scoreIntervalSeconds(5);
        CompoundTag oldSave = source.save(new CompoundTag());
        oldSave.putInt("DataVersion", 5);

        SFGameSavedData restored = SFGameSavedData.load(oldSave);

        assertEquals(1, restored.rules(com.sfgame.game.GameModeRegistry.DOMINATION).scoreIntervalSeconds());
    }

    @Test
    void migratesLegacyBreakthroughDefaultRespawnToTenSeconds() {
        SFGameSavedData source = new SFGameSavedData();
        source.rules(com.sfgame.game.GameModeRegistry.BREAKTHROUGH).respawnSeconds(5);
        CompoundTag oldSave = source.save(new CompoundTag());
        oldSave.putInt("DataVersion", 7);

        SFGameSavedData restored = SFGameSavedData.load(oldSave);

        assertEquals(10, restored.rules(com.sfgame.game.GameModeRegistry.BREAKTHROUGH).respawnSeconds());
    }

    @Test
    void migratesLegacyGlobalRulesOnlyIntoTdm() {
        CompoundTag legacy = new CompoundTag();
        MatchRules oldRules = new MatchRules();
        oldRules.scoreLimit(77);
        legacy.put("Rules", oldRules.save());

        SFGameSavedData restored = SFGameSavedData.load(legacy);

        assertEquals(77, restored.rules(com.sfgame.game.GameModeRegistry.TEAM_DEATHMATCH).scoreLimit());
        assertEquals(100, restored.rules(com.sfgame.game.GameModeRegistry.DOMINATION).scoreLimit());
    }

    @Test
    void persistsConfiguredBreakthroughMapWithRoleSpawnsAndSectors() {
        SFGameSavedData source = new SFGameSavedData();
        source.defaultLobby(LOBBY);
        assertTrue(source.selectMode(com.sfgame.game.GameModeRegistry.BREAKTHROUGH));
        source.activeMap().breakthrough().variant(BreakthroughVariant.CAPTAIN);
        source.activeMap().breakthrough().legs(2);
        source.activeMap().breakthrough().roles(com.sfgame.game.TeamSide.YELLOW, com.sfgame.game.TeamSide.GREEN);
        BreakthroughSectorDefinition first = new BreakthroughSectorDefinition("first", 1);
        first.addPoint(new CapturePointDefinition("A", new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1)
                .withRespawnPosition(LOBBY).withNearbyRespawnPosition(RED));
        first.addSpawn(true, RED);
        first.addSpawn(false, BLUE);
        source.activeMap().breakthrough().addSector(first);

        SFGameSavedData restored = SFGameSavedData.load(source.save(new CompoundTag()));

        assertTrue(restored.isArenaConfigured());
        assertEquals(BreakthroughVariant.CAPTAIN, restored.activeMap().breakthrough().variant());
        assertEquals(2, restored.activeMap().breakthrough().legs());
        assertEquals(List.of(com.sfgame.game.TeamSide.YELLOW, com.sfgame.game.TeamSide.GREEN), restored.enabledTeams());
        assertEquals("a", restored.activeMap().breakthrough().sectors().get(0).points().get(0).id());
        assertEquals(LOBBY, restored.activeMap().breakthrough().sectors().get(0).points().get(0).respawnPosition());
        assertEquals(RED, restored.activeMap().breakthrough().sectors().get(0).points().get(0).nearbyRespawnPosition());
        assertEquals(List.of(RED), restored.activeMap().breakthrough().sectors().get(0).spawns(true));
    }

    @Test
    void breakthroughRequiresInsideAndNearbyRespawnPositionsForEveryPoint() {
        BreakthroughSectorDefinition sector = new BreakthroughSectorDefinition("first", 1);
        CapturePointDefinition point = new CapturePointDefinition("a",
                new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1);
        sector.addPoint(point);
        sector.addSpawn(true, RED);
        sector.addSpawn(false, BLUE);

        assertTrue(sector.validate().stream().anyMatch(error -> error.contains("inside respawn")));
        sector.replacePoint("a", point.withRespawnPosition(LOBBY).withNearbyRespawnPosition(RED));
        assertTrue(sector.validate().isEmpty());
    }

    @Test
    void acceptsSingleDigitBreakthroughSectorAndPointIds() {
        BreakthroughMapConfig config = new BreakthroughMapConfig();
        BreakthroughSectorDefinition sector = new BreakthroughSectorDefinition("1", 1);
        sector.addPoint(new CapturePointDefinition("2",
                new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1));
        config.addSector(sector);

        assertEquals("1", config.sector("1").orElseThrow().id());
        assertEquals("2", config.sector("1").orElseThrow().point("2").orElseThrow().id());
    }

    @Test
    void persistsBreakthroughVehicleSlots() {
        BreakthroughMapConfig config = new BreakthroughMapConfig();
        BreakthroughVehicleDefinition source = new BreakthroughVehicleDefinition("1", "minecraft:minecart",
                BreakthroughVehicleDefinition.Role.DEFENDER, RED, 30);
        source.spawnYOffset(1.25D);
        source.energyPercent(75);
        source.clearAmmo();
        source.setAmmo("minecraft:arrow", 128);
        source.setAmmo("minecraft:firework_rocket", 32);
        config.addVehicle(source);

        BreakthroughMapConfig restored = BreakthroughMapConfig.load(config.save());

        assertEquals(1, restored.vehicles().size());
        BreakthroughVehicleDefinition vehicle = restored.vehicle("1").orElseThrow();
        assertEquals("minecraft:minecart", vehicle.entityId());
        assertEquals(BreakthroughVehicleDefinition.Role.DEFENDER, vehicle.role());
        assertEquals(30, vehicle.respawnSeconds());
        assertEquals(RED, vehicle.spawn());
        assertEquals(1.25D, vehicle.spawnYOffset());
        assertEquals(75, vehicle.energyPercent());
        assertEquals(List.of(new BreakthroughVehicleDefinition.AmmoEntry("minecraft:arrow", 128),
                new BreakthroughVehicleDefinition.AmmoEntry("minecraft:firework_rocket", 32)), vehicle.ammo());
    }

    @Test
    void breakthroughVehicleDefaultsToRaisedSpawnFullEnergyAndCreativeAmmo() {
        ArenaPosition tiltedPlacement = new ArenaPosition("minecraft:overworld", 1, 64, 2, 135, 82);
        BreakthroughVehicleDefinition vehicle = new BreakthroughVehicleDefinition("1", "minecraft:minecart",
                BreakthroughVehicleDefinition.Role.ATTACKER, tiltedPlacement, 20);

        assertEquals(0.2D, vehicle.spawnYOffset());
        assertEquals(100, vehicle.energyPercent());
        assertEquals(135.0F, vehicle.spawn().yaw());
        assertEquals(0.0F, vehicle.spawn().pitch());
        assertEquals(List.of(new BreakthroughVehicleDefinition.AmmoEntry(
                "superbwarfare:creative_ammo_box", 1)), vehicle.ammo());
    }

    @Test
    void breakthroughRejectsOverlapsWithinSectorButAllowsSameIdsAcrossSectors() {
        BreakthroughMapConfig config = new BreakthroughMapConfig();
        BreakthroughSectorDefinition first = new BreakthroughSectorDefinition("first", 1);
        first.addPoint(new CapturePointDefinition("a", new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> first.addPoint(
                new CapturePointDefinition("b", new SquareCaptureRegion("minecraft:overworld", 4, 0, 5, null, null), 2)));
        BreakthroughSectorDefinition second = new BreakthroughSectorDefinition("second", 2);
        second.addPoint(new CapturePointDefinition("a", new SquareCaptureRegion("minecraft:overworld", 100, 0, 5, null, null), 1));
        config.addSector(first);
        config.addSector(second);
        assertEquals("a", config.sectors().get(0).points().get(0).id());
        assertEquals("a", config.sectors().get(1).points().get(0).id());
    }
}

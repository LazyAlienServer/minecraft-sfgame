package com.sfgame.game;

import com.sfgame.data.ArenaPosition;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.BreakthroughSectorDefinition;
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.SquareCaptureRegion;
import com.sfgame.data.MatchRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BreakthroughRuntimeTest {
    private static final ArenaPosition INSIDE = new ArenaPosition("minecraft:overworld", 0, 64, 0, 0, 0);
    private static final ArenaPosition NEARBY = new ArenaPosition("minecraft:overworld", 10, 64, 0, 0, 0);
    private static final CapturePointDefinition POINT = new CapturePointDefinition("a",
            new SquareCaptureRegion("minecraft:overworld", 0, 0, 5, null, null), 1)
            .withRespawnPosition(INSIDE).withNearbyRespawnPosition(NEARBY);

    @Test
    void usesInsidePositionForSecureOwnedPoint() {
        CapturePointState state = new CapturePointState();
        state.reset(TeamSide.BLUE);

        assertEquals(INSIDE, BreakthroughRuntime.pointRespawnPosition(POINT, state));
    }

    @Test
    void usesNearbyPositionWhilePointIsContestedOrUnderAttack() {
        CapturePointState state = new CapturePointState();
        state.reset(TeamSide.BLUE);
        state.contested(true);
        assertEquals(NEARBY, BreakthroughRuntime.pointRespawnPosition(POINT, state));

        state.advance(TeamSide.RED, 0.2, false);
        assertEquals(NEARBY, BreakthroughRuntime.pointRespawnPosition(POINT, state));
    }

    @Test
    void liveClockUpdatesCurrentSectorIndependently() {
        BreakthroughRuntime runtime = new BreakthroughRuntime();
        com.sfgame.data.MatchRules rules = new com.sfgame.data.MatchRules(GameModeRegistry.BREAKTHROUGH);
        MatchManager manager = new MatchManager();

        runtime.setRemainingSeconds(manager, rules, 900);
        assertEquals(900, runtime.remainingSeconds(manager, rules));

        runtime.setRemainingSeconds(manager, rules, 0);
        assertEquals(0, runtime.remainingSeconds(manager, rules));
        BreakthroughRuntime unlimitedRuntime = new BreakthroughRuntime();
        MatchRules unlimitedRules = new MatchRules(GameModeRegistry.BREAKTHROUGH);
        unlimitedRules.timeLimitSeconds(MatchRules.UNLIMITED_TIME_SECONDS);
        assertEquals(MatchRules.UNLIMITED_TIME_SECONDS,
                unlimitedRuntime.remainingSeconds(manager, unlimitedRules));
    }

    @Test
    void liveStateEditsResetTheSelectedSector() {
        BreakthroughRuntime runtime = new BreakthroughRuntime();
        MatchRules rules = new MatchRules(GameModeRegistry.BREAKTHROUGH);
        rules.attackerTickets(120);
        ArenaMap map = twoSectorMap();
        assertTrue(runtime.setLegState(rules, map, 10));
        assertEquals(10, runtime.leg());
        assertEquals(TeamSide.RED, runtime.attacker());
        assertEquals(TeamSide.BLUE, runtime.defender());
        assertEquals(1, runtime.sectorNumber());
        assertEquals(MatchRules.DEFAULT_BREAKTHROUGH_ATTACK_ROUNDS, runtime.attackRoundsRemaining());
        assertEquals(120, runtime.tickets());


        assertTrue(runtime.setTicketsValue(37));
        assertEquals(37, runtime.tickets());
        assertTrue(runtime.setSectorState(rules, map, 2));
        assertEquals(2, runtime.sectorNumber());
        assertEquals(120, runtime.tickets());
        rules.breakthroughAttackRounds(3);
        assertTrue(runtime.setSectorState(rules, map, 1));
        assertEquals(3, runtime.attackRoundsRemaining());

        assertFalse(runtime.setSectorState(rules, map, 3));
        assertFalse(runtime.setLegState(rules, map, 11));
        assertTrue(BreakthroughRuntime.changesRosterParity(1, 10));
        assertFalse(BreakthroughRuntime.changesRosterParity(1, 9));
        assertTrue(runtime.setTicketsValue(MatchRules.UNLIMITED_TICKETS));
        assertEquals(MatchRules.UNLIMITED_TICKETS, runtime.tickets());
    }
    @Test
    void remainingLegsCountsRotationsAfterTheCurrentLeg() {
        BreakthroughRuntime runtime = new BreakthroughRuntime();
        MatchRules rules = new MatchRules(GameModeRegistry.BREAKTHROUGH);
        rules.breakthroughLegs(1);
        ArenaMap map = twoSectorMap();

        assertTrue(runtime.setLegState(rules, map, 1));
        assertEquals(1, runtime.remainingLegs(rules));
        assertTrue(runtime.setLegState(rules, map, 2));
        assertEquals(0, runtime.remainingLegs(rules));
    }
    @Test
    void rotationTimingAndWinnerUseTheCompletedLegResult() {
        assertEquals(TeamSide.RED, BreakthroughRuntime.roundWinner(true, TeamSide.RED, TeamSide.BLUE));
        assertEquals(TeamSide.BLUE, BreakthroughRuntime.roundWinner(false, TeamSide.RED, TeamSide.BLUE));
        assertEquals(5, BreakthroughRuntime.LEG_ROTATION_NOTICE_SECONDS);
        assertEquals(30, BreakthroughRuntime.LEG_PREPARATION_SECONDS);
    }
    @Test
    void stopResetsLifecycleStateForTheNextMatch() {
        BreakthroughRuntime runtime = new BreakthroughRuntime();

        runtime.stop();

        assertEquals("active", runtime.subState());
    }

    @Test
    void startValidationUsesSectorSpawnsWithoutGlobalTeamSpawns() {
        ArenaMap map = twoSectorMap();
        MatchRules rules = new MatchRules(GameModeRegistry.BREAKTHROUGH);

        assertTrue(map.enabledTeams().isEmpty());
        assertTrue(BreakthroughRuntime.validateConfiguration(map, rules).isEmpty());

        map.breakthrough().sectors().get(0).clearSpawns(false);
        assertTrue(BreakthroughRuntime.validateConfiguration(map, rules).stream()
                .anyMatch(error -> error.contains("needs a defender spawn")));
    }

    private static ArenaMap twoSectorMap() {
        ArenaMap map = new ArenaMap("test");
        for (int index = 1; index <= 2; index++) {
            BreakthroughSectorDefinition sector = new BreakthroughSectorDefinition("s" + index, index);
            sector.addPoint(new CapturePointDefinition("p" + index,
                    new SquareCaptureRegion("minecraft:overworld", index * 20, 0, 5, null, null), 1)
                    .withRespawnPosition(new ArenaPosition("minecraft:overworld", index * 20, 64, 0, 0, 0))
                    .withNearbyRespawnPosition(new ArenaPosition(
                            "minecraft:overworld", index * 20 + 10, 64, 0, 0, 0)));
            sector.addSpawn(true, INSIDE);
            sector.addSpawn(false, NEARBY);
            map.breakthrough().addSector(sector);
        }
        return map;
    }
}

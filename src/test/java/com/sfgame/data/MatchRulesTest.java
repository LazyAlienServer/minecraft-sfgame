package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchRulesTest {
    @Test
    void clampsEveryRuntimeRule() {
        MatchRules rules = new MatchRules();

        rules.maxPlayers(1);
        rules.scoreLimit(20_000);
        rules.timeLimitSeconds(1);
        rules.startCountdownSeconds(61);
        rules.respawnSeconds(-1);
        rules.respawnProtectionSeconds(31);
        rules.resultSeconds(0);

        assertEquals(2, rules.maxPlayers());
        assertEquals(10_000, rules.scoreLimit());
        assertEquals(30, rules.timeLimitSeconds());
        assertEquals(60, rules.startCountdownSeconds());
        assertEquals(0, rules.respawnSeconds());
        assertEquals(30, rules.respawnProtectionSeconds());
        assertEquals(1, rules.resultSeconds());
    }

    @Test
    void supportsUnlimitedAndMillionPlayerLimits() {
        MatchRules rules = new MatchRules();

        rules.maxPlayers(MatchRules.UNLIMITED_PLAYERS);
        assertEquals(MatchRules.UNLIMITED_PLAYERS, rules.maxPlayers());
        assertTrue(rules.permitsPlayerCount(Long.MAX_VALUE));

        rules.maxPlayers(MatchRules.MAX_PLAYER_LIMIT);
        assertTrue(rules.permitsPlayerCount(MatchRules.MAX_PLAYER_LIMIT));
        assertFalse(rules.permitsPlayerCount((long) MatchRules.MAX_PLAYER_LIMIT + 1));

        rules.maxPlayers(MatchRules.MAX_PLAYER_LIMIT + 1);
        assertEquals(MatchRules.MAX_PLAYER_LIMIT, rules.maxPlayers());
        rules.maxPlayers(0);
        assertEquals(MatchRules.MIN_PLAYER_LIMIT, rules.maxPlayers());
    }

    @Test
    void persistsAndRestoresRules() {
        MatchRules source = new MatchRules();
        source.maxPlayers(24);
        source.scoreLimit(75);
        source.timeLimitSeconds(900);
        source.respawnSeconds(3);
        source.mapRestorePartitionDelayTicks(4);
        source.mapRestoreAdaptiveThrottling(false);
        source.mapRestoreTargetTickMillis(35);
        source.mapRestoreMaxPartitionsPerTick(12);

        CompoundTag saved = source.save();
        MatchRules restored = new MatchRules();
        restored.load(saved);

        assertEquals(24, restored.maxPlayers());
        assertEquals(75, restored.scoreLimit());
        assertEquals(900, restored.timeLimitSeconds());
        assertEquals(3, restored.respawnSeconds());
        assertEquals(4, restored.mapRestorePartitionDelayTicks());
        assertEquals(false, restored.mapRestoreAdaptiveThrottling());
        assertEquals(35, restored.mapRestoreTargetTickMillis());
        assertEquals(12, restored.mapRestoreMaxPartitionsPerTick());
    }

    @Test
    void resetReturnsAllDefaults() {
        MatchRules rules = new MatchRules();
        rules.maxPlayers(128);
        rules.scoreLimit(999);
        rules.resultSeconds(60);

        rules.reset();

        assertEquals(MatchRules.DEFAULT_MAX_PLAYERS, rules.maxPlayers());
        assertEquals(MatchRules.DEFAULT_SCORE_LIMIT, rules.scoreLimit());
        assertEquals(MatchRules.DEFAULT_TIME_LIMIT_SECONDS, rules.timeLimitSeconds());
        assertEquals(MatchRules.DEFAULT_START_COUNTDOWN_SECONDS, rules.startCountdownSeconds());
        assertEquals(MatchRules.DEFAULT_RESPAWN_SECONDS, rules.respawnSeconds());
        assertEquals(MatchRules.DEFAULT_RESPAWN_PROTECTION_SECONDS, rules.respawnProtectionSeconds());
        assertEquals(MatchRules.DEFAULT_RESULT_SECONDS, rules.resultSeconds());
        assertFalse(rules.mapBlockBreaking());
        assertTrue(rules.mapBlockAllowlist().isEmpty());
        assertEquals(0, rules.mapRestorePartitionDelayTicks());
        assertTrue(rules.mapRestoreAdaptiveThrottling());
        assertEquals(40, rules.mapRestoreTargetTickMillis());
        assertEquals(8, rules.mapRestoreMaxPartitionsPerTick());
    }

    @Test
    void blockBreakingDefaultsToDisabledForEveryMode() {
        for (String modeId : java.util.List.of(
                GameModeRegistry.TEAM_DEATHMATCH,
                GameModeRegistry.DOMINATION,
                GameModeRegistry.BREAKTHROUGH,
                GameModeRegistry.CAPTURE_THE_FLAG)) {
            assertFalse(new MatchRules(modeId).mapBlockBreaking(), modeId);
        }
    }

    @Test
    void blockAllowlistPersistsBlockAndTagSelectors() {
        MatchRules rules = new MatchRules();
        rules.mapBlockAllowlist(java.util.List.of("minecraft:white_wool", "#minecraft:logs"));

        MatchRules restored = new MatchRules();
        restored.load(rules.save());

        assertEquals(java.util.Set.of("minecraft:white_wool", "#minecraft:logs"),
                restored.mapBlockAllowlist());
    }

    @Test
    void dominationRulesHaveIndependentDefaultsAndPersistDecimals() {
        MatchRules rules = new MatchRules(GameModeRegistry.DOMINATION);
        assertEquals(100, rules.scoreLimit());
        assertEquals(1, rules.scoreIntervalSeconds());
        assertEquals(1, rules.scorePerPoint());
        rules.captureDifferenceCoefficient(2.25);
        rules.captureUsePlayerDifference(false);
        rules.syncHoldSeconds(90);
        rules.dominationStrategy(PointActivationStrategy.SYNC);

        MatchRules restored = new MatchRules(GameModeRegistry.DOMINATION);
        restored.load(rules.save());

        assertEquals(2.25, restored.captureDifferenceCoefficient());
        assertEquals(false, restored.captureUsePlayerDifference());
        assertEquals(90, restored.syncHoldSeconds());
        assertEquals(PointActivationStrategy.SYNC, restored.dominationStrategy());
    }

    @Test
    void breakthroughRulesPersistTicketsTransitionsAndWeights() {
        MatchRules rules = new MatchRules(GameModeRegistry.BREAKTHROUGH);
        assertEquals(10, rules.respawnSeconds());
        assertEquals(MatchRules.DEFAULT_BREAKTHROUGH_ATTACK_ROUNDS, rules.breakthroughAttackRounds());
        rules.attackerTickets(150);
        rules.sectorTransitionSeconds(12);
        rules.captainVoteSeconds(20);
        rules.captainReplacementVoteSeconds(8);
        rules.attackerCaptainGlowing(false);
        rules.mapBlockBreaking(false);
        rules.attackerCaptainCaptureWeight(2.5);
        rules.defenderCaptureWeight(1.6);
        rules.breakthroughVariant(BreakthroughVariant.CAPTAIN);
        rules.breakthroughLegs(2);
        rules.breakthroughAttacker(TeamSide.YELLOW);
        rules.breakthroughAttackRounds(100);
        assertEquals(100, rules.breakthroughAttackRounds());
        rules.breakthroughAttackRounds(1);
        rules.breakthroughDefender(TeamSide.GREEN);

        MatchRules restored = new MatchRules(GameModeRegistry.BREAKTHROUGH);
        restored.load(rules.save());

        assertEquals(150, restored.attackerTickets());
        assertEquals(12, restored.sectorTransitionSeconds());
        assertEquals(20, restored.captainVoteSeconds());
        assertEquals(8, restored.captainReplacementVoteSeconds());
        assertEquals(false, restored.attackerCaptainGlowing());
        assertEquals(false, restored.mapBlockBreaking());
        assertEquals(2.5, restored.attackerCaptainCaptureWeight());
        assertEquals(1.6, restored.defenderCaptureWeight());
        assertEquals(BreakthroughVariant.CAPTAIN, restored.breakthroughVariant());
        assertEquals(2, restored.breakthroughLegs());
        assertEquals(TeamSide.YELLOW, restored.breakthroughAttacker());
        assertEquals(1, restored.breakthroughAttackRounds());
        assertEquals(TeamSide.GREEN, restored.breakthroughDefender());
    }

    @Test
    void ctfRulesUseIndependentDefaultsAndPersistFlagTimers() {
        MatchRules rules = new MatchRules(GameModeRegistry.CAPTURE_THE_FLAG);
        assertEquals(3, rules.scoreLimit());
        assertEquals(30, rules.ctfFlagReturnSeconds());
        rules.ctfFlagReturnSeconds(45);
        rules.ctfHomeCaptureTimeSeconds(20);
        rules.ctfVariant(CtfVariant.ASSAULT);
        rules.ctfAttacker(TeamSide.GREEN);
        rules.ctfDefender(TeamSide.RED);
        rules.ctfCarrierRestriction(CarrierRestriction.NO_WEAPONS);
        MatchRules restored = new MatchRules(GameModeRegistry.CAPTURE_THE_FLAG);
        restored.load(rules.save());
        assertEquals(45, restored.ctfFlagReturnSeconds());
        assertEquals(20, restored.ctfHomeCaptureTimeSeconds());
        assertEquals(CtfVariant.ASSAULT, restored.ctfVariant());
        assertEquals(TeamSide.GREEN, restored.ctfAttacker());
        assertEquals(TeamSide.RED, restored.ctfDefender());
        assertEquals(CarrierRestriction.NO_WEAPONS, restored.ctfCarrierRestriction());
    }
    @Test
    void economyRulesClampDefaultPersistAndCopy() {
        MatchRules rules = new MatchRules(GameModeRegistry.CAPTURE_THE_FLAG);
        assertFalse(rules.economyEnabled());
        assertFalse(new MatchRules(GameModeRegistry.TEAM_DEATHMATCH).economyEnabled());
        assertEquals(25, rules.killCurrency());
        assertEquals(10, rules.ctfTerritoryUnlockCurrency());
        assertEquals(50, rules.ctfForwardFlagReplantCurrency());
        assertEquals(100, rules.ctfForwardFlagCaptureCurrency());
        assertEquals(100, rules.ctfHomeFlagCaptureCurrency());

        rules.killCurrency(-1);
        rules.ctfTerritoryUnlockCurrency(100_001);
        rules.ctfForwardFlagReplantCurrency(7);
        rules.ctfForwardFlagCaptureCurrency(8);
        rules.ctfHomeFlagCaptureCurrency(9);
        rules.economyEnabled(false);

        MatchRules restored = new MatchRules(GameModeRegistry.CAPTURE_THE_FLAG);
        restored.load(rules.save());
        assertEquals(0, restored.killCurrency());
        assertEquals(100_000, restored.ctfTerritoryUnlockCurrency());
        assertEquals(7, restored.ctfForwardFlagReplantCurrency());
        assertEquals(8, restored.ctfForwardFlagCaptureCurrency());
        assertEquals(9, restored.ctfHomeFlagCaptureCurrency());
        assertFalse(restored.economyEnabled());
        assertEquals(9, restored.copy().ctfHomeFlagCaptureCurrency());

        restored.reset();
        assertEquals(25, restored.killCurrency());
        assertEquals(10, restored.ctfTerritoryUnlockCurrency());
        assertEquals(50, restored.ctfForwardFlagReplantCurrency());
        assertEquals(100, restored.ctfForwardFlagCaptureCurrency());
        assertEquals(100, restored.ctfHomeFlagCaptureCurrency());
        assertFalse(restored.economyEnabled());
    }

}

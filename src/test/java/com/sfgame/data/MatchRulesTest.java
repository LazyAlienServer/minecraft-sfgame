package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void persistsAndRestoresRules() {
        MatchRules source = new MatchRules();
        source.maxPlayers(24);
        source.scoreLimit(75);
        source.timeLimitSeconds(900);
        source.respawnSeconds(3);

        CompoundTag saved = source.save();
        MatchRules restored = new MatchRules();
        restored.load(saved);

        assertEquals(24, restored.maxPlayers());
        assertEquals(75, restored.scoreLimit());
        assertEquals(900, restored.timeLimitSeconds());
        assertEquals(3, restored.respawnSeconds());
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
    }

    @Test
    void dominationRulesHaveIndependentDefaultsAndPersistDecimals() {
        MatchRules rules = new MatchRules(GameModeRegistry.DOMINATION);
        assertEquals(100, rules.scoreLimit());
        rules.captureDifferenceCoefficient(2.25);
        rules.captureUsePlayerDifference(false);
        rules.syncHoldSeconds(90);

        MatchRules restored = new MatchRules(GameModeRegistry.DOMINATION);
        restored.load(rules.save());

        assertEquals(2.25, restored.captureDifferenceCoefficient());
        assertEquals(false, restored.captureUsePlayerDifference());
        assertEquals(90, restored.syncHoldSeconds());
    }
}

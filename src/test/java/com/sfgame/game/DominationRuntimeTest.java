package com.sfgame.game;

import com.sfgame.data.MatchRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DominationRuntimeTest {
    @Test
    void calculatesConfiguredPlayerDifferenceAndCap() {
        MatchRules rules = new MatchRules(GameModeRegistry.DOMINATION);
        rules.captureDifferenceCoefficient(1.5);
        rules.captureMaxMultiplier(4);

        assertEquals(1.5, DominationRuntime.calculateCaptureMultiplier(rules, 3, 2));
        assertEquals(4.0, DominationRuntime.calculateCaptureMultiplier(rules, 5, 1));
    }

    @Test
    void disabledPlayerDifferenceUsesFixedBaseSpeed() {
        MatchRules rules = new MatchRules(GameModeRegistry.DOMINATION);
        rules.captureUsePlayerDifference(false);
        assertEquals(1.0, DominationRuntime.calculateCaptureMultiplier(rules, 10, 1));
    }
}

package com.sfgame.game;

import com.sfgame.data.ArenaPosition;
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.SquareCaptureRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

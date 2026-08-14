package com.sfgame.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CapturePointStateTest {
    @Test
    void capturesNeutralPointAndRequiresNeutralizationBeforeEnemyCapture() {
        CapturePointState state = new CapturePointState();
        assertEquals(CapturePointState.Change.CAPTURED, state.advance(TeamSide.RED, 1.0, false));
        assertEquals(TeamSide.RED, state.owner());

        assertEquals(CapturePointState.Change.NEUTRALIZED, state.advance(TeamSide.BLUE, 1.0, false));
        assertEquals(TeamSide.NONE, state.owner());
        assertEquals(TeamSide.BLUE, state.contender());

        assertEquals(CapturePointState.Change.CAPTURED, state.advance(TeamSide.BLUE, 1.0, false));
        assertEquals(TeamSide.BLUE, state.owner());
    }

    @Test
    void defenderRepairsButOwnedPointDoesNotDecayWhenEmpty() {
        CapturePointState state = new CapturePointState();
        state.advance(TeamSide.RED, 1.0, false);
        state.advance(TeamSide.BLUE, 0.4, false);
        assertEquals(0.6, state.progress(), 0.0001);
        state.advance(TeamSide.NONE, 0.5, true);
        assertEquals(0.6, state.progress(), 0.0001);
        state.advance(TeamSide.RED, 0.4, false);
        assertEquals(1.0, state.progress(), 0.0001);
    }

    @Test
    void neutralProgressFallsBackAndChangingContenderMustClearItFirst() {
        CapturePointState state = new CapturePointState();
        state.advance(TeamSide.RED, 0.6, false);
        state.advance(TeamSide.NONE, 0.2, true);
        assertEquals(0.4, state.progress(), 0.0001);
        state.advance(TeamSide.BLUE, 0.3, false);
        assertEquals(0.1, state.progress(), 0.0001);
        assertEquals(TeamSide.RED, state.contender());
        state.advance(TeamSide.BLUE, 0.2, false);
        assertEquals(0.0, state.progress(), 0.0001);
        assertEquals(TeamSide.BLUE, state.contender());
    }

    @Test
    void canInitializePointAsDefenderOwned() {
        CapturePointState state = new CapturePointState();
        state.reset(TeamSide.BLUE);
        assertEquals(TeamSide.BLUE, state.owner());
        assertEquals(1.0, state.progress(), 0.0001);
        assertEquals(CapturePointState.Change.NEUTRALIZED, state.advance(TeamSide.RED, 1.0, false));
    }
}

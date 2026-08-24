package com.sfgame.game;

import com.sfgame.data.CtfVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaptureTheFlagRuntimeTest {
    @Test
    void forwardFlagsStartPickableOutsideTerritoryMode() {
        assertTrue(CaptureTheFlagRuntime.flagsUnlockedByDefault(CtfVariant.CLASSIC));
        assertTrue(CaptureTheFlagRuntime.flagsUnlockedByDefault(CtfVariant.ASSAULT));
        assertFalse(CaptureTheFlagRuntime.flagsUnlockedByDefault(CtfVariant.TERRITORY));
    }
}

package com.sfgame.data;

import com.sfgame.game.TeamSide;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CaptureTheFlagMapConfigTest {
    private static final ArenaPosition RED_FLAG = new ArenaPosition("minecraft:overworld", 0, 64, 0, 0, 0);
    private static final ArenaPosition BLUE_FLAG = new ArenaPosition("minecraft:overworld", 20, 64, 0, 0, 0);

    @Test
    void normalizesSingleDigitForwardIdsAndPersistsVariant() {
        CaptureTheFlagMapConfig source = new CaptureTheFlagMapConfig();
        source.variant(CtfVariant.TERRITORY);
        source.home(TeamSide.RED).flagPosition(RED_FLAG);
        source.home(TeamSide.RED).captureRegion(new BoxCaptureRegion("minecraft:overworld", -2, 2, -2, 2, null, null));
        source.home(TeamSide.RED).depotPosition(RED_FLAG);
        source.home(TeamSide.BLUE).flagPosition(BLUE_FLAG);
        source.home(TeamSide.BLUE).captureRegion(new BoxCaptureRegion("minecraft:overworld", 18, 22, -2, 2, null, null));
        source.home(TeamSide.BLUE).depotPosition(BLUE_FLAG);
        source.addForward(new CtfForwardFlagDefinition("7", TeamSide.RED,
                new BoxCaptureRegion("minecraft:overworld", 8, 12, -2, 2, null, null),
                new ArenaPosition("minecraft:overworld", 10, 64, 0, 0, 0), 1));

        CaptureTheFlagMapConfig restored = CaptureTheFlagMapConfig.load(source.save());
        assertEquals(CtfVariant.TERRITORY, restored.variant());
        assertTrue(restored.forward("7").isPresent());
        assertTrue(restored.validate(List.of(TeamSide.RED, TeamSide.BLUE)).isEmpty());
    }
}

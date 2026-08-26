package com.sfgame.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SFGameScoreboardOverlayTest {
    @Test
    void centersSidebarLikeVanillaScoreboard() {
        assertEquals(87, SFGameScoreboardOverlay.topFor(240, 6));
        assertEquals(0, SFGameScoreboardOverlay.topFor(20, 6));
    }
}

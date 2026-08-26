package com.sfgame.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamCaptainServiceTest {
    @Test
    void onlyDominationAndCtfOwnRespawnAnchorCaptains() {
        TeamCaptainService service = new TeamCaptainService();
        assertTrue(service.supports(GameModeRegistry.DOMINATION));
        assertTrue(service.supports(GameModeRegistry.CAPTURE_THE_FLAG));
        assertFalse(service.supports(GameModeRegistry.BREAKTHROUGH));
        assertNull(service.captain(TeamSide.RED));
        assertFalse(service.isCaptain(UUID.randomUUID()));
    }
}

package com.sfgame.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminRuleCatalogTest {
    @Test
    void filtersModeSpecificRules() {
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.DOMINATION, "scorePerPoint").isPresent());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH, "scorePerPoint").isPresent());
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.BREAKTHROUGH, "attackerCaptainCaptureWeight").isPresent());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.CAPTURE_THE_FLAG, "attackerCaptainCaptureWeight").isPresent());
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.DOMINATION, "dominationStrategy").isPresent());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH, "dominationStrategy").isPresent());
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.BREAKTHROUGH, "breakthroughVariant").isPresent());
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.CAPTURE_THE_FLAG, "ctfCarrierRestriction").isPresent());
    }

    @Test
    void identifiesLiveAndLobbyOnlySettings() {
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH,
                "mapRestoreAdaptiveThrottling").orElseThrow().hotReload());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH,
                "mapBlockBreaking").orElseThrow().hotReload());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH,
                "startCountdownSeconds").orElseThrow().hotReload());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH,
                "mapSnapshotMode").orElseThrow().hotReload());
    }

    @Test
    void validatesTypedInputAndRanges() {
        AdminRuleCatalog.Definition delay = AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH,
                "mapRestorePartitionDelayTicks").orElseThrow();
        assertEquals(12, AdminRuleCatalog.parse(delay, "12"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(delay, "201"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(delay, "1.5"));

        AdminRuleCatalog.Definition snapshotMode = AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH,
                "mapSnapshotMode").orElseThrow();
        assertEquals("allowlist", AdminRuleCatalog.parse(snapshotMode, "ALLOWLIST"));
        assertEquals("full", AdminRuleCatalog.parse(snapshotMode, "full"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(snapshotMode, "partial"));

        AdminRuleCatalog.Definition roles = AdminRuleCatalog.find(GameModeRegistry.BREAKTHROUGH,
                "breakthroughAttacker").orElseThrow();
        assertEquals("yellow", AdminRuleCatalog.parse(roles, "YELLOW"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(roles, "none"));
        assertEquals(java.util.List.of("red", "blue", "yellow", "green"),
                AdminRuleCatalog.enumValues("breakthroughAttacker"));
    }
}

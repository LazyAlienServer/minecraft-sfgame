package com.sfgame.game;

import com.sfgame.data.MatchRules;
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
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.BREAKTHROUGH, "killCurrency").orElseThrow().hotReload());
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.DOMINATION, "killCurrency").orElseThrow().hotReload());
        assertTrue(AdminRuleCatalog.find(GameModeRegistry.CAPTURE_THE_FLAG, "killCurrency").orElseThrow().hotReload());
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH, "killCurrency").isPresent());
        for (String modeId : java.util.List.of(GameModeRegistry.BREAKTHROUGH,
                GameModeRegistry.CAPTURE_THE_FLAG, GameModeRegistry.DOMINATION)) {
            assertTrue(AdminRuleCatalog.find(modeId, "economyEnabled").isPresent());
            assertFalse(AdminRuleCatalog.find(modeId, "economyEnabled").orElseThrow().hotReload());
        }
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.TEAM_DEATHMATCH, "economyEnabled").isPresent());
        for (String key : java.util.List.of("ctfTerritoryUnlockCurrency", "ctfForwardFlagReplantCurrency",
                "ctfForwardFlagCaptureCurrency", "ctfHomeFlagCaptureCurrency")) {
            assertTrue(AdminRuleCatalog.find(GameModeRegistry.CAPTURE_THE_FLAG, key).orElseThrow().hotReload());
            assertFalse(AdminRuleCatalog.find(GameModeRegistry.DOMINATION, key).isPresent());
        }
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
        assertFalse(AdminRuleCatalog.find(GameModeRegistry.BREAKTHROUGH,
                "breakthroughLegs").orElseThrow().hotReload());
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

        AdminRuleCatalog.Definition rounds = AdminRuleCatalog.find(
                GameModeRegistry.BREAKTHROUGH, "breakthroughAttackRounds").orElseThrow();
        assertEquals(100, AdminRuleCatalog.parse(rounds, "100"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(rounds, "101"));
        AdminRuleCatalog.Definition time = AdminRuleCatalog.find(
                GameModeRegistry.TEAM_DEATHMATCH, "timeLimitSeconds").orElseThrow();
        assertEquals(MatchRules.UNLIMITED_TIME_SECONDS, AdminRuleCatalog.parse(time, "-1"));
        AdminRuleCatalog.Definition tickets = AdminRuleCatalog.find(
                GameModeRegistry.BREAKTHROUGH, "attackerTickets").orElseThrow();
        assertEquals(MatchRules.UNLIMITED_TICKETS, AdminRuleCatalog.parse(tickets, "-1"));
        AdminRuleCatalog.Definition legs = AdminRuleCatalog.find(
                GameModeRegistry.BREAKTHROUGH, "breakthroughLegs").orElseThrow();
        assertEquals(1, AdminRuleCatalog.parse(legs, "1"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(legs, "2"));
        AdminRuleCatalog.Definition roles = AdminRuleCatalog.find(GameModeRegistry.BREAKTHROUGH,
                "breakthroughAttacker").orElseThrow();
        assertEquals("yellow", AdminRuleCatalog.parse(roles, "YELLOW"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(roles, "none"));
        assertEquals(java.util.List.of("red", "blue", "yellow", "green"),
                AdminRuleCatalog.enumValues("breakthroughAttacker"));
    }

    @Test
    void validatesUnlimitedAndMillionPlayerLimits() {
        AdminRuleCatalog.Definition maxPlayers = AdminRuleCatalog.find(
                GameModeRegistry.TEAM_DEATHMATCH, "maxPlayers").orElseThrow();

        assertEquals(-1, AdminRuleCatalog.parse(maxPlayers, "-1"));
        assertEquals(1_000_000, AdminRuleCatalog.parse(maxPlayers, "1000000"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(maxPlayers, "-2"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(maxPlayers, "0"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(maxPlayers, "1"));
        assertThrows(IllegalArgumentException.class, () -> AdminRuleCatalog.parse(maxPlayers, "1000001"));
    }
}

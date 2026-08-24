package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.SupplyOfferDefinition;
import com.sfgame.data.SupplyTriggerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupplyServiceTest {
    @Test
    void firesOneShotAndRepeatingTimeTriggersAtConfiguredTicks() {
        ArenaMap map = map();
        map.supply().addOffer(SupplyOfferDefinition.item("apples", "minecraft:apple", 2, ""));
        map.supply().addTrigger(new SupplyTriggerDefinition("one", "match_time", "apples", "red",
                1, 0, "", 2, 0, ""));
        map.supply().addTrigger(new SupplyTriggerDefinition("repeat", "match_time", "apples", "blue",
                2, 0, "", 1, 2, ""));
        SupplyService service = new SupplyService();
        service.beginRunning(map, GameModeRegistry.DOMINATION, TeamSide.NONE, TeamSide.NONE);

        assertFalse(service.tick(19));
        assertTrue(service.tick(20));
        assertEquals(2, service.item(TeamSide.BLUE, "apples").orElseThrow().quantity());
        assertTrue(service.tick(40));
        assertEquals(1, service.item(TeamSide.RED, "apples").orElseThrow().quantity());
        assertFalse(service.tick(41));
        assertTrue(service.tick(60));
        assertEquals(4, service.item(TeamSide.BLUE, "apples").orElseThrow().quantity());
        assertTrue(service.tick(100));
        assertEquals(6, service.item(TeamSide.BLUE, "apples").orElseThrow().quantity());
        assertEquals(1, service.item(TeamSide.RED, "apples").orElseThrow().quantity());
    }

    @Test
    void stageSectorAndCaptureTriggersFireOnce() {
        ArenaMap map = map();
        map.supply().addOffer(SupplyOfferDefinition.item("apples", "minecraft:apple", 1, ""));
        map.supply().addTrigger(new SupplyTriggerDefinition("stage", "breakthrough_stage", "apples", "attacker",
                1, 2, "", 0, 0, ""));
        map.supply().addTrigger(new SupplyTriggerDefinition("sector", "breakthrough_sector", "apples", "defender",
                2, 0, "second", 0, 0, ""));
        SupplyService service = new SupplyService();
        service.beginRunning(map, GameModeRegistry.BREAKTHROUGH, TeamSide.RED, TeamSide.BLUE);

        assertFalse(service.fireEvent(SupplyTriggerDefinition.BREAKTHROUGH_STAGE, TeamSide.NONE, 1, "", ""));
        assertTrue(service.fireEvent(SupplyTriggerDefinition.BREAKTHROUGH_STAGE, TeamSide.NONE, 2, "", ""));
        assertFalse(service.fireEvent(SupplyTriggerDefinition.BREAKTHROUGH_STAGE, TeamSide.NONE, 2, "", ""));
        assertTrue(service.fireEvent(SupplyTriggerDefinition.BREAKTHROUGH_SECTOR, TeamSide.NONE, 0, "second", ""));
        assertEquals(1, service.item(TeamSide.RED, "apples").orElseThrow().quantity());
        assertEquals(2, service.item(TeamSide.BLUE, "apples").orElseThrow().quantity());

        ArenaMap captureMap = map();
        captureMap.supply().addOffer(SupplyOfferDefinition.item("apples", "minecraft:apple", 1, ""));
        captureMap.supply().addTrigger(new SupplyTriggerDefinition("capture", "ctf_capture", "apples", "event",
                3, 0, "", 0, 0, ""));
        service.beginRunning(captureMap, GameModeRegistry.CAPTURE_THE_FLAG, TeamSide.RED, TeamSide.BLUE);
        assertTrue(service.fireEvent(SupplyTriggerDefinition.CTF_CAPTURE, TeamSide.BLUE, 0, "", ""));
        assertFalse(service.fireEvent(SupplyTriggerDefinition.CTF_CAPTURE, TeamSide.BLUE, 0, "", ""));
        assertEquals(3, service.item(TeamSide.BLUE, "apples").orElseThrow().quantity());
    }

    @Test
    void roleTargetsUseCurrentBreakthroughRuntimeRoles() {
        ArenaMap map = map();
        map.supply().addOffer(SupplyOfferDefinition.item("apples", "minecraft:apple", 1, ""));
        map.supply().addTrigger(new SupplyTriggerDefinition("stage", "breakthrough_stage", "apples",
                "attacker", 1, 2, "", 0, 0, ""));
        SupplyService service = new SupplyService();
        service.beginRunning(map, GameModeRegistry.BREAKTHROUGH, TeamSide.RED, TeamSide.BLUE);
        service.updateRoles(TeamSide.BLUE, TeamSide.RED);

        assertTrue(service.fireEvent(SupplyTriggerDefinition.BREAKTHROUGH_STAGE, TeamSide.NONE, 2, "", ""));
        assertEquals(1, service.item(TeamSide.BLUE, "apples").orElseThrow().quantity());
        assertTrue(service.item(TeamSide.RED, "apples").isEmpty());
    }

    @Test
    void mergesIdenticalStockRejectsConflictsAndRemovesAtZero() {
        SupplyService service = new SupplyService();
        SupplyService.PublishedSupply apples = new SupplyService.PublishedSupply(
                "emergency", "item", "minecraft:apple", 2, "", "", 0);
        SupplyService.PublishedSupply bread = new SupplyService.PublishedSupply(
                "emergency", "item", "minecraft:bread", 2, "", "", 0);

        assertTrue(service.publish(TeamSide.RED, apples, 2));
        assertTrue(service.publish(TeamSide.RED, apples, 3));
        assertEquals(5, service.item(TeamSide.RED, "emergency").orElseThrow().quantity());
        assertFalse(service.publish(TeamSide.RED, bread, 1));
        assertTrue(service.consume(TeamSide.RED, "emergency"));
        assertEquals(4, service.item(TeamSide.RED, "emergency").orElseThrow().quantity());
        for (int i = 0; i < 4; i++) assertTrue(service.consume(TeamSide.RED, "emergency"));
        assertTrue(service.item(TeamSide.RED, "emergency").isEmpty());
        assertFalse(service.consume(TeamSide.RED, "emergency"));
    }

    private static ArenaMap map() {
        ArenaMap map = new ArenaMap("arena");
        ArenaPosition spawn = new ArenaPosition("minecraft:overworld", 0, 64, 0, 0, 0);
        map.addSpawn(TeamSide.RED, spawn);
        map.addSpawn(TeamSide.BLUE, spawn);
        return map;
    }
}

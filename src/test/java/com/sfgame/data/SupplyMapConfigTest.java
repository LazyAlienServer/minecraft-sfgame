package com.sfgame.data;

import com.google.gson.JsonObject;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupplyMapConfigTest {
    @Test
    void arenaMapJsonRoundTripsItemEliteAndTriggers() {
        ArenaMap map = new ArenaMap("arena");
        map.supply().addOffer(SupplyOfferDefinition.item(
                "field_apples", "minecraft:golden_apple", 2, ""));
        map.supply().addOffer(SupplyOfferDefinition.elite("elite_assault", "elite_assault"));
        map.supply().addTrigger(new SupplyTriggerDefinition(
                "stage_2_elite", "breakthrough_stage", "elite_assault", "attacker", 1,
                2, "", 0, 0, ""));
        map.supply().addTrigger(new SupplyTriggerDefinition(
                "red_apples_120", "match_time", "field_apples", "red", 4,
                0, "", 120, 120, ""));

        JsonObject json = MapConfigJson.write(map);
        JsonObject supply = json.getAsJsonObject("supply");
        assertEquals(2, supply.getAsJsonArray("offers").size());
        assertEquals("elite_class", supply.getAsJsonArray("offers").get(1).getAsJsonObject().get("type").getAsString());
        assertEquals(120, supply.getAsJsonArray("triggers").get(1).getAsJsonObject().get("repeatSeconds").getAsInt());

        ArenaMap restored = MapConfigJson.read(json);
        assertEquals(map.supply().offers(), restored.supply().offers());
        assertEquals(map.supply().triggers(), restored.supply().triggers());
        assertTrue(restored.hasLocalConfiguration());
    }

    @Test
    void rejectsDuplicateIdsInvalidOffersAndTriggerBounds() {
        SupplyMapConfig config = new SupplyMapConfig();
        config.addOffer(SupplyOfferDefinition.item("apples", "minecraft:apple", 1, ""));
        assertThrows(IllegalArgumentException.class,
                () -> config.addOffer(SupplyOfferDefinition.item("apples", "minecraft:bread", 1, "")));
        assertThrows(IllegalArgumentException.class,
                () -> SupplyOfferDefinition.item("bad", "not an item", 1, ""));
        assertThrows(IllegalArgumentException.class,
                () -> SupplyOfferDefinition.item("bad", "minecraft:apple", 65, ""));
        assertThrows(IllegalArgumentException.class,
                () -> SupplyOfferDefinition.elite("bad", ""));
        assertThrows(IllegalArgumentException.class, () -> new SupplyTriggerDefinition(
                "bad", "breakthrough_stage", "apples", "red", 0, 0, "", 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new SupplyTriggerDefinition(
                "bad", "breakthrough_sector", "apples", "red", 1, 0, "", 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new SupplyTriggerDefinition(
                "bad", "match_time", "apples", "red", 1, 0, "", -1, 0, ""));
    }

    @Test
    void validatesMissingOffersModesTargetsRolesAndElitePools() {
        List<TeamSide> teams = List.of(TeamSide.RED, TeamSide.BLUE);

        SupplyMapConfig missing = configWithTrigger("ctf_capture", "missing", "event");
        assertContains(missing.validate(GameModeRegistry.CAPTURE_THE_FLAG, teams,
                TeamSide.RED, TeamSide.BLUE, (side, id) -> true), "missing supply offer");

        SupplyMapConfig wrongMode = configWithOfferAndTrigger("ctf_capture", "red");
        assertContains(wrongMode.validate(GameModeRegistry.DOMINATION, teams,
                TeamSide.NONE, TeamSide.NONE, (side, id) -> true), "unavailable in domination");

        SupplyMapConfig eventTime = configWithOfferAndTrigger("match_time", "event");
        assertContains(eventTime.validate(GameModeRegistry.BREAKTHROUGH, teams,
                TeamSide.RED, TeamSide.BLUE, (side, id) -> true), "event target");

        SupplyMapConfig disabled = configWithOfferAndTrigger("match_time", "green");
        assertContains(disabled.validate(GameModeRegistry.DOMINATION, teams,
                TeamSide.NONE, TeamSide.NONE, (side, id) -> true), "not enabled");

        SupplyMapConfig unresolved = configWithOfferAndTrigger("match_time", "attacker");
        assertContains(unresolved.validate(GameModeRegistry.DOMINATION, teams,
                TeamSide.NONE, TeamSide.NONE, (side, id) -> true), "unresolved target role");

        SupplyMapConfig elite = new SupplyMapConfig();
        elite.addOffer(SupplyOfferDefinition.elite("elite", "elite_assault"));
        elite.addTrigger(new SupplyTriggerDefinition("elite_capture", "ctf_capture", "elite", "event",
                1, 0, "", 0, 0, ""));
        List<String> errors = elite.validate(GameModeRegistry.CAPTURE_THE_FLAG, teams,
                TeamSide.RED, TeamSide.BLUE, (side, id) -> side == TeamSide.RED);
        assertContains(errors, "unavailable for blue");
    }

    private static SupplyMapConfig configWithTrigger(String event, String offerId, String target) {
        SupplyMapConfig config = new SupplyMapConfig();
        config.addTrigger(new SupplyTriggerDefinition("trigger", event, offerId, target,
                1, event.equals("breakthrough_stage") ? 1 : 0,
                event.equals("breakthrough_sector") ? "a" : "", 0, 0, ""));
        return config;
    }

    private static SupplyMapConfig configWithOfferAndTrigger(String event, String target) {
        SupplyMapConfig config = new SupplyMapConfig();
        config.addOffer(SupplyOfferDefinition.item("offer", "minecraft:apple", 1, ""));
        config.addTrigger(new SupplyTriggerDefinition("trigger", event, "offer", target,
                1, event.equals("breakthrough_stage") ? 1 : 0,
                event.equals("breakthrough_sector") ? "a" : "", 0, 0, ""));
        return config;
    }

    private static void assertContains(List<String> errors, String text) {
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains(text)), errors.toString());
    }
}

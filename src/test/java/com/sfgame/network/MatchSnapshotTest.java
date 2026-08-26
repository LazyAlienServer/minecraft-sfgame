package com.sfgame.network;

import io.netty.buffer.Unpooled;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSnapshotTest {
    @Test
    void classViewPreservesIconRenderingConfiguration() {
        MatchSnapshot.ClassView expected = new MatchSnapshot.ClassView(
                "assault", "Assault", "Frontline", "tacz:modern_kinetic_gun", "png",
                "example:textures/gui/classes/hk416d.png", "tacz:hk416d", 20.0, 1.05, 90);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.encode(buffer);

            assertEquals(expected, MatchSnapshot.ClassView.decode(buffer));
        } finally {
            buffer.release();
        }
    }
    @Test
    void roundTripsGenericEconomyAndShopFields() {
        MatchSnapshot expected = new MatchSnapshot(
                GameModeRegistry.DOMINATION, "Default", MatchPhase.RUNNING, TeamSide.RED,
                1, 2, 3, 4, 100, 321, true, 1, 2, 3, 4,
                "assault", "medic", true, false, List.of(),
                "", TeamSide.NONE, TeamSide.NONE, 0, true, 0, 0, 0, 0, "",
                null, null, 0, false, null, null, List.of(), List.of(new MatchSnapshot.CaptainCandidate("old", "Old")),
                "new", "New", 5, true, List.of(new MatchSnapshot.CaptainCandidate("anchor", "Anchor")),
                false, List.of(new MatchSnapshot.RespawnOption("squad:anchor", "squad", "Anchor")),
                null, null, true, 73, true, List.of(),
                List.of(new MatchSnapshot.ShopView("medkit", "医疗包", "minecraft:golden_apple", 50)),
                List.of(new MatchSnapshot.SupplyView(
                        "elite_drop", "elite_class", "精英突击", "minecraft:iron_sword", 2)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.encode(buffer);
            MatchSnapshot actual = MatchSnapshot.decode(buffer);
            assertEquals(expected, actual);
            assertEquals("Default", actual.mapName());
            assertTrue(actual.economyEnabled());
            assertTrue(actual.showUnlimitedTime());
            assertTrue(actual.showUnlimitedTickets());
            assertTrue(actual.devMode());
            assertEquals(73, actual.currency());
            assertEquals("medkit", actual.shopItems().get(0).id());
            assertEquals(2, actual.supplyItems().get(0).quantity());
        } finally {
            buffer.release();
        }
    }

}

package com.sfgame.network;

import com.sfgame.game.TeamSide;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquadSnapshotTest {
    @Test
    void roundTripsRosterOccupancyAndBeaconHealth() {
        UUID member = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SquadSnapshot expected = new SquadSnapshot(TeamSide.RED, 3, 2, 1, 42.0F, 50.0F,
                List.of(new SquadSnapshot.SquadView(1, 1,
                        List.of(new SquadSnapshot.MemberView(member, "Alpha", true, true, false)))));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.encode(buffer);
            assertEquals(expected, SquadSnapshot.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}

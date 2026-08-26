package com.sfgame.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AdminSnapshotTest {
    @Test
    void mapViewRoundTripsDisplayNameAndId() {
        AdminSnapshot.MapView expected = new AdminSnapshot.MapView("default", "默认", true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            expected.encode(buffer);
            assertEquals(expected, AdminSnapshot.MapView.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}

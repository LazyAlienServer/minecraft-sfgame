package com.sfgame.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

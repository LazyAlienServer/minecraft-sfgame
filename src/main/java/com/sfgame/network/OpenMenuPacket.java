package com.sfgame.network;

import com.sfgame.client.ClientMatchState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class OpenMenuPacket {
    public static void encode(OpenMenuPacket packet, FriendlyByteBuf buffer) {}
    public static OpenMenuPacket decode(FriendlyByteBuf buffer) { return new OpenMenuPacket(); }

    public static void handle(OpenMenuPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientMatchState::openScreen));
        context.setPacketHandled(true);
    }
}

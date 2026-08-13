package com.sfgame.network;

import com.sfgame.client.ClientMatchState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SnapshotPacket(MatchSnapshot snapshot) {
    public static void encode(SnapshotPacket packet, FriendlyByteBuf buffer) {
        packet.snapshot.encode(buffer);
    }

    public static SnapshotPacket decode(FriendlyByteBuf buffer) {
        return new SnapshotPacket(MatchSnapshot.decode(buffer));
    }

    public static void handle(SnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMatchState.update(packet.snapshot)));
        context.setPacketHandled(true);
    }
}

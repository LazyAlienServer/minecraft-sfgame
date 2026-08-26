package com.sfgame.network;

import com.sfgame.client.ClientSquadState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SquadSnapshotPacket(SquadSnapshot snapshot) {
    public static void encode(SquadSnapshotPacket packet, FriendlyByteBuf buffer) {
        packet.snapshot.encode(buffer);
    }

    public static SquadSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new SquadSnapshotPacket(SquadSnapshot.decode(buffer));
    }

    public static void handle(SquadSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSquadState.update(packet.snapshot)));
        context.setPacketHandled(true);
    }
}

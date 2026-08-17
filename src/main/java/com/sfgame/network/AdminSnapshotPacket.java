package com.sfgame.network;

import com.sfgame.client.ClientAdminState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AdminSnapshotPacket(AdminSnapshot snapshot, boolean openScreen) {
    public static void encode(AdminSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.openScreen);
        packet.snapshot.encode(buffer);
    }

    public static AdminSnapshotPacket decode(FriendlyByteBuf buffer) {
        boolean open = buffer.readBoolean();
        return new AdminSnapshotPacket(AdminSnapshot.decode(buffer), open);
    }

    public static void handle(AdminSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientAdminState.update(packet.snapshot, packet.openScreen)));
        context.setPacketHandled(true);
    }
}

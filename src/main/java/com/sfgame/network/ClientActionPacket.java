package com.sfgame.network;

import com.sfgame.game.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientActionPacket(Action action, String value) {
    public enum Action { REQUEST_SNAPSHOT, JOIN, LEAVE, SELECT_CLASS }

    public static void encode(ClientActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.value == null ? "" : packet.value, 64);
    }

    public static ClientActionPacket decode(FriendlyByteBuf buffer) {
        return new ClientActionPacket(buffer.readEnum(Action.class), buffer.readUtf(64));
    }

    public static void handle(ClientActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            MatchManager manager = MatchManager.get();
            switch (packet.action) {
                case REQUEST_SNAPSHOT -> SFGameNetwork.sendSnapshot(player, manager.snapshot(player));
                case JOIN -> manager.queueOrJoinLobby(player);
                case LEAVE -> manager.leave(player);
                case SELECT_CLASS -> manager.selectClass(player, packet.value);
            }
        });
        context.setPacketHandled(true);
    }
}


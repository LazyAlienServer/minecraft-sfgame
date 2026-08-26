package com.sfgame.network;

import com.sfgame.game.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SquadActionPacket(Action action, int squadIndex) {
    public enum Action { REQUEST, JOIN, LEAVE }

    public static void encode(SquadActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeVarInt(Math.max(0, Math.min(64, packet.squadIndex)));
    }

    public static SquadActionPacket decode(FriendlyByteBuf buffer) {
        return new SquadActionPacket(buffer.readEnum(Action.class), buffer.readVarInt());
    }

    public static void handle(SquadActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            MatchManager manager = MatchManager.get();
            switch (packet.action) {
                case REQUEST -> { }
                case JOIN -> {
                    if (!manager.squads().join(player, packet.squadIndex)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "sfgame.squad.join_failed"), true);
                    }
                }
                case LEAVE -> {
                    if (!manager.squads().leave(player)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "sfgame.squad.leave_failed"), true);
                    }
                }
            }
            SFGameNetwork.sendSquadSnapshot(player, manager.squads().snapshot(player));
        });
        context.setPacketHandled(true);
    }
}

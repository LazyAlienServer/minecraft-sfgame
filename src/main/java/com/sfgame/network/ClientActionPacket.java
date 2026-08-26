package com.sfgame.network;

import com.sfgame.game.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClientActionPacket(Action action, String value) {
    public enum Action { REQUEST_SNAPSHOT, JOIN, LEAVE, SELECT_CLASS, SELECT_CAPTAIN_CLASS, CAPTAIN_VOTE, CAPTAIN_ABSTAIN,
        SELECT_RESPAWN, SHOP_BUY, SUPPLY_CLAIM }

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
                case JOIN -> manager.joinFromMenu(player);
                case LEAVE -> manager.leaveFromMenu(player);
                case SELECT_CLASS -> manager.selectClass(player, packet.value);
                case SELECT_CAPTAIN_CLASS -> manager.selectCaptainClass(player, packet.value);
                case CAPTAIN_ABSTAIN -> {
                    MatchSnapshot snapshot = manager.snapshot(player);
                    if (snapshot.electionSeconds() > 0) {
                        manager.breakthrough().vote(player, null, true, manager);
                    } else if (snapshot.anchorElectionSeconds() > 0) {
                        manager.voteAnchorCaptain(player, null, true);
                    }
                }
                case CAPTAIN_VOTE -> {
                    try {
                        ServerPlayer candidate = player.server.getPlayerList().getPlayer(java.util.UUID.fromString(packet.value));
                        MatchSnapshot snapshot = manager.snapshot(player);
                        if (snapshot.electionSeconds() > 0) {
                            manager.breakthrough().vote(player, candidate, false, manager);
                        } else if (snapshot.anchorElectionSeconds() > 0) {
                            manager.voteAnchorCaptain(player, candidate, false);
                        }
                    } catch (IllegalArgumentException ignored) { }
                }
                case SELECT_RESPAWN -> manager.selectRespawn(player, packet.value);
                case SHOP_BUY -> manager.purchase(player, packet.value);
                case SUPPLY_CLAIM -> {
                    if (!manager.claimSupply(player, packet.value)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "sfgame.supply.claim_failed"), true);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}

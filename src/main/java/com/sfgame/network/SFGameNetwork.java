package com.sfgame.network;

import com.sfgame.SFGame;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SFGameNetwork {
    private static final String PROTOCOL = "8";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(SFGame.MOD_ID + ":main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SFGameNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ClientActionPacket.class, ClientActionPacket::encode, ClientActionPacket::decode,
                ClientActionPacket::handle);
        CHANNEL.registerMessage(id++, SnapshotPacket.class, SnapshotPacket::encode, SnapshotPacket::decode,
                SnapshotPacket::handle);
        CHANNEL.registerMessage(id++, OpenMenuPacket.class, OpenMenuPacket::encode, OpenMenuPacket::decode,
                OpenMenuPacket::handle);
        CHANNEL.registerMessage(id++, AdminActionPacket.class, AdminActionPacket::encode, AdminActionPacket::decode,
                AdminActionPacket::handle);
        CHANNEL.registerMessage(id, AdminSnapshotPacket.class, AdminSnapshotPacket::encode, AdminSnapshotPacket::decode,
                AdminSnapshotPacket::handle);
    }

    public static void sendSnapshot(ServerPlayer player, MatchSnapshot snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnapshotPacket(snapshot));
    }

    public static void openMenu(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenMenuPacket());
        sendSnapshot(player, com.sfgame.game.MatchManager.get().snapshot(player));
    }

    public static void sendToServer(ClientActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(AdminActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendAdminSnapshot(ServerPlayer player, boolean openScreen) {
        if (!player.hasPermissions(2)) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new AdminSnapshotPacket(AdminSnapshot.create(player), openScreen));
    }
}

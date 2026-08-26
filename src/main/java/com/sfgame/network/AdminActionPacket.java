package com.sfgame.network;

import com.sfgame.data.SFGameSavedData;
import com.sfgame.game.AdminRuleCatalog;
import com.sfgame.game.MatchManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** All administrator mutations are revalidated on the logical server. */
public record AdminActionPacket(Action action, String modeId, String mapId, String key, String value) {
    public enum Action { REQUEST, OPEN, SELECT_MODE, SELECT_MAP, SET_RULE }

    public static AdminActionPacket request(boolean open) {
        return new AdminActionPacket(open ? Action.OPEN : Action.REQUEST, "", "", "", "");
    }

    public static AdminActionPacket selectMode(String modeId) {
        return new AdminActionPacket(Action.SELECT_MODE, modeId, "", "", "");
    }

    public static AdminActionPacket selectMap(String modeId, String mapId) {
        return new AdminActionPacket(Action.SELECT_MAP, modeId, mapId, "", "");
    }

    public static AdminActionPacket setRule(String modeId, String mapId, String key, String value) {
        return new AdminActionPacket(Action.SET_RULE, modeId, mapId, key, value);
    }

    public static void encode(AdminActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUtf(safe(packet.modeId), 64);
        buffer.writeUtf(safe(packet.mapId), 64);
        buffer.writeUtf(safe(packet.key), 64);
        buffer.writeUtf(safe(packet.value), 128);
    }

    public static AdminActionPacket decode(FriendlyByteBuf buffer) {
        return new AdminActionPacket(buffer.readEnum(Action.class), buffer.readUtf(64), buffer.readUtf(64),
                buffer.readUtf(64), buffer.readUtf(128));
    }

    public static void handle(AdminActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            MatchManager manager = MatchManager.get();
            SFGameSavedData data = SFGameSavedData.get(player.server);
            boolean open = packet.action == Action.OPEN;
            Component feedback = null;
            try {
                switch (packet.action) {
                    case REQUEST, OPEN -> { }
                    case SELECT_MODE -> {
                        requireArenaEditable(manager);
                        if (!data.selectMode(packet.modeId)) {
                            throw new IllegalArgumentException("Unknown game mode: " + packet.modeId);
                        }
                        manager.arenaSelectionChanged();
                        manager.refreshCommandTree();
                        feedback = Component.translatable("sfgame.admin.feedback.mode.colored", packet.modeId);
                    }
                    case SELECT_MAP -> {
                        requireArenaEditable(manager);
                        if (!data.selectedMode().equals(packet.modeId)) {
                            throw new IllegalStateException("The selected mode changed; refresh the panel");
                        }
                        if (!data.selectMap(packet.mapId)) {
                            throw new IllegalArgumentException("Unknown map: " + packet.mapId);
                        }
                        manager.arenaSelectionChanged();
                        feedback = Component.translatable("sfgame.admin.feedback.map.colored", packet.mapId);
                    }
                    case SET_RULE -> {
                        applyRule(manager, data, packet);
                        feedback = Component.translatable("sfgame.admin.feedback.rule.colored", packet.key, packet.value);
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                Component error = Component.translatable("sfgame.admin.error", exception.getMessage());
                player.sendSystemMessage(error);
                player.displayClientMessage(error, true);
            }
            if (feedback != null) player.displayClientMessage(feedback, true);
            SFGameNetwork.sendAdminSnapshot(player, open);
            SFGameNetwork.sendSnapshot(player, manager.snapshot(player));
        });
        context.setPacketHandled(true);
    }

    private static void applyRule(MatchManager manager, SFGameSavedData data, AdminActionPacket packet) {
        if (!data.selectedMode().equals(packet.modeId) || !data.selectedMap().equals(packet.mapId)) {
            throw new IllegalStateException("The mode or map changed; refresh the panel");
        }
        AdminRuleCatalog.Definition definition = AdminRuleCatalog.find(data.selectedMode(), packet.key)
                .orElseThrow(() -> new IllegalArgumentException("Rule is unavailable in this mode: " + packet.key));
        if (!definition.hotReload() && !manager.canChangeArena()) {
            throw new IllegalStateException("This rule can only be changed outside a match");
        }
        Object parsed = AdminRuleCatalog.parse(definition, packet.value);
        switch (definition.type()) {
            case INTEGER -> manager.setRule(definition.key(), (Integer) parsed);
            case DECIMAL -> manager.setRule(definition.key(), (Double) parsed);
            case BOOLEAN -> manager.setRule(definition.key(), (Boolean) parsed);
            case ENUM -> manager.setRule(definition.key(), (String) parsed);
        }
    }

    private static void requireArenaEditable(MatchManager manager) {
        if (!manager.canChangeArena()) throw new IllegalStateException("Mode and map cannot change during a match");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

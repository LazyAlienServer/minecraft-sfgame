package com.sfgame.network;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.MatchRules;
import com.sfgame.data.SFGameSavedData;
import com.sfgame.game.AdminRuleCatalog;
import com.sfgame.game.GameModeDefinition;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.MatchManager;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.PlayerMatchState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** A permission-gated snapshot consumed only by the administrator screen. */
public record AdminSnapshot(String selectedMode, String selectedMap, MatchPhase phase,
                            boolean mapConfigured, boolean devMode, String ruleParent,
                            int onlinePlayers, int participatingPlayers, int queuedPlayers,
                            int remainingSeconds, int redScore, int blueScore, int yellowScore, int greenScore,
                            boolean restoringMap, double restoreProgress, long restoreElapsedMillis,
                            int restoredPartitions, int totalPartitions,
                            List<ModeView> modes, List<RuleView> rules) {
    public record MapView(String id, boolean configured) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeBoolean(configured);
        }

        static MapView decode(FriendlyByteBuf buffer) {
            return new MapView(buffer.readUtf(64), buffer.readBoolean());
        }
    }

    public record ModeView(String id, String name, List<MapView> maps) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeUtf(name, 128);
            buffer.writeVarInt(maps.size());
            maps.forEach(map -> map.encode(buffer));
        }

        static ModeView decode(FriendlyByteBuf buffer) {
            String id = buffer.readUtf(64);
            String name = buffer.readUtf(128);
            int size = buffer.readVarInt();
            List<MapView> maps = new ArrayList<>(size);
            for (int i = 0; i < size; i++) maps.add(MapView.decode(buffer));
            return new ModeView(id, name, List.copyOf(maps));
        }
    }

    public record RuleView(String key, String value, AdminRuleCatalog.ValueType type,
                           double minimum, double maximum, boolean hotReload) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(key, 64);
            buffer.writeUtf(value, 128);
            buffer.writeEnum(type);
            buffer.writeDouble(minimum);
            buffer.writeDouble(maximum);
            buffer.writeBoolean(hotReload);
        }

        static RuleView decode(FriendlyByteBuf buffer) {
            return new RuleView(buffer.readUtf(64), buffer.readUtf(128),
                    buffer.readEnum(AdminRuleCatalog.ValueType.class), buffer.readDouble(),
                    buffer.readDouble(), buffer.readBoolean());
        }
    }

    public static AdminSnapshot create(ServerPlayer viewer) {
        MatchManager manager = MatchManager.get();
        SFGameSavedData data = SFGameSavedData.get(viewer.server);
        MatchRules currentRules = manager.rules();
        List<ModeView> modeViews = new ArrayList<>();
        for (GameModeDefinition mode : GameModeRegistry.all()) {
            List<MapView> maps = data.maps(mode.id()).stream()
                    .map(map -> new MapView(map.id(), data.mapConfigured(map, mode.id())))
                    .toList();
            modeViews.add(new ModeView(mode.id(), mode.displayName(), maps));
        }
        List<RuleView> ruleViews = AdminRuleCatalog.forMode(data.selectedMode()).stream()
                .map(definition -> new RuleView(definition.key(), definition.value(currentRules), definition.type(),
                        definition.minimum(), definition.maximum(), definition.hotReload()))
                .toList();

        int participating = 0;
        int queued = 0;
        for (PlayerMatchState state : manager.playerStates()) {
            if (state.participating()) participating++;
            if (state.queued()) queued++;
        }

        ArenaMap activeMap = data.activeMap();
        return new AdminSnapshot(data.selectedMode(), data.selectedMap(), manager.phase(),
                activeMap != null && data.mapConfigured(activeMap), data.devMode(),
                manager.ruleConfigs().parent(data.selectedMode(), data.selectedMap()),
                viewer.server.getPlayerList().getPlayerCount(), participating, queued,
                manager.remainingSeconds(), manager.redScore(), manager.blueScore(), manager.yellowScore(), manager.greenScore(),
                manager.restoringMap(), Math.max(0.0, Math.min(1.0, manager.mapRestoreProgress())),
                Math.max(0L, manager.mapRestoreElapsedMillis()), Math.max(0, manager.restoredPartitions()),
                Math.max(0, manager.totalRestorePartitions()), List.copyOf(modeViews), ruleViews);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(selectedMode, 64);
        buffer.writeUtf(selectedMap, 64);
        buffer.writeEnum(phase);
        buffer.writeBoolean(mapConfigured);
        buffer.writeBoolean(devMode);
        buffer.writeUtf(ruleParent, 64);
        buffer.writeVarInt(onlinePlayers);
        buffer.writeVarInt(participatingPlayers);
        buffer.writeVarInt(queuedPlayers);
        buffer.writeVarInt(remainingSeconds);
        buffer.writeVarInt(redScore);
        buffer.writeVarInt(blueScore);
        buffer.writeVarInt(yellowScore);
        buffer.writeVarInt(greenScore);
        buffer.writeBoolean(restoringMap);
        buffer.writeDouble(restoreProgress);
        buffer.writeVarLong(restoreElapsedMillis);
        buffer.writeVarInt(restoredPartitions);
        buffer.writeVarInt(totalPartitions);
        buffer.writeVarInt(modes.size());
        modes.forEach(mode -> mode.encode(buffer));
        buffer.writeVarInt(rules.size());
        rules.forEach(rule -> rule.encode(buffer));
    }

    public static AdminSnapshot decode(FriendlyByteBuf buffer) {
        String mode = buffer.readUtf(64);
        String map = buffer.readUtf(64);
        MatchPhase phase = buffer.readEnum(MatchPhase.class);
        boolean configured = buffer.readBoolean();
        boolean dev = buffer.readBoolean();
        String parent = buffer.readUtf(64);
        int online = buffer.readVarInt();
        int participating = buffer.readVarInt();
        int queued = buffer.readVarInt();
        int remaining = buffer.readVarInt();
        int red = buffer.readVarInt();
        int blue = buffer.readVarInt();
        int yellow = buffer.readVarInt();
        int green = buffer.readVarInt();
        boolean restoring = buffer.readBoolean();
        double progress = buffer.readDouble();
        long elapsed = buffer.readVarLong();
        int restored = buffer.readVarInt();
        int total = buffer.readVarInt();
        int modeCount = buffer.readVarInt();
        List<ModeView> modes = new ArrayList<>(modeCount);
        for (int i = 0; i < modeCount; i++) modes.add(ModeView.decode(buffer));
        int ruleCount = buffer.readVarInt();
        List<RuleView> rules = new ArrayList<>(ruleCount);
        for (int i = 0; i < ruleCount; i++) rules.add(RuleView.decode(buffer));
        return new AdminSnapshot(mode, map, phase, configured, dev, parent, online, participating, queued,
                remaining, red, blue, yellow, green, restoring, progress, elapsed, restored, total,
                List.copyOf(modes), List.copyOf(rules));
    }

}

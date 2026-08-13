package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.MatchRules;
import com.sfgame.data.PointActivationStrategy;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DominationRuntime implements MatchModeRuntime {
    private final Map<String, CapturePointState> states = new HashMap<>();
    private final Map<String, ServerBossEvent> bossBars = new HashMap<>();
    private int scoreTicks;
    private int syncHoldTicks;
    private int syncIndex;

    @Override
    public List<String> validate(MinecraftServer server, ArenaMap map) {
        List<String> errors = new ArrayList<>(map.domination().validate());
        for (CapturePointDefinition point : map.domination().points()) {
            ResourceLocation id = ResourceLocation.tryParse(point.region().dimension());
            net.minecraft.server.level.ServerLevel level = id == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level == null) {
                errors.add("Capture point " + point.id() + " uses an unavailable dimension: " + point.region().dimension());
                continue;
            }
            if (point.region().minY() != null && (point.region().minY() < level.getMinBuildHeight()
                    || point.region().maxY() >= level.getMaxBuildHeight())) {
                errors.add("Capture point " + point.id() + " height is outside dimension build limits");
            }
        }
        return errors;
    }

    @Override
    public void start(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        stop(); states.clear(); scoreTicks = 0; syncHoldTicks = 0; syncIndex = 0;
        map.domination().points().forEach(point -> states.put(point.id(), new CapturePointState()));
        refreshBossBars(server, manager, map);
    }

    @Override
    public ModeTickResult tick(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        List<CapturePointDefinition> active = activePoints(map);
        for (CapturePointDefinition point : active) tickPoint(server, manager, point, rules);
        if (++scoreTicks >= rules.scoreIntervalSeconds() * 20) {
            scoreTicks = 0;
            for (CapturePointDefinition point : active) {
                TeamSide owner = states.get(point.id()).owner();
                if (owner != TeamSide.NONE) manager.addTeamScore(owner, rules.scorePerPoint());
            }
        }

        if (map.domination().strategy() == PointActivationStrategy.SYNC && !active.isEmpty()) {
            CapturePointState state = states.get(active.get(0).id());
            if (state.owner() != TeamSide.NONE) syncHoldTicks++;
            if (syncHoldTicks >= rules.syncHoldSeconds() * 20) {
                if (syncIndex + 1 >= map.domination().points().size()) {
                    return ModeTickResult.finish(manager.determineWinner());
                }
                syncIndex++; syncHoldTicks = 0; states.values().forEach(CapturePointState::reset);
                announce(server, manager, Component.translatable("sfgame.point.next", activePoints(map).get(0).id()));
                refreshBossBars(server, manager, map);
            }
        }
        refreshBossBars(server, manager, map);
        return map.enabledTeams().stream().anyMatch(side -> manager.score(side) >= rules.scoreLimit())
                ? ModeTickResult.finish(manager.determineWinner()) : ModeTickResult.CONTINUE;
    }

    @Override
    public void stop() {
        bossBars.values().forEach(ServerBossEvent::removeAllPlayers);
        bossBars.clear(); states.clear(); scoreTicks = 0; syncHoldTicks = 0; syncIndex = 0;
    }

    private void tickPoint(MinecraftServer server, MatchManager manager, CapturePointDefinition point, MatchRules rules) {
        EnumMap<TeamSide, Integer> counts = new EnumMap<>(TeamSide.class);
        TeamSide.PLAYABLE.forEach(side -> counts.put(side, 0));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState playerState = manager.state(player);
            if (!playerState.participating() || playerState.respawning() || player.isSpectator() || !point.region().contains(player)) continue;
            TeamSide side = manager.teams().sideOf(player, manager.savedData());
            if (side != TeamSide.NONE) counts.put(side, counts.get(side) + 1);
        }
        List<Map.Entry<TeamSide, Integer>> ranked = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0).sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).toList();
        CapturePointState state = states.get(point.id());
        if (ranked.isEmpty()) {
            state.advance(TeamSide.NONE, 1.0 / (rules.captureTimeSeconds() * 20.0), true);
            return;
        }
        int first = ranked.get(0).getValue();
        int second = ranked.size() > 1 ? ranked.get(1).getValue() : 0;
        if (first == second) { state.contested(true); return; }
        double multiplier = calculateCaptureMultiplier(rules, first, second);
        CapturePointState.Change change = state.advance(ranked.get(0).getKey(),
                multiplier / (rules.captureTimeSeconds() * 20.0), false);
        if (change == CapturePointState.Change.CAPTURED) {
            announce(server, manager, Component.translatable("sfgame.point.captured", point.id(),
                    Component.translatable("sfgame.team." + state.owner().id())));
        } else if (change == CapturePointState.Change.NEUTRALIZED) {
            announce(server, manager, Component.translatable("sfgame.point.neutralized", point.id()));
        }
    }

    static double calculateCaptureMultiplier(MatchRules rules, int first, int second) {
        double multiplier = rules.captureUsePlayerDifference()
                ? Math.max(0, first - second) * rules.captureDifferenceCoefficient() : 1.0;
        return Math.min(rules.captureMaxMultiplier(), multiplier);
    }

    private List<CapturePointDefinition> activePoints(ArenaMap map) {
        List<CapturePointDefinition> points = map.domination().points();
        if (map.domination().strategy() == PointActivationStrategy.ASYNC) return points;
        return points.isEmpty() ? List.of() : List.of(points.get(Math.min(syncIndex, points.size() - 1)));
    }

    private void refreshBossBars(MinecraftServer server, MatchManager manager, ArenaMap map) {
        List<CapturePointDefinition> active = activePoints(map);
        List<String> activeIds = active.stream().map(CapturePointDefinition::id).toList();
        new ArrayList<>(bossBars.keySet()).stream().filter(id -> !activeIds.contains(id)).forEach(id -> {
            bossBars.remove(id).removeAllPlayers();
        });
        for (CapturePointDefinition point : active) {
            CapturePointState state = states.get(point.id());
            ServerBossEvent bar = bossBars.computeIfAbsent(point.id(), ignored -> new ServerBossEvent(
                    Component.literal(point.id().toUpperCase()), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS));
            TeamSide colorSide = state.owner() != TeamSide.NONE ? state.owner() : state.contender();
            bar.setColor(color(colorSide));
            bar.setProgress((float) Math.max(0.0, Math.min(1.0, state.progress())));
            Component status = state.contested() ? Component.translatable("sfgame.point.contested")
                    : state.owner() != TeamSide.NONE ? Component.translatable("sfgame.team." + state.owner().id())
                    : state.contender() != TeamSide.NONE ? Component.translatable("sfgame.team." + state.contender().id())
                    : Component.translatable("sfgame.point.neutral");
            bar.setName(Component.translatable("sfgame.point.bossbar", point.id().toUpperCase(), status));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerMatchState playerState = manager.state(player);
                if (playerState.participating() || playerState.queued()) bar.addPlayer(player); else bar.removePlayer(player);
            }
        }
    }

    private static BossEvent.BossBarColor color(TeamSide side) {
        return switch (side) {
            case RED -> BossEvent.BossBarColor.RED;
            case BLUE -> BossEvent.BossBarColor.BLUE;
            case YELLOW -> BossEvent.BossBarColor.YELLOW;
            case GREEN -> BossEvent.BossBarColor.GREEN;
            case NONE -> BossEvent.BossBarColor.WHITE;
        };
    }

    private static void announce(MinecraftServer server, MatchManager manager, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState state = manager.state(player);
            if (!state.participating() && !state.queued()) continue;
            player.sendSystemMessage(message, true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.MASTER, 0.8F, 1.2F);
        }
    }
}

package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.entity.DeployableBeaconEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Server-authoritative listing and immediate revalidation of respawn sources. */
public final class RespawnSourceResolver {
    private final MatchManager manager;

    public RespawnSourceResolver(MatchManager manager) {
        this.manager = manager;
    }
    @Nullable
    public RespawnTarget baseTarget(TeamSide side) {
        return fromArena(manager.runtimeSpawn(side));
    }

    public List<MatchSnapshot.RespawnOption> options(ServerPlayer player) {
        if (!eligibleForSelection(player)) return List.of();
        String mode = manager.savedData().selectedMode();
        if (!GameModeRegistry.DOMINATION.equals(mode) && !GameModeRegistry.BREAKTHROUGH.equals(mode)
                && !GameModeRegistry.CAPTURE_THE_FLAG.equals(mode)) return List.of();
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        List<MatchSnapshot.RespawnOption> result = new ArrayList<>();
        addArenaOption(result, "base", "base", "", manager.runtimeSpawn(side));

        manager.squads().members(side).stream()
                .map(manager::serverPlayer)
                .filter(target -> isLiveTarget(target, side))
                .sorted(Comparator.comparing((ServerPlayer target) -> target.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(target -> target.getUUID().toString()))
                .forEach(target -> result.add(new MatchSnapshot.RespawnOption(
                        "squad:" + target.getUUID(), "squad", target.getGameProfile().getName())));

        UUID captain = captainFor(mode, side);
        ServerPlayer captainPlayer = captain == null ? null : manager.serverPlayer(captain);
        if (isLiveTarget(captainPlayer, side)) {
            result.add(new MatchSnapshot.RespawnOption("captain", "captain", captainPlayer.getGameProfile().getName()));
        }

        DeployableBeaconEntity beacon = manager.beacons().target(side);
        if (beacon != null && beacon.isAlive()) {
            result.add(new MatchSnapshot.RespawnOption("beacon", "beacon", side.id()));
        }

        if (GameModeRegistry.BREAKTHROUGH.equals(mode) && manager.savedData().activeMap() != null) {
            manager.breakthrough().respawnOptions(player, manager, manager.savedData().activeMap()).stream()
                    .filter(option -> "point".equals(option.type()))
                    .forEach(option -> {
                        ArenaPosition point = manager.breakthrough().respawnPosition(player, option.id(), manager,
                                manager.savedData().activeMap());
                        if (point != null) result.add(option);
                    });
        }
        return List.copyOf(result);
    }

    @Nullable
    public RespawnTarget resolve(ServerPlayer player, String optionId) {
        if (!eligibleForSelection(player) || optionId == null) return null;
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        if ("base".equals(optionId)) return fromArena(manager.runtimeSpawn(side));
        if ("captain".equals(optionId)) {
            UUID captain = captainFor(manager.savedData().selectedMode(), side);
            ServerPlayer target = captain == null ? null : manager.serverPlayer(captain);
            return isLiveTarget(target, side) ? fromPlayer(target) : null;
        }
        if ("beacon".equals(optionId)) {
            DeployableBeaconEntity beacon = manager.beacons().target(side);
            return beacon == null || !beacon.isAlive() ? null
                    : new RespawnTarget((ServerLevel) beacon.level(), beacon.position().add(0.0, 0.35, 0.0),
                    beacon.getYRot(), beacon.getXRot());
        }
        if (optionId.startsWith("squad:")) {
            try {
                UUID id = UUID.fromString(optionId.substring("squad:".length()));
                if (!manager.squads().members(side).contains(id)) return null;
                ServerPlayer target = manager.serverPlayer(id);
                return isLiveTarget(target, side) ? fromPlayer(target) : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (optionId.startsWith("point:") && GameModeRegistry.BREAKTHROUGH.equals(manager.savedData().selectedMode())
                && manager.savedData().activeMap() != null) {
            ArenaPosition point = manager.breakthrough().respawnPosition(player, optionId, manager,
                    manager.savedData().activeMap());
            return fromArena(point);
        }
        return null;
    }

    private UUID captainFor(String mode, TeamSide side) {
        if (GameModeRegistry.BREAKTHROUGH.equals(mode)) {
            return side == manager.breakthrough().attacker() ? manager.breakthrough().captain() : null;
        }
        return manager.teamCaptains().supports(mode) ? manager.teamCaptains().captain(side) : null;
    }

    private boolean eligibleForSelection(ServerPlayer player) {
        return manager.phase() == MatchPhase.RUNNING && manager.server() != null
                && manager.state(player).participating() && manager.state(player).respawning()
                && manager.state(player).awaitingRespawnSelection();
    }

    private boolean isLiveTarget(@Nullable ServerPlayer player, TeamSide side) {
        return player != null && manager.state(player).participating() && !manager.state(player).respawning()
                && !player.isSpectator() && !player.isDeadOrDying()
                && manager.teams().sideOf(player, manager.savedData()) == side;
    }

    private void addArenaOption(List<MatchSnapshot.RespawnOption> result, String id, String type, String targetName,
                                @Nullable ArenaPosition position) {
        if (position != null && fromArena(position) != null) result.add(new MatchSnapshot.RespawnOption(id, type, targetName));
    }

    @Nullable
    private RespawnTarget fromArena(@Nullable ArenaPosition position) {
        if (position == null || manager.server() == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(position.dimension());
        if (id == null) return null;
        ServerLevel level = manager.server().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return level == null ? null : new RespawnTarget(level,
                new net.minecraft.world.phys.Vec3(position.x(), position.y(), position.z()), position.yaw(), position.pitch());
    }

    private static RespawnTarget fromPlayer(ServerPlayer player) {
        return new RespawnTarget(player.serverLevel(), player.position(), player.getYRot(), player.getXRot());
    }

    public record RespawnTarget(ServerLevel level, net.minecraft.world.phys.Vec3 position, float yaw, float pitch) { }
}

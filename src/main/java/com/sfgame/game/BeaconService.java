package com.sfgame.game;
import com.sfgame.data.MatchRules;

import com.sfgame.entity.DeployableBeaconEntity;
import com.sfgame.network.SFGameNetwork;
import com.sfgame.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Owns the single live respawn beacon permitted for each faction. */
public final class BeaconService {
    private static final AABB WORLD_SCAN = new AABB(-30_000_000, -2048, -30_000_000,
            30_000_000, 4096, 30_000_000);
    private final MatchManager manager;
    private final EnumMap<TeamSide, BeaconRecord> records = new EnumMap<>(TeamSide.class);

    public BeaconService(MatchManager manager) {
        this.manager = manager;
    }

    public void beginRunning(MinecraftServer server) {
        clear(server);
        records.clear();
    }

    public boolean hasBeacon(TeamSide side) {
        return target(side) != null;
    }

    public int health(TeamSide side) {
        DeployableBeaconEntity beacon = target(side);
        return beacon == null ? 0 : Math.round(beacon.getHealth());
    }

    public int maxHealth(TeamSide side) {
        DeployableBeaconEntity beacon = target(side);
        return beacon == null ? 0 : Math.round(beacon.getMaxHealth());
    }

    @Nullable
    public DeployableBeaconEntity target(TeamSide side) {
        BeaconRecord record = records.get(side);
        if (record == null) return null;
        if (!(record.level.getEntity(record.entityId) instanceof DeployableBeaconEntity beacon)
                || !beacon.isAlive() || beacon.team() != side || !beacon.getTags().contains(DeployableBeaconEntity.BEACON_TAG)) {
            records.remove(side);
            return null;
        }
        return beacon;
    }

    public boolean deploy(ServerPlayer player, Vec3 position) {
        if (manager.phase() != MatchPhase.RUNNING || manager.server() == null
                || !manager.state(player).participating() || manager.state(player).respawning()
                || player.isSpectator() || player.isDeadOrDying()) return false;
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        if (side == TeamSide.NONE || !manager.savedData().enabledTeams().contains(side)) return false;
        if (hasBeacon(side)) return false;
        ServerLevel level = player.serverLevel();
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(position))) return false;
        DeployableBeaconEntity beacon = ModEntities.RESPAWN_BEACON.get().create(level);
        if (beacon == null) return false;
        beacon.setPos(position.x, position.y, position.z);
        beacon.team(side);
        beacon.ownerUuid(player.getUUID());
        beacon.initializeHealth(manager.rules().respawnBeaconHealth());
        beacon.addTag(DeployableBeaconEntity.BEACON_TAG);
        if (!level.addFreshEntity(beacon)) {
            beacon.discard();
            return false;
        }
        records.put(side, new BeaconRecord(beacon.getUUID(), level));
        manager.syncAll();
        return true;
    }

    public void tick(MinecraftServer server) {
        if (manager.phase() != MatchPhase.RUNNING) return;
        reconcile(server, false);
    }

    public void onRuleChanged(MatchRules rules) {
        for (TeamSide side : TeamSide.PLAYABLE) {
            DeployableBeaconEntity beacon = target(side);
            if (beacon == null) continue;
            beacon.setMaxHealth(rules.respawnBeaconHealth());
        }
    }

    public void onDestroyed(DeployableBeaconEntity beacon) {
        TeamSide side = beacon.team();
        BeaconRecord record = records.get(side);
        if (record != null && record.entityId.equals(beacon.getUUID())) records.remove(side);
        if (manager.server() != null && side != TeamSide.NONE) {
            for (ServerPlayer player : manager.server().getPlayerList().getPlayers()) {
                if (manager.state(player).participating() && manager.teams().sideOf(player, manager.savedData()) == side) {
                    player.sendSystemMessage(Component.translatable("sfgame.respawn.beacon_destroyed"), true);
                }
            }
        }
        manager.syncAll();
    }

    public void clear(MinecraftServer server) {
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (DeployableBeaconEntity beacon : level.getEntitiesOfClass(DeployableBeaconEntity.class, WORLD_SCAN,
                        entity -> entity.getTags().contains(DeployableBeaconEntity.BEACON_TAG))) {
                    beacon.discard();
                }
            }
        }
        records.clear();
    }

    /** Rebuilds UUID/level references after a server reload and removes stale duplicates. */
    public void reconcile(MinecraftServer server, boolean discardOrphans) {
        if (server == null) return;
        for (TeamSide side : TeamSide.PLAYABLE) target(side);
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (DeployableBeaconEntity beacon : level.getEntitiesOfClass(DeployableBeaconEntity.class, WORLD_SCAN,
                    entity -> entity.getTags().contains(DeployableBeaconEntity.BEACON_TAG))) {
                TeamSide side = beacon.team();
                boolean valid = side != TeamSide.NONE && manager.savedData().enabledTeams().contains(side);
                BeaconRecord existing = records.get(side);
                if (!valid || discardOrphans || existing != null && !existing.entityId.equals(beacon.getUUID())
                        || !seen.add(beacon.getUUID())) {
                    beacon.discard();
                    continue;
                }
                records.put(side, new BeaconRecord(beacon.getUUID(), level));
            }
        }
        records.entrySet().removeIf(entry -> target(entry.getKey()) == null);
    }

    private record BeaconRecord(UUID entityId, ServerLevel level) { }
}

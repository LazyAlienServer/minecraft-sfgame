package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.BreakthroughMapConfig;
import com.sfgame.data.BreakthroughSectorDefinition;
import com.sfgame.data.BreakthroughVehicleDefinition;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.MatchRules;
import com.sfgame.network.SFGameNetwork;
import com.sfgame.network.MatchSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakthroughRuntime implements MatchModeRuntime {
    private static final String CAPTAIN_FLAG_TAG = "SFGameCaptainFlag";
    private static final String CAPTAIN_GLOW_TAG = "SFGameCaptainGlowing";
    private enum RuntimeState { ACTIVE, SECTOR_TRANSITION, LEG_TRANSITION }

    private final Map<String, CapturePointState> pointStates = new HashMap<>();
    private final Map<String, ServerBossEvent> bossBars = new HashMap<>();
    private final Map<String, Entity> spawnedVehicles = new HashMap<>();
    private final Map<String, Integer> vehicleCooldowns = new HashMap<>();
    private final CapturePointMarkerService pointMarkers = new CapturePointMarkerService();
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Set<UUID> abstentions = new HashSet<>();
    private RuntimeState runtimeState = RuntimeState.ACTIVE;
    private TeamSide attacker = TeamSide.NONE;
    private TeamSide defender = TeamSide.NONE;
    private UUID captain;
    private UUID appearanceCaptain;
    private MinecraftServer runtimeServer;
    private int leg = 1;
    private int sectorIndex;
    private int sectorElapsedTicks;
    private int totalAttackTicks;
    private int transitionTicks;
    private int electionTicks;
    private int tickets;
    private LegResult firstLeg;

    @Override
    public List<String> validate(MinecraftServer server, ArenaMap map) {
        List<String> errors = new ArrayList<>(map.breakthrough().validate());
        for (BreakthroughSectorDefinition sector : map.breakthrough().sectors()) {
            for (CapturePointDefinition point : sector.points()) {
                ResourceLocation id = ResourceLocation.tryParse(point.region().dimension());
                ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (level == null) {
                    errors.add("Sector " + sector.id() + " point " + point.id() + " uses unavailable dimension " + point.region().dimension());
                } else if (point.region().minY() != null && (point.region().minY() < level.getMinBuildHeight()
                        || point.region().maxY() >= level.getMaxBuildHeight())) {
                    errors.add("Sector " + sector.id() + " point " + point.id() + " height is outside dimension build limits");
                }
            }
        }
        for (BreakthroughVehicleDefinition vehicle : map.breakthrough().vehicles()) {
            ResourceLocation entityId = ResourceLocation.tryParse(vehicle.entityId());
            if (entityId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                errors.add("Vehicle " + vehicle.id() + " uses unavailable entity type " + vehicle.entityId());
            }
            for (BreakthroughVehicleDefinition.AmmoEntry ammo : vehicle.ammo()) {
                ResourceLocation ammoId = ResourceLocation.tryParse(ammo.itemId());
                if (ammoId == null || !BuiltInRegistries.ITEM.containsKey(ammoId)) {
                    errors.add("Vehicle " + vehicle.id() + " uses unavailable ammo item " + ammo.itemId());
                }
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(vehicle.spawn().dimension());
            ServerLevel level = dimensionId == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) {
                errors.add("Vehicle " + vehicle.id() + " uses unavailable dimension " + vehicle.spawn().dimension());
            }
        }
        return errors;
    }

    @Override public boolean needsPreparation(ArenaMap map) {
        return map.breakthrough().variant() == BreakthroughVariant.CAPTAIN;
    }

    @Override
    public void prepare(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        runtimeServer = server;
        clearRuntimeEntities();
        configureRoles(map.breakthrough(), 1);
        captain = null;
        beginElection(rules.captainVoteSeconds());
        announce(server, manager, Component.translatable("sfgame.breakthrough.captain.vote", rules.captainVoteSeconds()));
        manager.forParticipants(player -> {
            if (manager.teams().sideOf(player, manager.savedData()) == attacker) SFGameNetwork.openMenu(player);
        });
    }

    @Override
    public boolean tickPreparation(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        if (electionTicks > 0) electionTicks--;
        if (electionTicks > 0) return false;
        resolveElection(server, manager);
        return captain != null;
    }

    @Override
    public void start(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        runtimeServer = server;
        clearRuntimeEntities();
        configureRoles(map.breakthrough(), 1);
        leg = 1; sectorIndex = 0; sectorElapsedTicks = 0; totalAttackTicks = 0;
        transitionTicks = 0; tickets = rules.attackerTickets(); firstLeg = null;
        runtimeState = RuntimeState.ACTIVE;
        resetCurrentSector(map);
        refreshDisplays(server, manager, map);
        updateCaptainAppearance(server, manager, map, rules);
        announceObjective(server, manager, map);
    }

    @Override
    public ModeTickResult tick(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        runtimeServer = server;
        maintainCaptain(server, manager, map, rules);
        updateCaptainAppearance(server, manager, map, rules);
        if (runtimeState != RuntimeState.ACTIVE) return tickTransition(server, manager, map, rules);

        sectorElapsedTicks++;
        totalAttackTicks++;
        for (CapturePointDefinition point : currentSector(map).points()) tickPoint(server, manager, map, point, rules);
        tickVehicles(server, manager, map);
        refreshDisplays(server, manager, map);

        if (allPointsCaptured(map)) return completeSector(server, manager, map, rules);
        if (tickets <= 0 || sectorElapsedTicks >= rules.timeLimitSeconds() * 20) {
            return finishLeg(server, manager, map, rules, false);
        }
        return ModeTickResult.CONTINUE;
    }

    @Override
    public void onPlayerDeath(TeamSide victim, MatchManager manager) {
        if (runtimeState == RuntimeState.ACTIVE && victim == attacker) tickets = Math.max(0, tickets - 1);
    }

    @Override
    public void onRuleChanged(String key, MatchRules rules) {
        if ("attackerTickets".equals(key)) tickets = rules.attackerTickets();
    }

    @Override
    public ArenaPosition spawnFor(TeamSide side, ArenaMap map) {
        if (side == TeamSide.NONE || map.breakthrough().sectors().isEmpty()) return null;
        BreakthroughSectorDefinition sector = currentSector(map);
        return sector.randomSpawn(side == attacker);
    }

    @Override public int remainingSeconds(MatchManager manager, MatchRules rules) {
        return Math.max(0, rules.timeLimitSeconds() - sectorElapsedTicks / 20);
    }
    @Override public boolean isCaptain(UUID playerId) { return captain != null && captain.equals(playerId); }
    @Override public boolean usesCommonTimeLimit() { return false; }
    @Override public boolean blocksCombat() { return runtimeState != RuntimeState.ACTIVE; }
    @Override
    public boolean canBreakBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                  net.minecraft.world.level.block.state.BlockState state, MatchManager manager) {
        // The breakthrough block-editing rule enables both breaking and
        // placing while the active sector is running.  Captain elections and
        // sector transitions stay protected so the map cannot be altered
        // during deployments.
        return runtimeState == RuntimeState.ACTIVE && manager.rules().breakthroughBlockBreaking();
    }

    @Override
    public boolean canPlaceBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                  net.minecraft.world.level.block.state.BlockState state, MatchManager manager) {
        return runtimeState == RuntimeState.ACTIVE && manager.rules().breakthroughBlockBreaking();
    }

    public TeamSide attacker() { return attacker; }
    public TeamSide defender() { return defender; }
    public UUID captain() { return captain; }
    public int tickets() { return tickets; }
    public int leg() { return leg; }
    public int sectorNumber() { return sectorIndex + 1; }
    public int sectorCount(ArenaMap map) { return map.breakthrough().sectors().size(); }
    public int electionSeconds() { return Math.max(0, (electionTicks + 19) / 20); }
    public String subState() { return runtimeState.name().toLowerCase(Locale.ROOT); }

    public List<MatchSnapshot.RespawnOption> respawnOptions(ServerPlayer player, MatchManager manager, ArenaMap map) {
        if (runtimeState != RuntimeState.ACTIVE || !manager.state(player).awaitingRespawnSelection()) return List.of();
        List<MatchSnapshot.RespawnOption> options = new ArrayList<>();
        options.add(new MatchSnapshot.RespawnOption("base", ""));
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        for (CapturePointDefinition point : currentSector(map).points()) {
            CapturePointState state = pointStates.get(point.id());
            if (point.respawnPosition() != null && point.nearbyRespawnPosition() != null
                    && state != null && state.owner() == side) {
                options.add(new MatchSnapshot.RespawnOption("point:" + point.id(), point.id()));
            }
        }
        return List.copyOf(options);
    }

    @Nullable
    public ArenaPosition respawnPosition(ServerPlayer player, String optionId, MatchManager manager, ArenaMap map) {
        if (runtimeState != RuntimeState.ACTIVE || !manager.state(player).awaitingRespawnSelection()) return null;
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        if ("base".equals(optionId)) return spawnFor(side, map);
        if (optionId == null || !optionId.startsWith("point:")) return null;
        String pointId = optionId.substring("point:".length());
        CapturePointDefinition point = currentSector(map).point(pointId).orElse(null);
        CapturePointState state = pointStates.get(pointId);
        if (point == null || point.respawnPosition() == null || point.nearbyRespawnPosition() == null
                || state == null || state.owner() != side) return null;
        return pointRespawnPosition(point, state);
    }

    static ArenaPosition pointRespawnPosition(CapturePointDefinition point, CapturePointState state) {
        boolean underAttack = state.contested() || state.contender() != TeamSide.NONE && state.progress() < 1.0;
        return underAttack ? point.nearbyRespawnPosition() : point.respawnPosition();
    }

    public boolean vote(ServerPlayer voter, @Nullable ServerPlayer candidate, boolean abstain, MatchManager manager) {
        if (electionTicks <= 0 || manager.teams().sideOf(voter, manager.savedData()) != attacker || !manager.state(voter).participating()) return false;
        votes.remove(voter.getUUID()); abstentions.remove(voter.getUUID());
        if (abstain) { abstentions.add(voter.getUUID()); return true; }
        if (candidate == null || manager.teams().sideOf(candidate, manager.savedData()) != attacker || !manager.state(candidate).participating()) return false;
        votes.put(voter.getUUID(), candidate.getUUID());
        return true;
    }

    public boolean setCaptain(ServerPlayer player, MatchManager manager) {
        if (manager.teams().sideOf(player, manager.savedData()) != attacker || !manager.state(player).participating()) return false;
        UUID previous = captain;
        captain = player.getUUID(); electionTicks = 0; votes.clear(); abstentions.clear();
        if (previous != null && !previous.equals(captain)) {
            ServerPlayer oldCaptain = manager.serverPlayer(previous);
            if (oldCaptain != null) manager.redeploy(oldCaptain);
        }
        manager.ensureCaptainClass(player);
        manager.redeploy(player);
        return true;
    }

    public boolean reelect(TeamSide side, MatchRules rules, MatchManager manager) {
        if (side != attacker) return false;
        UUID previous = captain; captain = null; beginElection(rules.captainReplacementVoteSeconds()); clearCaptainAppearance();
        if (previous != null) {
            ServerPlayer oldCaptain = manager.serverPlayer(previous);
            if (oldCaptain != null) manager.redeploy(oldCaptain);
        }
        return true;
    }

    @Override
    public void stop() {
        clearRuntimeEntities();
        pointStates.clear(); votes.clear(); abstentions.clear(); captain = null;
        attacker = TeamSide.NONE; defender = TeamSide.NONE; firstLeg = null;
        sectorIndex = 0; sectorElapsedTicks = 0; totalAttackTicks = 0; tickets = 0; electionTicks = 0;
        runtimeServer = null;
    }

    private void tickPoint(MinecraftServer server, MatchManager manager, ArenaMap map,
                           CapturePointDefinition point, MatchRules rules) {
        double attackWeight = 0.0;
        double defenseWeight = 0.0;
        boolean captainMode = map.breakthrough().variant() == BreakthroughVariant.CAPTAIN;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState state = manager.state(player);
            if (!state.participating() || state.respawning() || player.isSpectator() || !point.region().contains(player)) continue;
            TeamSide side = manager.teams().sideOf(player, manager.savedData());
            if (side == attacker) attackWeight += captainMode && isCaptain(player.getUUID())
                    ? rules.attackerCaptainCaptureWeight() : 1.0;
            else if (side == defender) defenseWeight += captainMode ? rules.defenderCaptureWeight() : 1.0;
        }
        CapturePointState state = pointStates.get(point.id());
        if (attackWeight == 0.0 && defenseWeight == 0.0) {
            state.advance(TeamSide.NONE, 1.0 / (rules.captureTimeSeconds() * 20.0), true);
            return;
        }
        if (Math.abs(attackWeight - defenseWeight) < 0.0001) { state.contested(true); return; }
        TeamSide leader = attackWeight > defenseWeight ? attacker : defender;
        double difference = Math.abs(attackWeight - defenseWeight);
        double multiplier = rules.captureUsePlayerDifference()
                ? difference * rules.captureDifferenceCoefficient() : 1.0;
        multiplier = Math.min(rules.captureMaxMultiplier(), multiplier);
        CapturePointState.Change change = state.advance(leader, multiplier / (rules.captureTimeSeconds() * 20.0), false);
        if (change == CapturePointState.Change.CAPTURED) {
            announce(server, manager, Component.translatable("sfgame.point.captured", displayId(point.id()), teamName(state.owner())));
        } else if (change == CapturePointState.Change.NEUTRALIZED) {
            announce(server, manager, Component.translatable("sfgame.point.neutralized", displayId(point.id())));
        }
    }

    private ModeTickResult completeSector(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        if (sectorIndex + 1 >= map.breakthrough().sectors().size()) return finishLeg(server, manager, map, rules, true);
        String completed = currentSector(map).id();
        sectorIndex++;
        sectorElapsedTicks = 0;
        tickets = rules.attackerTickets();
        runtimeState = RuntimeState.SECTOR_TRANSITION;
        transitionTicks = rules.sectorTransitionSeconds() * 20;
        clearDisplays();
        manager.modeRedeployAll(rules.sectorTransitionSeconds() * 20 + 20);
        announce(server, manager, Component.translatable("sfgame.breakthrough.sector.transition", completed, currentSector(map).id()));
        if (transitionTicks == 0) beginNextSector(server, manager, map);
        return ModeTickResult.CONTINUE;
    }

    private ModeTickResult tickTransition(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        boolean changingLeg = runtimeState == RuntimeState.LEG_TRANSITION;
        if (changingLeg && electionTicks > 0) {
            electionTicks--;
            if (electionTicks == 0) resolveElection(server, manager);
        }
        if (transitionTicks > 0) transitionTicks--;
        if (transitionTicks > 0 || electionTicks > 0) return ModeTickResult.CONTINUE;
        if (changingLeg) {
            sectorIndex = 0;
            totalAttackTicks = 0;
            tickets = rules.attackerTickets();
        }
        sectorElapsedTicks = 0;
        runtimeState = RuntimeState.ACTIVE;
        resetCurrentSector(map);
        if (changingLeg) manager.modeRedeployAll(rules.respawnProtectionSeconds() * 20);
        refreshDisplays(server, manager, map);
        announceObjective(server, manager, map);
        return ModeTickResult.CONTINUE;
    }

    private void beginNextSector(MinecraftServer server, MatchManager manager, ArenaMap map) {
        runtimeState = RuntimeState.ACTIVE;
        resetCurrentSector(map);
        refreshDisplays(server, manager, map);
        announceObjective(server, manager, map);
    }

    private ModeTickResult finishLeg(MinecraftServer server, MatchManager manager, ArenaMap map,
                                     MatchRules rules, boolean attackSucceeded) {
        LegResult current = snapshotLeg(map, attackSucceeded);
        if (map.breakthrough().legs() == 1) return ModeTickResult.finish(attackSucceeded ? attacker : defender);
        if (leg == 1) {
            firstLeg = current; leg = 2;
            TeamSide oldAttacker = attacker; attacker = defender; defender = oldAttacker;
            captain = null; clearCaptainAppearance();
            sectorIndex = 0; sectorElapsedTicks = 0; totalAttackTicks = 0; tickets = rules.attackerTickets();
            runtimeState = RuntimeState.LEG_TRANSITION;
            transitionTicks = Math.max(rules.sectorTransitionSeconds(),
                    map.breakthrough().variant() == BreakthroughVariant.CAPTAIN ? rules.captainVoteSeconds() : 0) * 20;
            clearDisplays();
            if (map.breakthrough().variant() == BreakthroughVariant.CAPTAIN) {
                beginElection(rules.captainVoteSeconds());
                announce(server, manager, Component.translatable("sfgame.breakthrough.captain.vote", rules.captainVoteSeconds()));
            }
            manager.modeRedeployAll(transitionTicks + 20);
            announce(server, manager, Component.translatable("sfgame.breakthrough.leg.transition"));
            return ModeTickResult.CONTINUE;
        }
        int comparison = compare(current, firstLeg);
        return ModeTickResult.finish(comparison > 0 ? current.attacker : comparison < 0 ? firstLeg.attacker : TeamSide.NONE);
    }

    private LegResult snapshotLeg(ArenaMap map, boolean success) {
        int completed = success ? map.breakthrough().sectors().size() : sectorIndex;
        int captured = success ? 0 : (int) currentSector(map).points().stream()
                .filter(point -> pointStates.get(point.id()).owner() == attacker).count();
        return new LegResult(attacker, completed, captured, totalAttackTicks, tickets);
    }

    private static int compare(LegResult first, LegResult second) {
        int result = Integer.compare(first.completedSectors, second.completedSectors);
        if (result != 0) return result;
        result = Integer.compare(first.capturedPoints, second.capturedPoints);
        if (result != 0) return result;
        result = Integer.compare(second.elapsedTicks, first.elapsedTicks);
        if (result != 0) return result;
        return Integer.compare(first.tickets, second.tickets);
    }

    private void maintainCaptain(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        if (map.breakthrough().variant() != BreakthroughVariant.CAPTAIN) return;
        if (captain != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(captain);
            if (player == null || !manager.state(player).participating()
                    || manager.teams().sideOf(player, manager.savedData()) != attacker) {
                captain = null;
                if (player != null && manager.state(player).participating()) manager.redeploy(player);
                clearCaptainAppearance(); beginElection(rules.captainReplacementVoteSeconds());
                announce(server, manager, Component.translatable("sfgame.breakthrough.captain.reelect", rules.captainReplacementVoteSeconds()));
            }
        } else if (electionTicks <= 0) beginElection(rules.captainReplacementVoteSeconds());
        if (electionTicks > 0 && runtimeState == RuntimeState.ACTIVE) {
            electionTicks--;
            if (electionTicks == 0) resolveElection(server, manager);
        }
    }

    private void beginElection(int seconds) {
        votes.clear(); abstentions.clear(); electionTicks = Math.max(1, seconds * 20);
    }

    private void resolveElection(MinecraftServer server, MatchManager manager) {
        List<ServerPlayer> eligible = server.getPlayerList().getPlayers().stream()
                .filter(player -> manager.state(player).participating())
                .filter(player -> manager.teams().sideOf(player, manager.savedData()) == attacker).toList();
        if (eligible.isEmpty()) { captain = null; return; }
        if (eligible.size() == 1) captain = eligible.get(0).getUUID();
        else {
            Set<UUID> eligibleIds = eligible.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
            Map<UUID, Integer> counts = new HashMap<>();
            votes.forEach((voter, candidate) -> {
                if (eligibleIds.contains(voter) && eligibleIds.contains(candidate)) counts.merge(candidate, 1, Integer::sum);
            });
            int cast = counts.values().stream().mapToInt(Integer::intValue).sum();
            int abstainCount = abstentions.size() + Math.max(0, eligible.size() - votes.size() - abstentions.size());
            if (abstainCount > cast || counts.isEmpty()) captain = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())).getUUID();
            else {
                int high = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                List<UUID> leaders = counts.entrySet().stream().filter(entry -> entry.getValue() == high)
                        .map(Map.Entry::getKey).sorted(Comparator.comparing(UUID::toString)).toList();
                captain = leaders.get(ThreadLocalRandom.current().nextInt(leaders.size()));
            }
        }
        votes.clear(); abstentions.clear(); electionTicks = 0;
        ServerPlayer selected = server.getPlayerList().getPlayer(captain);
        if (selected != null) {
            manager.ensureCaptainClass(selected);
            manager.redeploy(selected);
            announce(server, manager, Component.translatable("sfgame.breakthrough.captain.selected", selected.getDisplayName()));
        }
    }

    private void updateCaptainAppearance(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        if (map.breakthrough().variant() != BreakthroughVariant.CAPTAIN || captain == null) {
            clearCaptainAppearance();
            return;
        }
        if (appearanceCaptain != null && !appearanceCaptain.equals(captain)) clearCaptainAppearance();
        ServerPlayer player = server.getPlayerList().getPlayer(captain);
        if (player == null || player.isSpectator() || manager.state(player).respawning()) {
            clearCaptainAppearance();
            return;
        }
        appearanceCaptain = captain;
        // Captains are identified by the server-side glowing outline only.
        // Their configured helmet remains untouched; CTF flag carriers use a
        // separate head-slot marker in CaptureTheFlagRuntime.
        updateCaptainGlow(player, rules.attackerCaptainGlowing());
    }

    void clearStaleCaptainAppearance(ServerPlayer player) {
        if (captain != null && captain.equals(player.getUUID())) return;
        clearCaptainAppearance(player);
    }

    private void refreshDisplays(MinecraftServer server, MatchManager manager, ArenaMap map) {
        List<CapturePointDefinition> points = runtimeState == RuntimeState.ACTIVE ? currentSector(map).points() : List.of();
        pointMarkers.refresh(server, points);
        List<String> ids = points.stream().map(CapturePointDefinition::id).toList();
        bossBars.entrySet().removeIf(entry -> {
            if (ids.contains(entry.getKey())) return false;
            entry.getValue().removeAllPlayers(); return true;
        });
        for (CapturePointDefinition point : points) {
            CapturePointState state = pointStates.get(point.id());
            ServerBossEvent bar = bossBars.computeIfAbsent(point.id(), ignored -> new ServerBossEvent(
                    Component.literal(displayId(point.id())), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS));
            TeamSide colorSide = state.owner() != TeamSide.NONE ? state.owner() : state.contender();
            bar.setColor(color(colorSide)); bar.setProgress((float) Math.max(0.0, Math.min(1.0, state.progress())));
            Component status = state.contested() ? Component.translatable("sfgame.point.contested")
                    : state.owner() != TeamSide.NONE ? teamName(state.owner())
                    : state.contender() != TeamSide.NONE ? teamName(state.contender()) : Component.translatable("sfgame.point.neutral");
            bar.setName(Component.translatable("sfgame.point.bossbar", displayId(point.id()), status));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                PlayerMatchState playerState = manager.state(player);
                if (playerState.participating() || playerState.queued()) bar.addPlayer(player); else bar.removePlayer(player);
            }
        }
    }

    private void resetCurrentSector(ArenaMap map) {
        pointStates.clear();
        currentSector(map).points().forEach(point -> {
            CapturePointState state = new CapturePointState(); state.reset(defender); pointStates.put(point.id(), state);
        });
    }

    /**
     * Maintains one entity per configured vehicle slot.  A slot is considered
     * occupied as long as its entity is alive; the respawn timer starts only
     * after that entity has been removed or killed, so repeated ticks never
     * create duplicate vehicles.
     */
    private void tickVehicles(MinecraftServer server, MatchManager manager, ArenaMap map) {
        for (BreakthroughVehicleDefinition definition : map.breakthrough().vehicles()) {
            Entity existing = spawnedVehicles.get(definition.id());
            if (existing != null) {
                if (!existing.isRemoved() && existing.isAlive()) continue;
                spawnedVehicles.remove(definition.id());
                vehicleCooldowns.put(definition.id(), definition.respawnSeconds() * 20);
            }
            int cooldown = vehicleCooldowns.getOrDefault(definition.id(), 0);
            if (cooldown > 0) {
                vehicleCooldowns.put(definition.id(), cooldown - 1);
                continue;
            }
            Entity spawned = spawnVehicle(server, definition);
            if (spawned != null) spawnedVehicles.put(definition.id(), spawned);
        }
    }

    @Nullable
    private Entity spawnVehicle(MinecraftServer server, BreakthroughVehicleDefinition definition) {
        ResourceLocation entityId = ResourceLocation.tryParse(definition.entityId());
        ResourceLocation dimensionId = ResourceLocation.tryParse(definition.spawn().dimension());
        if (entityId == null || dimensionId == null) return null;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) return null;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) return null;
        net.minecraft.world.entity.EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        if (type == null) return null;
        Entity entity = type.create(level);
        if (entity == null) return null;
        ArenaPosition spawn = definition.spawn();
        entity.moveTo(spawn.x(), spawn.y() + definition.spawnYOffset(), spawn.z(), spawn.yaw(), spawn.pitch());
        entity.getPersistentData().putString("SFGameVehicleSlot", definition.id());
        entity.getPersistentData().putString("SFGameVehicleRole", definition.role().id());
        entity.addTag("sfgame_vehicle");
        level.addFreshEntity(entity);
        fillVehicleEnergy(entity, definition);
        fillVehicleAmmo(entity, definition);
        return entity;
    }

    private void fillVehicleEnergy(Entity entity, BreakthroughVehicleDefinition definition) {
        IEnergyStorage storage = entity.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
        if (storage == null || storage.getMaxEnergyStored() <= 0) return;
        int target = (int) Math.round(storage.getMaxEnergyStored() * (definition.energyPercent() / 100.0D));
        int current = storage.getEnergyStored();
        if (current < target) storage.receiveEnergy(target - current, false);
        else if (current > target && storage.canExtract()) storage.extractEnergy(current - target, false);
    }

    private void fillVehicleAmmo(Entity entity, BreakthroughVehicleDefinition definition) {
        if (definition.ammo().isEmpty()) return;
        IItemHandler inventory = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (inventory == null) {
            SFGame.LOGGER.warn("Vehicle slot {} entity {} has no item inventory; configured ammo was not inserted",
                    definition.id(), definition.entityId());
            return;
        }
        for (BreakthroughVehicleDefinition.AmmoEntry ammo : definition.ammo()) {
            ResourceLocation itemId = ResourceLocation.tryParse(ammo.itemId());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) continue;
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemId);
            int remaining = ammo.count();
            int stackLimit = Math.max(1, new ItemStack(item).getMaxStackSize());
            while (remaining > 0) {
                int requested = Math.min(remaining, stackLimit);
                ItemStack leftover = ItemHandlerHelper.insertItemStacked(inventory,
                        new ItemStack(item, requested), false);
                int inserted = requested - leftover.getCount();
                if (inserted <= 0) {
                    SFGame.LOGGER.warn("Vehicle slot {} inventory is full; {} of {} could not be inserted",
                            definition.id(), remaining, ammo.itemId());
                    break;
                }
                remaining -= inserted;
            }
        }
    }

    private void clearVehicles() {
        spawnedVehicles.values().forEach(entity -> {
            if (!entity.isRemoved()) entity.discard();
        });
        spawnedVehicles.clear();
        vehicleCooldowns.clear();
    }
    private boolean allPointsCaptured(ArenaMap map) {
        return currentSector(map).points().stream().allMatch(point -> pointStates.get(point.id()).owner() == attacker);
    }
    private BreakthroughSectorDefinition currentSector(ArenaMap map) {
        List<BreakthroughSectorDefinition> sectors = map.breakthrough().sectors();
        return sectors.get(Math.max(0, Math.min(sectorIndex, sectors.size() - 1)));
    }
    private void configureRoles(BreakthroughMapConfig config, int legNumber) {
        if (legNumber == 2) { attacker = config.defender(); defender = config.attacker(); }
        else { attacker = config.attacker(); defender = config.defender(); }
    }
    private void announceObjective(MinecraftServer server, MatchManager manager, ArenaMap map) {
        announce(server, manager, Component.translatable("sfgame.breakthrough.objective", currentSector(map).id()));
    }
    private static Component teamName(TeamSide side) { return Component.translatable("sfgame.team." + side.id()); }
    private static String displayId(String id) { return id.toUpperCase(Locale.ROOT); }
    private static BossEvent.BossBarColor color(TeamSide side) {
        return switch (side) {
            case RED -> BossEvent.BossBarColor.RED; case BLUE -> BossEvent.BossBarColor.BLUE;
            case YELLOW -> BossEvent.BossBarColor.YELLOW; case GREEN -> BossEvent.BossBarColor.GREEN;
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
    private void clearDisplays() {
        bossBars.values().forEach(ServerBossEvent::removeAllPlayers); bossBars.clear(); pointMarkers.clear();
    }
    private void clearCaptainAppearance() {
        if (appearanceCaptain != null && runtimeServer != null) {
            ServerPlayer player = runtimeServer.getPlayerList().getPlayer(appearanceCaptain);
            if (player != null) clearCaptainAppearance(player);
        }
        appearanceCaptain = null;
    }

    private static void clearCaptainAppearance(ServerPlayer player) {
        if (isCaptainFlag(player.getItemBySlot(EquipmentSlot.HEAD))) {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        if (player.getPersistentData().getBoolean(CAPTAIN_GLOW_TAG)) {
            player.setGlowingTag(false);
            player.getPersistentData().remove(CAPTAIN_GLOW_TAG);
        }
    }

    private static void updateCaptainGlow(ServerPlayer player, boolean enabled) {
        if (enabled) {
            player.setGlowingTag(true);
            player.getPersistentData().putBoolean(CAPTAIN_GLOW_TAG, true);
        } else {
            clearCaptainGlow(player);
        }
    }

    private static void clearCaptainGlow(ServerPlayer player) {
        if (!player.getPersistentData().getBoolean(CAPTAIN_GLOW_TAG)) return;
        player.setGlowingTag(false);
        player.getPersistentData().remove(CAPTAIN_GLOW_TAG);
    }

    private static ItemStack captainFlag(TeamSide side) {
        ItemStack stack = new ItemStack(switch (side) {
            case RED -> Blocks.RED_BANNER; case BLUE -> Blocks.BLUE_BANNER;
            case YELLOW -> Blocks.YELLOW_BANNER; case GREEN -> Blocks.GREEN_BANNER; default -> Blocks.WHITE_BANNER;
        });
        stack.getOrCreateTag().putBoolean(CAPTAIN_FLAG_TAG, true);
        return stack;
    }

    private static boolean isCaptainFlag(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(CAPTAIN_FLAG_TAG);
    }

    private void clearRuntimeEntities() { clearDisplays(); clearCaptainAppearance(); clearVehicles(); }

    private record LegResult(TeamSide attacker, int completedSectors, int capturedPoints, int elapsedTicks, int tickets) { }
}

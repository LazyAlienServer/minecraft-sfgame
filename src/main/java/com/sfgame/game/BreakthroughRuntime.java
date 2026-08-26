package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.BreakthroughSectorDefinition;
import com.sfgame.data.BreakthroughVehicleDefinition;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.MatchRules;
import com.sfgame.network.SFGameNetwork;
import com.sfgame.network.MatchSnapshot;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

public final class BreakthroughRuntime implements MatchModeRuntime {
    private static final String CAPTAIN_FLAG_TAG = "SFGameCaptainFlag";
    private static final String CAPTAIN_GLOW_TAG = "SFGameCaptainGlowing";
    static final int ATTACK_ROUND_REST_SECONDS = 15;
    static final int LEG_ROTATION_NOTICE_SECONDS = 5;
    static final int LEG_PREPARATION_SECONDS = 30;
    private enum RuntimeState { ACTIVE, SECTOR_TRANSITION, ATTACK_ROUND_TRANSITION, LEG_ROTATION_NOTICE, LEG_PREPARATION }

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
    private long timeAdjustmentTicks;
    private boolean unlimitedTimeOverride;
    private boolean timeOverrideActive;
    private int totalAttackTicks;
    private int transitionTicks;
    private int electionTicks;
    private int tickets;
    private int attackRoundsRemaining;
    private LegResult firstLeg;
    private static final Map<Class<?>, Optional<Method>> VEHICLE_WRECK_METHODS = new ConcurrentHashMap<>();

    @Override
    public List<String> validate(MinecraftServer server, ArenaMap map, MatchRules rules) {
        List<String> errors = new ArrayList<>(validateConfiguration(map, rules));
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

    static List<String> validateConfiguration(ArenaMap map, MatchRules rules) {
        List<String> errors = new ArrayList<>(map.breakthrough().validate());
        if (rules.breakthroughAttacker() == rules.breakthroughDefender()) {
            errors.add("Breakthrough attacker and defender must be different teams");
        }
        return errors;
    }

    @Override public boolean needsPreparation(ArenaMap map, MatchRules rules) {
        return rules.breakthroughVariant() == BreakthroughVariant.CAPTAIN;
    }

    @Override
    public void prepare(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        runtimeServer = server;
        clearRuntimeEntities();
        configureRoles(rules);
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
        configureRoles(rules);
        leg = 1; sectorIndex = 0; sectorElapsedTicks = 0; timeAdjustmentTicks = 0L;
        unlimitedTimeOverride = false; timeOverrideActive = false; totalAttackTicks = 0;
        transitionTicks = 0;
        runtimeState = RuntimeState.ACTIVE;
        tickets = rules.attackerTickets();
        attackRoundsRemaining = rules.breakthroughAttackRounds();
        firstLeg = null;
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
        boolean ticketsExhausted = tickets != MatchRules.UNLIMITED_TICKETS && tickets <= 0;
        boolean timeExpired = remainingTicks(rules) <= 0L && attackRoundsRemaining > 0;
        if (ticketsExhausted || timeExpired) {
            return finishAttackRound(server, manager, map, rules);
        }
        return ModeTickResult.CONTINUE;
    }

    @Override
    public void onPlayerDeath(TeamSide victim, MatchManager manager) {
        if (runtimeState == RuntimeState.ACTIVE && victim == attacker
                && tickets != MatchRules.UNLIMITED_TICKETS) {
            tickets = Math.max(0, tickets - 1);
        }
    }

    @Override
    public void onRuleChanged(String key, MatchRules rules) {
        if ("attackerTickets".equals(key)) tickets = rules.attackerTickets();
        if ("breakthroughAttackRounds".equals(key)) {
            attackRoundsRemaining = Math.max(0, Math.min(attackRoundsRemaining, rules.breakthroughAttackRounds()));
        }
    }

    @Override
    public ArenaPosition spawnFor(TeamSide side, ArenaMap map) {
        if (side == TeamSide.NONE || map.breakthrough().sectors().isEmpty()) return null;
        BreakthroughSectorDefinition sector = currentSector(map);
        return sector.randomSpawn(side == attacker);
    }

    @Override
    public int remainingSeconds(MatchManager manager, MatchRules rules) {
        long ticks = remainingTicks(rules);
        if (ticks == Long.MAX_VALUE) return MatchRules.UNLIMITED_TIME_SECONDS;
        ticks = Math.max(0L, ticks);
        return (int) Math.min(Integer.MAX_VALUE, (ticks + 19L) / 20L);
    }
    @Override
    public void setRemainingSeconds(MatchManager manager, MatchRules rules, int seconds) {
        timeOverrideActive = true;
        if (seconds == MatchRules.UNLIMITED_TIME_SECONDS) {
            unlimitedTimeOverride = true;
            timeAdjustmentTicks = 0L;
            return;
        }
        unlimitedTimeOverride = false;
        long baseTicks = rules.timeLimitSeconds() == MatchRules.UNLIMITED_TIME_SECONDS
                ? -sectorElapsedTicks : rules.timeLimitSeconds() * 20L - sectorElapsedTicks;
        timeAdjustmentTicks = seconds * 20L - baseTicks;
    }
    private long remainingTicks(MatchRules rules) {
        if (unlimitedTimeOverride
                || !timeOverrideActive && rules.timeLimitSeconds() == MatchRules.UNLIMITED_TIME_SECONDS) {
            return Long.MAX_VALUE;
        }
        long limitTicks = rules.timeLimitSeconds() == MatchRules.UNLIMITED_TIME_SECONDS
                ? 0L : rules.timeLimitSeconds() * 20L;
        return limitTicks - sectorElapsedTicks + timeAdjustmentTicks;
    }
    @Override public boolean isCaptain(UUID playerId) { return captain != null && captain.equals(playerId); }
    @Override public boolean usesCommonTimeLimit() { return false; }
    @Override public boolean blocksCombat() { return runtimeState != RuntimeState.ACTIVE; }
    @Override
    public boolean allowsMapEditing() { return runtimeState == RuntimeState.ACTIVE; }

    public TeamSide attacker() { return attacker; }
    public TeamSide defender() { return defender; }
    public UUID captain() { return captain; }
    public int tickets() { return tickets; }
    public int attackRoundsRemaining() { return attackRoundsRemaining; }
    public int leg() { return leg; }
    public int sectorNumber() { return sectorIndex + 1; }
    public int sectorCount(ArenaMap map) { return map.breakthrough().sectors().size(); }
    public int electionSeconds() { return Math.max(0, (electionTicks + 19) / 20); }
    public String subState() { return runtimeState.name().toLowerCase(Locale.ROOT); }
    public int remainingLegs(MatchRules rules) {
        return Math.max(0, rules.breakthroughLegs() - Math.max(0, leg - 1));
    }

    boolean setTicketsValue(int value) {
        if (value < MatchRules.UNLIMITED_TICKETS || value > MatchManager.MAX_LIVE_SCORE) return false;
        tickets = value;
        return true;
    }

    boolean setLegState(MatchRules rules, ArenaMap map, int value) {
        if (value < 1 || value > MatchManager.MAX_LIVE_LEG || map.breakthrough().sectors().isEmpty()) return false;
        firstLeg = null;
        leg = value;
        configureRoles(rules);
        sectorIndex = 0;
        totalAttackTicks = 0;
        resetEditedSectorState(rules, map);
        captain = null;
        votes.clear();
        abstentions.clear();
        electionTicks = 0;
        return true;
    }


    boolean setSectorState(MatchRules rules, ArenaMap map, int value) {
        if (value < 1 || value > map.breakthrough().sectors().size()) return false;
        sectorIndex = value - 1;
        resetEditedSectorState(rules, map);
        return true;
    }

    boolean setLeg(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules, int value) {
        boolean swapRosters = changesRosterParity(leg, value);
        if (!setLegState(rules, map, value)) return false;
        if (swapRosters) manager.swapBreakthroughTeamPlayers(attacker, defender);
        rebuildEditedState(server, manager, map, rules, true);
        return true;
    }


    boolean setSector(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules, int value) {
        if (!setSectorState(rules, map, value)) return false;
        rebuildEditedState(server, manager, map, rules, false);
        return true;
    }

    private void resetEditedSectorState(MatchRules rules, ArenaMap map) {
        sectorElapsedTicks = 0;
        timeAdjustmentTicks = 0L;
        unlimitedTimeOverride = false;
        timeOverrideActive = false;
        transitionTicks = 0;
        tickets = rules.attackerTickets();
        attackRoundsRemaining = rules.breakthroughAttackRounds();
        runtimeState = RuntimeState.ACTIVE;
        resetCurrentSector(map);
    }

    private void rebuildEditedState(MinecraftServer server, MatchManager manager, ArenaMap map,
                                    MatchRules rules, boolean rolesChanged) {
        clearDisplays();
        clearVehicles();
        if (rolesChanged) clearCaptainAppearance();
        refreshDisplays(server, manager, map);
        updateCaptainAppearance(server, manager, map, rules);
        manager.modeRedeployAll(rules.respawnProtectionSeconds() * 20);
        announceObjective(server, manager, map);
    }

    public List<MatchSnapshot.RespawnOption> respawnOptions(ServerPlayer player, MatchManager manager, ArenaMap map) {
        if (runtimeState != RuntimeState.ACTIVE || !manager.state(player).awaitingRespawnSelection()) return List.of();
        List<MatchSnapshot.RespawnOption> options = new ArrayList<>();
        options.add(new MatchSnapshot.RespawnOption("base", "base", ""));
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        for (CapturePointDefinition point : currentSector(map).points()) {
            CapturePointState state = pointStates.get(point.id());
            if (point.respawnPosition() != null && point.nearbyRespawnPosition() != null
                    && state != null && state.owner() == side) {
                options.add(new MatchSnapshot.RespawnOption("point:" + point.id(), "point", point.id()));
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
        sectorIndex = 0; sectorElapsedTicks = 0; timeAdjustmentTicks = 0L;
        unlimitedTimeOverride = false;
        timeOverrideActive = false;
        totalAttackTicks = 0;
        runtimeState = RuntimeState.ACTIVE;
        tickets = 0;
        attackRoundsRemaining = 0;
        electionTicks = 0;
        runtimeServer = null;
    }

    private void tickPoint(MinecraftServer server, MatchManager manager, ArenaMap map,
                           CapturePointDefinition point, MatchRules rules) {
        double attackWeight = 0.0;
        double defenseWeight = 0.0;
        boolean captainMode = rules.breakthroughVariant() == BreakthroughVariant.CAPTAIN;
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
        manager.supplyEvent(com.sfgame.data.SupplyTriggerDefinition.BREAKTHROUGH_SECTOR,
                TeamSide.NONE, 0, completed, "");
        sectorElapsedTicks = 0;
        timeAdjustmentTicks = 0L;
        unlimitedTimeOverride = false;
        timeOverrideActive = false;
        tickets = rules.attackerTickets();
        attackRoundsRemaining = rules.breakthroughAttackRounds();
        runtimeState = RuntimeState.SECTOR_TRANSITION;
        transitionTicks = rules.sectorTransitionSeconds() * 20;
        clearDisplays();
        manager.modeRedeployAll(rules.sectorTransitionSeconds() * 20 + 20);
        announceSectorTransition(manager, completed, currentSector(map).id(), transitionTicks);
        if (transitionTicks == 0) beginNextSector(server, manager, map);
        return ModeTickResult.CONTINUE;
    }

    private ModeTickResult tickTransition(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        if (runtimeState == RuntimeState.LEG_ROTATION_NOTICE) {
            if (transitionTicks > 0) transitionTicks--;
            if (transitionTicks > 0) return ModeTickResult.CONTINUE;
            beginLegPreparation(manager, map, rules);
            return ModeTickResult.CONTINUE;
        }

        boolean changingLeg = runtimeState == RuntimeState.LEG_PREPARATION;
        boolean changingAttackRound = runtimeState == RuntimeState.ATTACK_ROUND_TRANSITION;
        if (changingLeg && electionTicks > 0) {
            electionTicks--;
            if (electionTicks == 0) resolveElection(server, manager);
        }
        if (transitionTicks > 0) transitionTicks--;
        if (changingLeg && transitionTicks > 0 && transitionTicks % 20 == 0) {
            manager.announceActionbar(Component.translatable(
                    "sfgame.breakthrough.leg.preparation.countdown.colored", transitionTicks / 20));
        }
        if (transitionTicks > 0 || electionTicks > 0) return ModeTickResult.CONTINUE;

        if (changingAttackRound) tickets = rules.attackerTickets();
        sectorElapsedTicks = 0;
        timeAdjustmentTicks = 0L;
        unlimitedTimeOverride = false;
        timeOverrideActive = false;
        runtimeState = RuntimeState.ACTIVE;
        if (changingLeg) manager.announceActionbar(Component.empty());
        resetCurrentSector(map);
        if (changingAttackRound) {
            manager.modeRedeployAll(rules.respawnProtectionSeconds() * 20);
            announceNextAttackRound(manager);
        }
        refreshDisplays(server, manager, map);
        if (!changingAttackRound) announceObjective(server, manager, map);
        return ModeTickResult.CONTINUE;
    }

    private void beginNextSector(MinecraftServer server, MatchManager manager, ArenaMap map) {
        runtimeState = RuntimeState.ACTIVE;
        resetCurrentSector(map);
        refreshDisplays(server, manager, map);
        announceObjective(server, manager, map);
    }

    private ModeTickResult finishAttackRound(MinecraftServer server, MatchManager manager,
                                              ArenaMap map, MatchRules rules) {
        if (attackRoundsRemaining > 0) {
            attackRoundsRemaining--;
            tickets = rules.attackerTickets();
            sectorElapsedTicks = 0;
            timeAdjustmentTicks = 0L;
            unlimitedTimeOverride = false;
            timeOverrideActive = false;
            runtimeState = RuntimeState.ATTACK_ROUND_TRANSITION;
            transitionTicks = ATTACK_ROUND_REST_SECONDS * 20;
            clearDisplays();
            announceAttackRoundRest(manager);
            return ModeTickResult.CONTINUE;
        }
        return finishLeg(server, manager, map, rules, false);
    }
    private ModeTickResult finishLeg(MinecraftServer server, MatchManager manager, ArenaMap map,
                                     MatchRules rules, boolean attackSucceeded) {
        LegResult current = snapshotLeg(map, attackSucceeded);
        if (rules.breakthroughLegs() == 0) return ModeTickResult.finish(attackSucceeded ? attacker : defender);
        if (leg == 1) {
            firstLeg = new LegResult(defender, current.completedSectors, current.capturedPoints,
                    current.elapsedTicks, current.tickets);
            captain = null;
            clearCaptainAppearance();
            runtimeState = RuntimeState.LEG_ROTATION_NOTICE;
            transitionTicks = LEG_ROTATION_NOTICE_SECONDS * 20;
            electionTicks = 0;
            clearDisplays();
            announceLegRotation(manager, roundWinner(attackSucceeded, attacker, defender));
            return ModeTickResult.CONTINUE;
        }
        if (firstLeg == null) {
            return ModeTickResult.finish(attackSucceeded ? attacker : defender);
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
        if (rules.breakthroughVariant() != BreakthroughVariant.CAPTAIN) return;
        if (captain != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(captain);
            if (player == null || !manager.state(player).participating()
                    || manager.teams().sideOf(player, manager.savedData()) != attacker) {
                captain = null;
                if (player != null && manager.state(player).participating()) manager.redeploy(player);
                clearCaptainAppearance(); beginElection(rules.captainReplacementVoteSeconds());
                announce(server, manager, Component.translatable("sfgame.breakthrough.captain.reelect", rules.captainReplacementVoteSeconds()));
            }
        } else if (electionTicks <= 0 && runtimeState == RuntimeState.ACTIVE) beginElection(rules.captainReplacementVoteSeconds());
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
        if (rules.breakthroughVariant() != BreakthroughVariant.CAPTAIN || captain == null) {
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
        pointMarkers.refresh(server, points, pointStates);
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
            bar.setName(Component.translatable("sfgame.point.label", displayId(point.id())));
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
                if (!isDestroyedVehicle(existing)) continue;
                if (!existing.isRemoved()) {
                    existing.ejectPassengers();
                    existing.discard();
                }
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

    /**
     * Superb Warfare keeps a destroyed vehicle entity alive as a burning wreck.
     * Detect its public Kotlin {@code isWreck()} property without making the
     * optional vehicle mod a compile-time dependency.
     */
    private boolean isDestroyedVehicle(Entity entity) {
        if (entity.isRemoved() || !entity.isAlive()) return true;
        Optional<Method> wreckMethod = VEHICLE_WRECK_METHODS.computeIfAbsent(entity.getClass(), type -> {
            try {
                Method method = type.getMethod("isWreck");
                return method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class
                        ? Optional.of(method) : Optional.empty();
            } catch (NoSuchMethodException exception) {
                return Optional.empty();
            }
        });
        if (wreckMethod.isEmpty()) return false;
        try {
            return Boolean.TRUE.equals(wreckMethod.get().invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            SFGame.LOGGER.warn("Could not read wreck state from vehicle entity {}", entity.getType(), exception);
            VEHICLE_WRECK_METHODS.put(entity.getClass(), Optional.empty());
            return false;
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
        entity.moveTo(spawn.x(), spawn.y() + definition.spawnYOffset(), spawn.z(), spawn.yaw(), 0.0F);
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
    private void configureRoles(MatchRules rules) {
        attacker = rules.breakthroughAttacker();
        defender = rules.breakthroughDefender();
    }
    static boolean changesRosterParity(int currentLeg, int targetLeg) {
        return (currentLeg & 1) != (targetLeg & 1);
    }
    static TeamSide roundWinner(boolean attackSucceeded, TeamSide attacker, TeamSide defender) {
        return attackSucceeded ? attacker : defender;
    }
    private void beginLegPreparation(MatchManager manager, ArenaMap map, MatchRules rules) {
        leg = 2;
        manager.swapBreakthroughTeamPlayers(attacker, defender);
        manager.supplyEvent(com.sfgame.data.SupplyTriggerDefinition.BREAKTHROUGH_STAGE,
                TeamSide.NONE, leg, "", "");
        captain = null;
        clearCaptainAppearance();
        sectorIndex = 0;
        sectorElapsedTicks = 0;
        timeAdjustmentTicks = 0L;
        unlimitedTimeOverride = false;
        timeOverrideActive = false;
        totalAttackTicks = 0;
        tickets = rules.attackerTickets();
        attackRoundsRemaining = rules.breakthroughAttackRounds();
        runtimeState = RuntimeState.LEG_PREPARATION;
        transitionTicks = LEG_PREPARATION_SECONDS * 20;
        electionTicks = 0;
        if (rules.breakthroughVariant() == BreakthroughVariant.CAPTAIN) {
            beginElection(Math.min(rules.captainVoteSeconds(), LEG_PREPARATION_SECONDS));
        }
        resetCurrentSector(map);
        manager.modeRedeployAll(transitionTicks + 20);
        announceLegPreparation(manager);
        manager.announceActionbar(Component.translatable(
                "sfgame.breakthrough.leg.preparation.countdown.colored", LEG_PREPARATION_SECONDS));
    }
    private void announceObjective(MinecraftServer server, MatchManager manager, ArenaMap map) {
        String sector = currentSector(map).id();
        manager.announceTitleAndChat(
                Component.translatable("sfgame.breakthrough.objective.title.colored", sector),
                Component.translatable("sfgame.breakthrough.objective.subtitle.colored"),
                Component.translatable("sfgame.breakthrough.objective.colored", sector),
                80);
    }
    private void announceNextAttackRound(MatchManager manager) {
        int remaining = attackRoundsRemaining + 1;
        manager.announceTitleAndChat(attacker,
                MatchManager.teamColored(attacker,
                        "sfgame.breakthrough.attack_round.next.attacker.title"),
                MatchManager.teamColored(attacker,
                        "sfgame.breakthrough.attack_round.next.attacker.subtitle"),
                MatchManager.teamColored(attacker,
                        "sfgame.breakthrough.attack_round.next.attacker.chat", remaining),
                80);
        manager.announceTitleAndChat(defender,
                MatchManager.teamColored(defender,
                        "sfgame.breakthrough.attack_round.next.defender.title"),
                MatchManager.teamColored(defender,
                        "sfgame.breakthrough.attack_round.next.defender.subtitle"),
                MatchManager.teamColored(defender,
                        "sfgame.breakthrough.attack_round.next.defender.chat", remaining),
                80);
    }
    private void announceSectorTransition(MatchManager manager, String completed, String next, int transitionTicks) {
        manager.announceTitleAndChat(
                Component.translatable("sfgame.breakthrough.sector.transition.title.colored", completed),
                Component.translatable("sfgame.breakthrough.sector.transition.subtitle.colored", next),
                Component.translatable("sfgame.breakthrough.sector.transition.chat.colored", completed, next),
                Math.max(80, transitionTicks));
    }
    private void announceAttackRoundRest(MatchManager manager) {
        int remaining = attackRoundsRemaining + 1;
        manager.announceTitleAndChat(attacker,
                MatchManager.teamColored(attacker,
                        "sfgame.breakthrough.attack_round.rest.attacker.title"),
                MatchManager.teamColored(attacker,
                        "sfgame.breakthrough.attack_round.rest.attacker.subtitle",
                        ATTACK_ROUND_REST_SECONDS, remaining),
                MatchManager.teamColored(attacker,
                        "sfgame.breakthrough.attack_round.rest.attacker.chat",
                        ATTACK_ROUND_REST_SECONDS, remaining),
                ATTACK_ROUND_REST_SECONDS * 20);
        manager.announceTitleAndChat(defender,
                MatchManager.teamColored(defender,
                        "sfgame.breakthrough.attack_round.rest.defender.title"),
                MatchManager.teamColored(defender,
                        "sfgame.breakthrough.attack_round.rest.defender.subtitle",
                        ATTACK_ROUND_REST_SECONDS, remaining),
                MatchManager.teamColored(defender,
                        "sfgame.breakthrough.attack_round.rest.defender.chat",
                        ATTACK_ROUND_REST_SECONDS, remaining),
                ATTACK_ROUND_REST_SECONDS * 20);
    }
    private void announceLegRotation(MatchManager manager, TeamSide winner) {
        Component role = roleName(winner);
        manager.announceTitleAndChat(
                MatchManager.teamColored(winner,
                        "sfgame.breakthrough.leg.rotation.title", role),
                MatchManager.teamColored(winner,
                        "sfgame.breakthrough.leg.rotation.subtitle",
                        LEG_ROTATION_NOTICE_SECONDS),
                MatchManager.teamColored(winner,
                        "sfgame.breakthrough.leg.rotation.chat", role,
                        LEG_ROTATION_NOTICE_SECONDS),
                LEG_ROTATION_NOTICE_SECONDS * 20);
    }
    private void announceLegPreparation(MatchManager manager) {
        manager.announceTitleAndChat(
                Component.translatable("sfgame.breakthrough.leg.preparation.title.colored"),
                Component.translatable("sfgame.breakthrough.leg.preparation.subtitle.colored",
                        LEG_PREPARATION_SECONDS),
                Component.translatable("sfgame.breakthrough.leg.preparation.chat.colored",
                        LEG_PREPARATION_SECONDS),
                5, 60, 20);
    }
    private Component roleName(TeamSide side) {
        String key = side == attacker ? "sfgame.breakthrough.role.attacker"
                : side == defender ? "sfgame.breakthrough.role.defender" : "sfgame.result.draw";
        return Component.translatable(key);
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

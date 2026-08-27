package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.classsystem.ClassRegistry;
import com.sfgame.classsystem.LoadoutService;
import com.sfgame.config.SFGameServerConfigPaths;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.CtfVariant;
import com.sfgame.data.MatchRules;
import com.sfgame.data.MapSnapshotMode;
import com.sfgame.data.SFGameSavedData;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.BossEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import java.nio.file.Path;

public final class MatchManager {
    public static final int MAX_LIVE_TIME_SECONDS = 86_400;
    public static final int MAX_LIVE_SCORE = 1_000_000;
    public static final int MAX_LIVE_LEG = 10;
    private static final MatchManager INSTANCE = new MatchManager();

    private final Map<UUID, PlayerMatchState> players = new HashMap<>();
    private final ClassRegistry classRegistry = new ClassRegistry();
    private final LoadoutService loadoutService = new LoadoutService();
    private final VanillaTeamBindingService teams = new VanillaTeamBindingService();
    private final TeamDeathmatchRuntime teamDeathmatchRuntime = new TeamDeathmatchRuntime();
    private final DominationRuntime dominationRuntime = new DominationRuntime();
    private final BreakthroughRuntime breakthroughRuntime = new BreakthroughRuntime();
    private final CaptureTheFlagRuntime captureTheFlagRuntime = new CaptureTheFlagRuntime();
    private final ShopRegistry shopRegistry = new ShopRegistry();
    private final SupplyService supplyService = new SupplyService();
    private final TeamCaptainService teamCaptainService = new TeamCaptainService();
    private final SquadService squadService = new SquadService(this);
    private final BeaconService beaconService = new BeaconService(this);
    private final RespawnSourceResolver respawnSourceResolver = new RespawnSourceResolver(this);
    private final MapConfigRegistry mapConfigRegistry = new MapConfigRegistry();
    private final RuleConfigRegistry ruleConfigRegistry = new RuleConfigRegistry();
    private final Map<String, MatchModeRuntime> runtimes = Map.of(
            GameModeRegistry.TEAM_DEATHMATCH, teamDeathmatchRuntime,
            GameModeRegistry.DOMINATION, dominationRuntime,
            GameModeRegistry.BREAKTHROUGH, breakthroughRuntime,
            GameModeRegistry.CAPTURE_THE_FLAG, captureTheFlagRuntime);
    private MatchModeRuntime activeRuntime = teamDeathmatchRuntime;

    private MatchPhase phase = MatchPhase.UNCONFIGURED;
    private MinecraftServer server;
    private int phaseTicks;
    private int elapsedTicks;
    private Long commonTimeOverrideEndTick;
    private int redScore;
    private int blueScore;
    private int yellowScore;
    private int greenScore;
    private int syncTicker;
    private TeamSide result = TeamSide.NONE;
    private MapBuildSnapshotService.RestoreSession mapRestoreSession;
    private ServerBossEvent mapRestoreBar;
    private int mapRestoreCooldownTicks;
    private int mapRestoreAdaptiveSkips;
    private double mapRestorePartitionMillisEstimate;
    private boolean modePreparationStarted;
    private boolean anchorPreparationStarted;
    private GameType matchParticipantGameType = GameType.ADVENTURE;

    public static MatchManager get() { return INSTANCE; }
    public MatchPhase phase() { return phase; }
    public ClassRegistry classes() { return classRegistry; }
    public LoadoutService loadouts() { return loadoutService; }
    public VanillaTeamBindingService teams() { return teams; }
    public BreakthroughRuntime breakthrough() { return breakthroughRuntime; }
    public CaptureTheFlagRuntime captureTheFlag() { return captureTheFlagRuntime; }
    public TeamCaptainService teamCaptains() { return teamCaptainService; }
    public SquadService squads() { return squadService; }
    public BeaconService beacons() { return beaconService; }
    public MapConfigRegistry mapConfigs() { return mapConfigRegistry; }
    public ShopRegistry shop() { return shopRegistry; }
    public SupplyService supplies() { return supplyService; }
    public RuleConfigRegistry ruleConfigs() { return ruleConfigRegistry; }
    public MatchRules rules() {
        SFGameSavedData data = data();
        return ruleConfigRegistry.rules(data.selectedMode(), data.selectedMap(), data.rules());
    }
    public Collection<PlayerMatchState> playerStates() { return players.values(); }
    public int redScore() { return redScore; }
    public int blueScore() { return blueScore; }
    public int yellowScore() { return yellowScore; }
    public int greenScore() { return greenScore; }
    public int score(TeamSide side) {
        return switch (side) {
            case RED -> redScore;
            case BLUE -> blueScore;
            case YELLOW -> yellowScore;
            case GREEN -> greenScore;
            case NONE -> 0;
        };
    }
    public int elapsedTicks() { return elapsedTicks; }
    public int remainingSeconds() { return activeRuntime.remainingSeconds(this, rules()); }
    public boolean setRemainingSeconds(int seconds) {
        if (phase != MatchPhase.RUNNING || seconds < MatchRules.UNLIMITED_TIME_SECONDS
                || seconds > MAX_LIVE_TIME_SECONDS) return false;
        activeRuntime.setRemainingSeconds(this, rules(), seconds);
        syncAll();
        return true;
    }
    public boolean setTeamScore(TeamSide side, int value) {
        if (phase != MatchPhase.RUNNING || server == null
                || GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                || value < 0 || value > MAX_LIVE_SCORE || !setTeamScoreValue(side, value)) return false;
        syncAll();
        return true;
    }
    public boolean setCurrency(ServerPlayer player, int value) {
        if (phase != MatchPhase.RUNNING || server == null || !economyEnabled()
                || !setCurrencyValue(state(player), data().selectedMode(), value)) return false;
        sync(player);
        return true;
    }
    boolean setCurrencyValue(PlayerMatchState state, String modeId, int value) {
        if (state == null || !supportsEconomy(modeId) || value < 0 || value > MAX_LIVE_SCORE) return false;
        state.currency(modeId, value);
        return true;
    }
    public static boolean supportsEconomy(String modeId) {
        return GameModeRegistry.BREAKTHROUGH.equals(modeId)
                || GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)
                || GameModeRegistry.DOMINATION.equals(modeId);
    }
    public static boolean supportsSupply(String modeId) {
        return GameModeRegistry.BREAKTHROUGH.equals(modeId)
                || GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)
                || GameModeRegistry.DOMINATION.equals(modeId);
    }

    public boolean economyEnabled() {
        return server != null && supportsEconomy(data().selectedMode()) && rules().economyEnabled();
    }
    static int killCurrencyFor(String modeId, MatchRules rules) {
        return rules != null && supportsEconomy(modeId) && rules.economyEnabled() ? rules.killCurrency() : 0;
    }
    public boolean setBreakthroughTickets(int value) {
        if (!canEditBreakthroughState() || !breakthroughRuntime.setTicketsValue(value)) return false;
        syncAll();
        return true;
    }
    public boolean setBreakthroughLeg(int value) {
        if (!canEditBreakthroughState()) return false;
        ArenaMap map = data().activeMap();
        if (map == null || !breakthroughRuntime.setLeg(server, this, map, rules(), value)) return false;
        syncAll();
        return true;
    }
    public boolean setBreakthroughSector(int value) {
        if (!canEditBreakthroughState()) return false;
        ArenaMap map = data().activeMap();
        if (map == null || !breakthroughRuntime.setSector(server, this, map, rules(), value)) return false;
        syncAll();
        return true;
    }
    private boolean canEditBreakthroughState() {
        return phase == MatchPhase.RUNNING && server != null
                && GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode());
    }
    int commonRemainingSeconds(MatchRules rules) {
        long ticks = commonRemainingTicks(rules);
        if (ticks == Long.MAX_VALUE) return MatchRules.UNLIMITED_TIME_SECONDS;
        ticks = Math.max(0L, ticks);
        return (int) Math.min(Integer.MAX_VALUE, (ticks + 19L) / 20L);
    }
    void setCommonRemainingSeconds(MatchRules rules, int seconds) {
        commonTimeOverrideEndTick = seconds == MatchRules.UNLIMITED_TIME_SECONDS
                ? Long.MAX_VALUE : elapsedTicks + seconds * 20L;
    }
    boolean commonTimeExpired(MatchRules rules) {
        long ticks = commonRemainingTicks(rules);
        return ticks != Long.MAX_VALUE && ticks <= 0L;
    }
    private long commonRemainingTicks(MatchRules rules) {
        if (commonTimeOverrideEndTick != null) {
            return commonTimeOverrideEndTick == Long.MAX_VALUE
                    ? Long.MAX_VALUE : commonTimeOverrideEndTick - elapsedTicks;
        }
        if (rules.timeLimitSeconds() == MatchRules.UNLIMITED_TIME_SECONDS) return Long.MAX_VALUE;
        return rules.timeLimitSeconds() * 20L - elapsedTicks;
    }
    public boolean restoringMap() { return mapRestoreSession != null; }
    public boolean devMode() { return server != null && data().devMode(); }
    public float mapRestoreProgress() { return mapRestoreSession == null ? 0.0F : mapRestoreSession.progress(); }
    public long mapRestoreElapsedMillis() { return mapRestoreSession == null ? 0L : mapRestoreSession.elapsedMillis(); }
    public int restoredPartitions() { return mapRestoreSession == null ? 0 : mapRestoreSession.completedPartitions(); }
    public int totalRestorePartitions() { return mapRestoreSession == null ? 0 : mapRestoreSession.totalPartitions(); }
    public boolean modeBlocksCombat() { return phase == MatchPhase.RUNNING && activeRuntime.blocksCombat(); }
    public boolean canBreakBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        return phase == MatchPhase.RUNNING && state(player).participating()
                && canEditMapBlock(player.serverLevel(), pos, state);
    }

    public Component mapEditDenialReason(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                         net.minecraft.world.level.block.state.BlockState blockState) {
        if (phase != MatchPhase.RUNNING || !state(player).participating()) {
            return Component.translatable("sfgame.map_edit.denied.match");
        }
        if (!rules().mapBlockBreaking()) return Component.translatable("sfgame.map_edit.denied.disabled");
        if (!activeRuntime.allowsMapEditing()) return Component.translatable("sfgame.map_edit.denied.mode_locked");
        ArenaMap map = data().activeMap();
        if (map == null || map.build().region() == null) {
            return Component.translatable("sfgame.map_edit.denied.no_region");
        }
        if (!isInsideBuildRegion(player.serverLevel(), pos)) {
            com.sfgame.data.BoxCaptureRegion region = map.build().region();
            return Component.translatable("sfgame.map_edit.denied.outside",
                    pos.getX(), pos.getY(), pos.getZ(),
                    net.minecraft.util.Mth.floor(region.minX()), net.minecraft.util.Mth.floor(region.maxX()),
                    net.minecraft.util.Mth.floor(region.minZ()), net.minecraft.util.Mth.floor(region.maxZ()));
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
        return Component.translatable("sfgame.map_edit.denied.not_allowed", id);
    }
    public boolean canPlaceBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        return phase == MatchPhase.RUNNING && state(player).participating()
                && canEditMapBlock(player.serverLevel(), pos, state);
    }

    /** Used by explosions, TACZ projectiles and Superb Warfare vehicle/projectile destruction. */
    public boolean canExternalDestroyBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                           net.minecraft.world.level.block.state.BlockState state) {
        // The result countdown is a hard map-lock period. This also covers
        // TACZ/Superb Warfare explosions and vehicle code paths intercepted
        // by LevelBlockProtectionMixin.
        if (phase == MatchPhase.RESULT) return false;
        if (mapRestoreSession != null && isInsideBuildRegion(level, pos)) return false;
        return phase != MatchPhase.RUNNING || canEditMapBlock(level, pos, state);
    }

    private boolean canEditMapBlock(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                    net.minecraft.world.level.block.state.BlockState state) {
        if (!rules().mapBlockBreaking() || !activeRuntime.allowsMapEditing()) return false;
        ArenaMap map = data().activeMap();
        if (map == null || !isInsideBuildRegion(level, pos)) return false;
        return rules().allowsMapBlock(state);
    }

    private boolean isInsideBuildRegion(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        ArenaMap map = data().activeMap();
        if (map == null || map.build().region() == null) return false;
        return containsBuildBlock(map.build().region(), level.dimension().location().toString(), pos);
    }

    static boolean containsBuildBlock(com.sfgame.data.BoxCaptureRegion region, String dimension,
                                      net.minecraft.core.BlockPos pos) {
        if (!dimension.equals(region.dimension())) return false;
        // Build selections are made while standing in blocks, so compare
        // integer block coordinates inclusively. Using block centres against
        // the player's fractional corner coordinates excluded both boundary
        // rows from otherwise valid selections.
        int minX = net.minecraft.util.Mth.floor(region.minX());
        int maxX = net.minecraft.util.Mth.floor(region.maxX());
        int minZ = net.minecraft.util.Mth.floor(region.minZ());
        int maxZ = net.minecraft.util.Mth.floor(region.maxZ());
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getZ() >= minZ && pos.getZ() <= maxZ
                && (region.minY() == null || pos.getY() >= region.minY())
                && (region.maxY() == null || pos.getY() <= region.maxY());
    }
    public boolean ctfCarrierCannotUseWeapons(ServerPlayer player) {
        return isCtfCarrier(player) && ctfCarrierRestriction() == com.sfgame.data.CarrierRestriction.NO_WEAPONS;
    }
    public boolean isCtfCarrier(ServerPlayer player) {
        return GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode()) && captureTheFlagRuntime.isCarrier(player.getUUID());
    }
    public com.sfgame.data.CarrierRestriction ctfCarrierRestriction() {
        return captureTheFlagRuntime.carrierRestriction();
    }

    public boolean purchase(ServerPlayer player, String itemId) {
        String modeId = data().selectedMode();
        if (!economyEnabled() || phase != MatchPhase.RUNNING
                || !state(player).participating() || state(player).respawning()) return false;
        ShopRegistry.ShopItem item = shopRegistry.item(modeId, itemId);
        if (item == null) return false;
        ItemStack stack = item.stack();
        if (stack.isEmpty()) return false;
        PlayerMatchState playerState = state(player);
        int balance = playerState.currency(modeId);
        if (balance < item.price()) return false;
        if (player.getInventory().getFreeSlot() < 0
                && player.getInventory().getSlotWithRemainingSpace(stack) < 0) return false;
        if (!player.getInventory().add(stack)) return false;
        playerState.currency(modeId, balance - item.price());
        player.sendSystemMessage(Component.translatable("sfgame.shop.bought.colored", item.name()), true);
        sync(player);
        return true;
    }
    public boolean pushSupplyPreset(TeamSide side, String offerId, int quantity) {
        if (!canMutateSupply(side)) return false;
        com.sfgame.data.SupplyOfferDefinition offer = data().activeMap() == null ? null
                : data().activeMap().supply().offer(offerId).orElse(null);
        if (offer == null) return false;
        if (com.sfgame.data.SupplyOfferDefinition.ELITE_CLASS.equals(offer.type())
                && !classRegistry.containsEliteForTeam(data().selectedMode(), data().selectedMap(),
                side, offer.classId())) return false;
        if (!supplyService.publishPreset(side, offerId, quantity)) return false;
        notifySupplyAvailable(side, offerId);
        syncAll();
        return true;
    }

    public boolean pushSupplyItem(TeamSide side, String offerId, int count, int quantity, String item) {
        if (!canMutateSupply(side) || !supplyService.publishItem(side, offerId, item, count, "", quantity)) return false;
        notifySupplyAvailable(side, offerId);
        syncAll();
        return true;
    }

    public boolean pushSupplyElite(TeamSide side, String offerId, String classId, int quantity) {
        if (!canMutateSupply(side)) return false;
        ClassDefinition definition = classRegistry.getEliteForTeam(
                data().selectedMode(), data().selectedMap(), side, classId).orElse(null);
        if (definition == null || !supplyService.publishElite(side, offerId, definition, quantity)) return false;
        notifySupplyAvailable(side, offerId);
        syncAll();
        return true;
    }

    public boolean removeSupply(TeamSide side, String offerId) {
        if (!canMutateSupply(side) || !supplyService.remove(side, offerId)) return false;
        syncAll();
        return true;
    }

    public int clearSupplies(TeamSide side) {
        if (!canMutateSupply(side)) return -1;
        int removed = supplyService.clear(side);
        syncAll();
        return removed;
    }

    private boolean canMutateSupply(TeamSide side) {
        return phase == MatchPhase.RUNNING && server != null && supportsSupply(data().selectedMode())
                && side != null && side != TeamSide.NONE && data().enabledTeams().contains(side);
    }

    public boolean canChangeArena() {
        return phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED;
    }

    public void arenaSelectionChanged() {
        if (server == null || !canChangeArena()) return;
        phase = data().isArenaConfigured() && teams.bindingsValid(server, data())
                ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        syncAll();
    }
    /** Persists the selected map after a command mutates its in-memory topology. */
    public void saveActiveMapConfiguration() {
        if (server == null) return;
        SFGameSavedData data = data();
        ArenaMap map = data.activeMap();
        if (map == null) return;
        mapConfigRegistry.saveMap(data.selectedMode(), map);
        classRegistry.ensureMapProfile(data.selectedMode(), map.id());
        data.setDirty();
    }
    public void createMapConfiguration(String modeId, String mapId) {
        mapConfigRegistry.createMap(modeId, mapId);
        classRegistry.createMapProfile(modeId, mapId);
        reloadMapOwnedConfigurations();
    }

    public void ensureMapConfigurationRemovable(String modeId, String mapId) {
        List<String> references = new ArrayList<>();
        references.addAll(ruleConfigRegistry.referencesTo(modeId, mapId));
        references.addAll(classRegistry.referencesTo(modeId, mapId));
        if (!references.isEmpty()) {
            throw new IllegalStateException("Map " + modeId + "/" + mapId
                    + " is inherited by: " + String.join(", ", new java.util.LinkedHashSet<>(references)));
        }
    }


    public void saveMapConfiguration(String modeId, ArenaMap map) {
        mapConfigRegistry.saveMap(modeId, map);
    }

    public void removeMapConfiguration(String modeId, String mapId) {
        mapConfigRegistry.deleteMap(modeId, mapId);
        reloadMapOwnedConfigurations();
    }

    private void reloadMapOwnedConfigurations() {
        SFGameSavedData data = data();
        List<String> problems = new ArrayList<>(ruleConfigRegistry.reload(data));
        for (String problem : classRegistry.reload(data)) if (!problems.contains(problem)) problems.add(problem);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Could not reload map configuration: " + String.join("; ", problems));
        }
    }


    /**
     * Re-send the vanilla Brigadier command tree after a mode change.  The
     * mode-specific rule branches use source predicates, and the client only
     * evaluates those predicates when the server sends this tree.
     */
    public void refreshCommandTree() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    public void serverStarted(MinecraftServer server) {
        this.server = server;
        beaconService.clear(server);
        squadService.clear();
        teamCaptainService.clear(server);
        SFGameSavedData data = data();
        Path configRoot = SFGameServerConfigPaths.root(server);
        mapConfigRegistry.useConfigRoot(configRoot);
        ruleConfigRegistry.useConfigRoot(configRoot);
        classRegistry.useConfigRoot(configRoot);
        shopRegistry.useConfigRoot(configRoot);
        teams.ensureDefaultTeams(server, data);
        List<String> mapErrors = mapConfigRegistry.reload(data);
        if (!mapErrors.isEmpty()) SFGame.LOGGER.warn("SFGame map configuration errors: {}", mapErrors);
        List<String> errors = classRegistry.reload(data);
        if (!errors.isEmpty()) SFGame.LOGGER.warn("SFGame class configuration errors: {}", errors);
        List<String> shopErrors = shopRegistry.reload();
        if (!shopErrors.isEmpty()) SFGame.LOGGER.warn("SFGame shop configuration errors: {}", shopErrors);
        List<String> ruleErrors = ruleConfigRegistry.reload(data);
        if (!ruleErrors.isEmpty()) SFGame.LOGGER.warn("SFGame rule configuration errors: {}", ruleErrors);
        phase = data.isArenaConfigured() && teams.bindingsValid(server, data) ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        resetRuntime();
    }

    public void serverStopped() {
        clearMapRestoreState();
        activeRuntime.stop();
        supplyService.clear();
        beaconService.clear(server);
        squadService.clear();
        teamCaptainService.clear(server);
        players.clear();
        server = null;
        phase = MatchPhase.UNCONFIGURED;
    }

    public void tick() {
        if (server == null) return;
        SFGameSavedData data = data();
        if (!teams.bindingsValid(server, data)) {
            if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
                stop(false, Component.literal("Bound vanilla team was removed"));
            }
            phase = MatchPhase.UNCONFIGURED;
            return;
        }
        if (phase == MatchPhase.UNCONFIGURED && data.isArenaConfigured()) phase = MatchPhase.LOBBY;
        if (phase == MatchPhase.LOBBY && !data.isArenaConfigured()) phase = MatchPhase.UNCONFIGURED;

        syncVanillaTeams();
        squadService.tick();
        beaconService.tick(server);
        if (phase == MatchPhase.RUNNING) teamCaptainService.maintain(server, this, rules());
        tickPlayerTimers();
        if (phase == MatchPhase.PREPARING) tickPreparing();
        if (phase == MatchPhase.COUNTDOWN) tickCountdown();
        if (phase == MatchPhase.RUNNING) tickRunning();
        if (phase == MatchPhase.RESULT) tickResult();

        if (++syncTicker >= 20) {
            syncTicker = 0;
            syncAll();
        }
    }

    public List<String> validateStart() {
        List<String> errors = new ArrayList<>();
        if (server == null) return List.of("Server is not running");
        SFGameSavedData data = data();
        boolean devMode = data.devMode();
        if (!data.isArenaConfigured()) errors.add("Lobby and spawn points for the selected map must be set");
        if (!teams.bindingsValid(server, data)) errors.add("All four SFGame sides must bind different existing vanilla teams");
        boolean captainMode = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())
                && rules().breakthroughVariant() == BreakthroughVariant.CAPTAIN;
        List<TeamSide> enabledTeams = data.enabledTeams();
        errors.addAll(loadoutService.validate(classRegistry, data.selectedMode(), data.selectedMap(), enabledTeams, captainMode));
        if (data.activeMap() != null) errors.addAll(modeRuntime().validate(server, data.activeMap(), rules()));
        if (data.activeMap() != null && supportsSupply(data.selectedMode())) {
            TeamSide supplyAttacker = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())
                    ? rules().breakthroughAttacker()
                    : GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode()) ? rules().ctfAttacker() : TeamSide.NONE;
            TeamSide supplyDefender = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())
                    ? rules().breakthroughDefender()
                    : GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode()) ? rules().ctfDefender() : TeamSide.NONE;
            errors.addAll(supplyService.validate(data.activeMap(), data.selectedMode(), supplyAttacker,
                    supplyDefender, classRegistry, data.selectedMap()));
        }
        if (rules().mapBlockBreaking() && data.activeMap() != null) {
            if (data.activeMap().build().region() == null) errors.add("Map build box must be set while mapBlockBreaking is enabled");
            else if (!MapBuildSnapshotService.exists(server, data.selectedMode(), data.activeMap(),
                    rules().mapSnapshotMode(), rules().mapBlockAllowlist())) {
                errors.add("Map snapshot must be saved while mapBlockBreaking is enabled");
            }
        }

        if (enabledTeams.size() < (devMode ? 1 : 2)) {
            errors.add(devMode ? "The selected map needs at least one enabled team"
                    : "The selected map needs spawn points for at least two teams");
        }
        int total = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeamSide side = teams.sideOf(player, data);
            if (!enabledTeams.contains(side)) continue;
            total++;
            PlayerMatchState state = state(player);
            state.cachedSide(side);
            ensureDefaultClass(state, side);
            String selected = selectedClass(state, side);
            if (!classRegistry.containsForTeam(data.selectedMode(), data.selectedMap(), side, selected)) errors.add(player.getGameProfile().getName() + " has no valid class for " + side.id());
        }
        if (!devMode) {
            for (TeamSide side : enabledTeams) {
                if (countSide(side) == 0) errors.add(side.id() + " team needs at least one player");
            }
        } else if (total == 0) {
            errors.add("At least one player is required to start in dev mode");
        }
        if (!rules().permitsPlayerCount(total)) errors.add("Player count exceeds maxPlayers");
        return errors;
    }

    public boolean start() {
        if (server == null || (phase != MatchPhase.LOBBY && phase != MatchPhase.UNCONFIGURED)) return false;
        if (!validateStart().isEmpty()) return false;
        MapBuildSnapshotService.RestoreSession pendingRestore = null;
        if (rules().mapBlockBreaking()) {
            try {
                pendingRestore = MapBuildSnapshotService.beginRestore(server, data().selectedMode(), data().activeMap(),
                        rules().mapSnapshotMode(), rules().mapBlockAllowlist());
            } catch (Exception exception) {
                server.getPlayerList().broadcastSystemMessage(Component.translatable(
                        "sfgame.map_restore.failed", exception.getMessage()), false);
                return false;
            }
        }
        redScore = 0;
        blueScore = 0;
        yellowScore = 0;
        greenScore = 0;
        elapsedTicks = 0;
        commonTimeOverrideEndTick = null;
        result = TeamSide.NONE;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeamSide side = teams.sideOf(player, data());
            PlayerMatchState state = state(player);
            state.resetRoundStats();
            state.cachedSide(side);
            if (data().enabledTeams().contains(side)) {
                state.participating(true);
                state.queued(false);
                String modeId = data().selectedMode();
                ensureDefaultClass(state, side);
                if (state.currentClass(modeId, side) == null) state.currentClass(modeId, side, state.pendingClass(modeId, side));
                player.setGameMode(GameType.ADVENTURE);
            } else {
                state.participating(false);
                player.setGameMode(GameType.SPECTATOR);
            }
        }
        activeRuntime = modeRuntime();
        if (pendingRestore != null) {
            mapRestoreSession = pendingRestore;
            mapRestoreCooldownTicks = 0;
            mapRestoreAdaptiveSkips = 0;
            mapRestorePartitionMillisEstimate = 0.0;
            modePreparationStarted = false;
            phase = MatchPhase.PREPARING;
            ArenaPosition safeLobby = data().lobby();
            if (safeLobby != null) forParticipants(player -> {
                safeLobby.teleport(player);
                player.fallDistance = 0.0F;
            });
            mapRestoreBar = new ServerBossEvent(Component.translatable("sfgame.map_restore.progress", 0,
                    pendingRestore.totalPartitions(), 0), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            mapRestoreBar.setProgress(0);
            forParticipants(mapRestoreBar::addPlayer);
        } else beginModePreparationOrCountdown();
        syncAll();
        return true;
    }

    public void stop(boolean showResult, Component reason) {
        if (server == null) return;
        clearMapRestoreState();
        activeRuntime.stop();
        supplyService.clear();
        beaconService.clear(server);
        squadService.clear();
        teamCaptainService.clear(server);
        anchorPreparationStarted = false;
        if (showResult) {
            phase = MatchPhase.RESULT;
            phaseTicks = rules().resultSeconds() * 20;
            announceResult();
        } else {
            server.getPlayerList().broadcastSystemMessage(reason, false);
            finishToLobby();
        }
    }

    public boolean queueOrJoinLobby(ServerPlayer player) {
        PlayerMatchState state = state(player);
        if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
            squadService.remove(player);
            state.queued(true);
            state.participating(false);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.respawnTicks(0);
            player.setGameMode(GameType.SPECTATOR);
            return true;
        }
        TeamSide currentSide = teams.sideOf(player, data());
        if (!data().enabledTeams().contains(currentSide)
                && !rules().permitsPlayerCount((long) countAssignedPlayers() + 1)) return false;
        if (!data().enabledTeams().contains(currentSide)) teams.assign(player, teams.balancedSide(server, data()), data());
        if (!data().enabledTeams().contains(teams.sideOf(player, data()))) return false;
        currentSide = teams.sideOf(player, data());
        state.cachedSide(currentSide);
        ensureDefaultClass(state, currentSide);
        state.queued(false);
        state.participating(false);
        state.pendingImmediateJoin(false);
        state.respawning(false);
        state.awaitingRespawnSelection(false);
        state.respawnTicks(0);
        state.respawnTicks(0);
        state.protectionTicks(0);
        state.cachedSide(currentSide);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        sync(player);
        return true;
    }

    public boolean joinFromMenu(ServerPlayer player) {
        PlayerMatchState state = state(player);
        boolean attackerElectionLocked = phase == MatchPhase.PREPARING
                && GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                && data().activeMap() != null
                && rules().breakthroughVariant() == BreakthroughVariant.CAPTAIN
                && breakthroughRuntime.electionSeconds() > 0
                && teams.sideOf(player, data()) == breakthroughRuntime.attacker();
        if (state.participating() || attackerElectionLocked) {
            player.sendSystemMessage(Component.translatable(attackerElectionLocked
                    ? "sfgame.menu.locked.election.error" : "sfgame.menu.locked.participating.error"));
            sync(player);
            return false;
        }
        return queueOrJoinLobby(player);
    }

    public boolean leaveFromMenu(ServerPlayer player) {
        if (isActiveMatchPhase()) {
            player.sendSystemMessage(Component.translatable("sfgame.menu.locked.command_leave.error"));
            sync(player);
            return false;
        }
        leave(player);
        return true;
    }

    public void leave(ServerPlayer player) {
        PlayerMatchState state = state(player);
        state.participating(false);
        state.queued(false);
        state.pendingImmediateJoin(false);
        state.respawning(false);
        state.awaitingRespawnSelection(false);
        squadService.remove(player);
        teams.remove(player);
        if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
            loadoutService.clear(player);
            player.setGameMode(GameType.SPECTATOR);
        }
        sync(player);
    }

    public boolean joinNow(ServerPlayer player) {
        if (phase != MatchPhase.RUNNING || !rules().permitsPlayerCount((long) participatingCount() + 1)) return false;
        PlayerMatchState state = state(player);
        if (!data().enabledTeams().contains(teams.sideOf(player, data()))) {
            teams.assign(player, teams.balancedSide(server, data()), data());
        }
        state.cachedSide(teams.sideOf(player, data()));
        state.queued(false);
        state.pendingImmediateJoin(true);
        state.participating(false);
        state.awaitingRespawnSelection(false);
        state.resetRoundStats();
        player.setGameMode(GameType.SPECTATOR);
        TeamSide joinSide = teams.sideOf(player, data());
        state.cachedSide(joinSide);
        if (classRegistry.containsForTeam(data().selectedMode(), data().selectedMap(), joinSide, selectedClass(state, joinSide))) {
            activateImmediateJoin(player, state);
        } else {
            SFGameNetwork.openMenu(player);
        }
        sync(player);
        return true;
    }

    public boolean selectClass(ServerPlayer player, String classId) {
        String modeId = data().selectedMode();
        PlayerMatchState state = state(player);
        TeamSide side = classSide(player, state);
        Optional<ClassDefinition> definition = classRegistry.getForTeam(modeId, data().selectedMap(), side, classId);
        if (definition.isEmpty()) return false;
        state.pendingClass(modeId, side, definition.get().id());
        state.grantedEliteClass(modeId, side, null);
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED) state.currentClass(modeId, side, definition.get().id());
        if (state.pendingImmediateJoin() && phase == MatchPhase.RUNNING) activateImmediateJoin(player, state);
        sync(player);
        return true;
    }

    public boolean selectCaptainClass(ServerPlayer player, String classId) {
        String modeId = data().selectedMode();
        PlayerMatchState state = state(player);
        TeamSide side = classSide(player, state);
        Optional<ClassDefinition> definition = classRegistry.getCaptainForTeam(modeId, data().selectedMap(), side, classId);
        if (definition.isEmpty()) return false;
        state.grantedEliteClass(modeId, side, null);
        state.pendingCaptainClass(modeId, side, definition.get().id());
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED || phase == MatchPhase.PREPARING) {
            state.currentCaptainClass(modeId, side, definition.get().id());
        }
        sync(player);
        return true;
    }

    public boolean voteAnchorCaptain(ServerPlayer voter, @Nullable ServerPlayer candidate, boolean abstain) {
        if (!teamCaptainService.supports(data().selectedMode())) return false;
        TeamSide side = teams.sideOf(voter, data());
        return teamCaptainService.electionSeconds(side) > 0
                && teamCaptainService.vote(voter, candidate, abstain, this);
    }
    public boolean selectRespawn(ServerPlayer player, String optionId) {
        PlayerMatchState state = state(player);
        if (phase != MatchPhase.RUNNING || !usesRespawnSelectionMode(data().selectedMode())
                || !state.participating() || !state.respawning() || !state.awaitingRespawnSelection()) return false;
        RespawnSourceResolver.RespawnTarget target = respawnSourceResolver.resolve(player, optionId);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("sfgame.respawn.option_unavailable.error"));
            SFGameNetwork.openMenu(player);
            return false;
        }
        deploy(player, state, true, target);
        return !state.respawning();
    }

    public void playerLoggedIn(ServerPlayer player) {
        PlayerMatchState state = state(player);
        state.connected(true);
        TeamSide side = teams.sideOf(player, data());
        state.cachedSide(side);
        if (phase == MatchPhase.RUNNING && state.participating() && side != TeamSide.NONE) {
            deploy(player, state, false);
        } else if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
            state.queued(true);
            state.participating(false);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.respawnTicks(0);
            player.setGameMode(GameType.SPECTATOR);
        } else {
            loadoutService.clear(player);
            state.participating(false);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            player.setGameMode(GameType.ADVENTURE);
            if (data().lobby() != null) data().lobby().teleport(player);
        }
        breakthroughRuntime.clearStaleCaptainAppearance(player);
        sync(player);
    }

    public void playerLoggedOut(ServerPlayer player) {
        state(player).connected(false);
        squadService.remove(player);
        activeRuntime.onPlayerLoggedOut(player, this);
        if (phase == MatchPhase.RUNNING) teamCaptainService.onPlayerLoggedOut(player, this, rules());
    }

    public void handleDeath(ServerPlayer victim, DamageSource source) {
        if (phase != MatchPhase.RUNNING) return;
        PlayerMatchState victimState = state(victim);
        if (!victimState.participating()) return;
        victimState.addDeath();
        TeamSide victimSide = teams.sideOf(victim, data());
        activeRuntime.onPlayerDeath(victim, victimSide, this);
        teamCaptainService.onPlayerDeath(victim, this);
        Player attacker = source.getEntity() instanceof Player player ? player
                : source.getDirectEntity() instanceof Player player ? player : null;
        if (attacker instanceof ServerPlayer serverAttacker && serverAttacker != victim) {
            PlayerMatchState attackerState = state(serverAttacker);
            TeamSide attackerSide = teams.sideOf(serverAttacker, data());
            if (attackerState.participating() && attackerSide != TeamSide.NONE && attackerSide != victimSide) {
                attackerState.addKill();
                int reward = killCurrencyFor(data().selectedMode(), rules());
                if (reward > 0) addCurrency(serverAttacker, reward);
                activeRuntime.onKill(serverAttacker, attackerSide, this);
            }
        }
        victimState.respawning(true);
        victimState.awaitingRespawnSelection(false);
        victimState.respawnTicks(rules().respawnSeconds() * 20);
        loadoutService.clear(victim);
        victim.setHealth(victim.getMaxHealth());
        victim.setGameMode(GameType.SPECTATOR);
        syncAll();
    }

    public boolean claimSupply(ServerPlayer player, String offerId) {
        if (phase != MatchPhase.RUNNING || !supportsSupply(data().selectedMode())) return false;
        PlayerMatchState state = state(player);
        if (!state.participating() || state.respawning() || player.isSpectator() || player.isDeadOrDying()) return false;
        TeamSide side = teams.sideOf(player, data());
        if (side == TeamSide.NONE) return false;
        SupplyService.PublishedSupply supply = supplyService.item(side, offerId).orElse(null);
        if (supply == null || supply.quantity() <= 0) return false;
        Component granted;
        if (com.sfgame.data.SupplyOfferDefinition.ITEM.equals(supply.type())) {
            ItemStack stack = supply.stack();
            if (stack.isEmpty()) return false;
            if (player.getInventory().getFreeSlot() < 0
                    && player.getInventory().getSlotWithRemainingSpace(stack) < 0) return false;
            if (!player.getInventory().add(stack)) return false;
            granted = Component.translatable("sfgame.supply.item_granted.colored", stack.getHoverName());
        } else if (com.sfgame.data.SupplyOfferDefinition.ELITE_CLASS.equals(supply.type())) {
            ClassDefinition definition = classRegistry.getEliteForTeam(
                    data().selectedMode(), data().selectedMap(), side, supply.classId()).orElse(null);
            if (definition == null || !loadoutService.apply(player, definition)) return false;
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            state.grantedEliteClass(data().selectedMode(), side, definition.id());
            state.currentClass(data().selectedMode(), side, definition.id());
            state.pendingClass(data().selectedMode(), side, definition.id());
            granted = Component.translatable("sfgame.supply.elite_granted.colored", definition.displayName());
        } else {
            return false;
        }
        if (!supplyService.consume(side, offerId)) return false;
        player.sendSystemMessage(granted, true);
        syncAll();
        return true;
    }

    public boolean redeploy(ServerPlayer player) {
        PlayerMatchState state = state(player);
        if (phase != MatchPhase.RUNNING || !state.participating()) return false;
        state.cachedSide(teams.sideOf(player, data()));
        deploy(player, state, false);
        return true;
    }

    public boolean areFriendly(ServerPlayer first, ServerPlayer second) {
        if (phase != MatchPhase.RUNNING) return false;
        PlayerMatchState a = state(first);
        PlayerMatchState b = state(second);
        TeamSide side = teams.sideOf(first, data());
        return a.participating() && b.participating() && side != TeamSide.NONE && side == teams.sideOf(second, data());
    }

    public boolean isProtected(ServerPlayer player) {
        return state(player).protectionTicks() > 0;
    }

    public void removeProtection(ServerPlayer player) {
        state(player).protectionTicks(0);
    }

    public boolean mayDrop(ServerPlayer player) {
        PlayerMatchState state = state(player);
        if (!state.participating()) return true;
        String modeId = data().selectedMode();
        TeamSide side = classSide(player, state);
        boolean captain = activeRuntime.isCaptain(player.getUUID());
        return (captain ? classRegistry.getCaptainForTeam(modeId, data().selectedMap(), side, state.currentCaptainClass(modeId, side))
                : classRegistry.getForTeam(modeId, data().selectedMap(), side, state.currentClass(modeId, side)))
                .map(ClassDefinition::allowDrop).orElse(false);
    }

    public void setRule(String key, int value) {
        ensureRuleChangeAllowed(key);
        boolean domination = GameModeRegistry.DOMINATION.equals(data().selectedMode());
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode());
        boolean ctf = GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode());
        if (!domination && !breakthrough && !ctf && key.startsWith("capture")) {
            throw new IllegalArgumentException(key + " is only available in a capture mode");
        }
        if (!domination && (key.equals("scoreIntervalSeconds") || key.equals("scorePerPoint") || key.equals("syncHoldSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in domination mode");
        }
        if (!domination && !breakthrough && !ctf && (key.equals("attackerTickets") || key.equals("sectorTransitionSeconds")
                || key.equals("captainVoteSeconds") || key.equals("captainReplacementVoteSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in a capture mode");
        }
        if (!ctf && key.equals("sectorTransitionSeconds")) {
            throw new IllegalArgumentException(key + " is only available in breakthrough mode");
        }
        if (!ctf && (key.equals("ctfFlagReturnSeconds") || key.equals("ctfHomeCaptureTimeSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in CTF mode");
        }
        ruleConfigRegistry.setInt(data().selectedMode(), data().selectedMap(), key, value);
        onRuleChanged(key);
        syncAll();
    }

    public void setRule(String key, boolean value) {
        ensureRuleChangeAllowed(key);
        boolean domination = GameModeRegistry.DOMINATION.equals(data().selectedMode());
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode());
        boolean ctf = GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode());
        switch (key) {
            case "captureUsePlayerDifference" -> {
                if (!domination && !breakthrough && !ctf) throw new IllegalArgumentException(key + " is only available in a capture mode");
            }
            case "attackerCaptainGlowing" -> {
                if (!breakthrough) throw new IllegalArgumentException(key + " is only available in breakthrough mode");
            }
            case "mapBlockBreaking" -> {
                if (value && (data().activeMap() == null || data().activeMap().build().region() == null
                        || !MapBuildSnapshotService.exists(server, data().selectedMode(), data().activeMap(),
                        rules().mapSnapshotMode(), rules().mapBlockAllowlist()))) {
                    throw new IllegalArgumentException("Set the map build box and save its snapshot before enabling mapBlockBreaking");
                }
            }
            case "showUnlimitedTime" -> { }
            case "showUnlimitedTickets" -> {
                if (!breakthrough && !ctf) {
                    throw new IllegalArgumentException(key + " is only available in breakthrough or ctf mode");
                }
            }
            case "mapRestoreAdaptiveThrottling", "economyEnabled" -> {
                if ("economyEnabled".equals(key) && !domination && !breakthrough && !ctf) {
                    throw new IllegalArgumentException(key + " is only available in a capture mode");
                }
            }
            default -> throw new IllegalArgumentException("Unknown boolean rule " + key);
        }
        ruleConfigRegistry.setBoolean(data().selectedMode(), data().selectedMap(), key, value);
        onRuleChanged(key);
        syncAll();
    }

    public void setRule(String key, double value) {
        ensureRuleChangeAllowed(key);
        String mode = data().selectedMode();
        boolean domination = GameModeRegistry.DOMINATION.equals(mode);
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(mode);
        boolean ctf = GameModeRegistry.CAPTURE_THE_FLAG.equals(mode);
        if (!domination && !breakthrough && !ctf) throw new IllegalArgumentException(key + " is only available in a capture mode");
        switch (key) {
            case "captureDifferenceCoefficient" -> { }
            case "attackerCaptainCaptureWeight", "defenderCaptureWeight" -> {
                if (!breakthrough) throw new IllegalArgumentException(key + " is only available in breakthrough mode");
            }
            default -> throw new IllegalArgumentException("Unknown decimal rule " + key);
        }
        ruleConfigRegistry.setDouble(mode, data().selectedMap(), key, value);
        onRuleChanged(key);
        syncAll();
    }

    public void setRule(String key, String value) {
        ensureRuleChangeAllowed(key);
        AdminRuleCatalog.Definition definition = AdminRuleCatalog.find(data().selectedMode(), key)
                .filter(candidate -> candidate.type() == AdminRuleCatalog.ValueType.ENUM)
                .orElseThrow(() -> new IllegalArgumentException("Unknown enum rule " + key));
        String normalized = (String) AdminRuleCatalog.parse(definition, value);
        ruleConfigRegistry.setString(data().selectedMode(), data().selectedMap(), key, normalized);
        onRuleChanged(key);
        syncAll();
    }

    public void resetRules() {
        if (isActiveMatchPhase()) throw new IllegalStateException("Rules can only be reset in the lobby");
        ruleConfigRegistry.resetMap(data().selectedMode(), data().selectedMap());
        onRuleChanged("attackerTickets");
        syncAll();
    }

    public void setRuleParent(String parent) {
        if (isActiveMatchPhase()) throw new IllegalStateException("Rule inheritance can only change in the lobby");
        ruleConfigRegistry.setParent(data().selectedMode(), data().selectedMap(), parent);
        onRuleChanged("attackerTickets");
        syncAll();
    }

    public List<String> reloadRuleConfigurations() {
        if (isActiveMatchPhase()) {
            return List.of("Rule files can only be reloaded in the lobby; use /sfgame rule set for live rules");
        }
        List<String> errors = ruleConfigRegistry.reload(data());
        if (errors.isEmpty()) {
            onRuleChanged("attackerTickets");
            arenaSelectionChanged();
            syncAll();
        }
        return errors;
    }
    public List<String> reloadMapConfigurations() {
        if (isActiveMatchPhase()) {
            return List.of("Map and supply files can only be reloaded in the lobby");
        }
        List<String> errors = mapConfigRegistry.reload(data());
        if (errors.isEmpty()) {
            arenaSelectionChanged();
            refreshCommandTree();
            syncAll();
        }
        return errors;
    }


    private void onRuleChanged(String key) {
        MatchRules current = rules();
        activeRuntime.onRuleChanged(key, current);
        teamCaptainService.onRuleChanged(key, current);
        if ("respawnBeaconHealth".equals(key)) beaconService.onRuleChanged(current);
        if ("squadMaxMembers".equals(key)) squadService.tick();
    }
    private void ensureRuleChangeAllowed(String key) {
        AdminRuleCatalog.find(data().selectedMode(), key).ifPresent(definition -> {
            if (!definition.hotReload() && isActiveMatchPhase()) {
                throw new IllegalStateException(key + " can only be changed in the lobby");
            }
        });
    }

    public MatchSnapshot snapshot(ServerPlayer viewer) {
        MatchRules rules = rules();
        PlayerMatchState state = state(viewer);
        TeamSide side = teams.sideOf(viewer, data());
        TeamSide classSide = side == TeamSide.NONE ? state.cachedSide() : side;
        int remaining = activeRuntime.remainingSeconds(this, rules);
        Collection<ClassDefinition> visibleClasses = classSide == TeamSide.NONE
                ? classRegistry.allForMode(data().selectedMode(), data().selectedMap())
                : classRegistry.allForTeam(data().selectedMode(), data().selectedMap(), classSide);
        Collection<ClassDefinition> visibleCaptainClasses = classSide == TeamSide.NONE
                ? classRegistry.captainClassesForMode(data().selectedMode(), data().selectedMap())
                : classRegistry.captainClassesForTeam(data().selectedMode(), data().selectedMap(), classSide);
        List<MatchSnapshot.ClassView> classViews = visibleClasses.stream()
                .map(c -> new MatchSnapshot.ClassView(c.id(), c.displayName(), c.description(), c.icon(), c.iconRender(),
                        c.iconTexture(), c.gunId(), c.maxHealth(), c.movementSpeedMultiplier(), c.reserveAmmo()))
                .toList();
        List<MatchSnapshot.ClassView> captainClassViews = visibleCaptainClasses.stream()
                .map(c -> new MatchSnapshot.ClassView(c.id(), c.displayName(), c.description(), c.icon(), c.iconRender(),
                        c.iconTexture(), c.gunId(), c.maxHealth(), c.movementSpeedMultiplier(), c.reserveAmmo())).toList();
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode()) && data().activeMap() != null;
        TeamSide attackSide = breakthrough && (phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RUNNING)
                ? breakthroughRuntime.attacker() : breakthrough ? rules.breakthroughAttacker() : TeamSide.NONE;
        TeamSide defenseSide = breakthrough && (phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RUNNING)
                ? breakthroughRuntime.defender() : breakthrough ? rules.breakthroughDefender() : TeamSide.NONE;
        UUID captainId = breakthrough ? breakthroughRuntime.captain() : null;
        ServerPlayer captainPlayer = captainId == null || server == null ? null : server.getPlayerList().getPlayer(captainId);
        List<MatchSnapshot.CaptainCandidate> candidates = !breakthrough || breakthroughRuntime.electionSeconds() <= 0 || side != attackSide
                ? List.of() : server.getPlayerList().getPlayers().stream()
                .filter(player -> state(player).participating() && teams.sideOf(player, data()) == attackSide)
                .map(player -> new MatchSnapshot.CaptainCandidate(player.getUUID().toString(), player.getGameProfile().getName())).toList();
        boolean anchorMode = teamCaptainService.supports(data().selectedMode())
                && (phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RUNNING);
        UUID anchorCaptainId = anchorMode ? teamCaptainService.captain(side) : null;
        ServerPlayer anchorCaptainPlayer = anchorCaptainId == null ? null : serverPlayer(anchorCaptainId);
        int anchorElectionSeconds = anchorMode ? teamCaptainService.electionSeconds(side) : 0;
        boolean anchorCaptain = anchorCaptainId != null && anchorCaptainId.equals(viewer.getUUID());
        List<MatchSnapshot.CaptainCandidate> anchorCandidates = anchorMode && anchorElectionSeconds > 0
                ? teamCaptainService.candidates(viewer, this).stream()
                .map(candidate -> new MatchSnapshot.CaptainCandidate(candidate.uuid(), candidate.name())).toList()
                : List.of();
        List<MatchSnapshot.RespawnOption> respawnOptions = state.awaitingRespawnSelection()
                ? respawnSourceResolver.options(viewer) : List.of();
        boolean ctf = GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode()) && data().activeMap() != null;
        boolean ctfAssault = ctf && rules.ctfVariant() == com.sfgame.data.CtfVariant.ASSAULT;
        String ctfVariant = ctf ? rules.ctfVariant().id() : null;
        String ctfRestriction = ctf ? rules.ctfCarrierRestriction().id() : null;
        List<MatchSnapshot.CtfFlagView> ctfFlags = ctf
                ? captureTheFlagRuntime.flagViews(this).stream()
                .map(flag -> new MatchSnapshot.CtfFlagView(flag.id(), flag.owner(), flag.state(), flag.carrier(), flag.unlocked(), flag.depotTeam()))
                .toList() : List.of();
        boolean economy = economyEnabled() && phase == MatchPhase.RUNNING;
        List<MatchSnapshot.ShopView> shopItems = economy ? shopRegistry.items(data().selectedMode()).stream()
                .map(item -> new MatchSnapshot.ShopView(item.id(), item.name(), item.icon(), item.price()))
                .toList() : List.of();
        List<MatchSnapshot.SupplyView> supplyItems = new ArrayList<>();
        if (phase == MatchPhase.RUNNING && supportsSupply(data().selectedMode()) && side != TeamSide.NONE) {
            for (SupplyService.PublishedSupply supply : supplyService.items(side)) {
                if (com.sfgame.data.SupplyOfferDefinition.ITEM.equals(supply.type())) {
                    ItemStack stack = supply.stack();
                    if (!stack.isEmpty()) {
                        supplyItems.add(new MatchSnapshot.SupplyView(supply.id(), supply.type(),
                                stack.getHoverName().getString(), supply.item(), supply.quantity()));
                    }
                } else {
                    classRegistry.getEliteForTeam(data().selectedMode(), data().selectedMap(), side, supply.classId())
                            .ifPresent(definition -> supplyItems.add(new MatchSnapshot.SupplyView(
                                    supply.id(), supply.type(), definition.displayName(),
                                    definition.icon(), supply.quantity())));
                }
            }
        }
        String mapName = data().activeMap() == null ? data().selectedMap() : data().activeMap().displayName();
        return new MatchSnapshot(data().selectedMode(), mapName, phase, side, redScore, blueScore, yellowScore, greenScore,
                rules.scoreLimit(), remaining, rules.showUnlimitedTime(), countSide(TeamSide.RED),
                countSide(TeamSide.BLUE), countSide(TeamSide.YELLOW), countSide(TeamSide.GREEN),
                state.currentClass(data().selectedMode(), classSide),
                state.pendingClass(data().selectedMode(), classSide),
                state.participating(), state.queued(), classViews,
                breakthrough ? rules.breakthroughVariant().name().toLowerCase(java.util.Locale.ROOT) : "",
                attackSide, defenseSide,
                breakthrough ? breakthroughRuntime.tickets() : ctfAssault ? captureTheFlagRuntime.attackerTickets() : 0,
                (breakthrough || ctfAssault) && rules.showUnlimitedTickets(),
                breakthrough ? breakthroughRuntime.attackRoundsRemaining() : 0,
                breakthrough ? breakthroughRuntime.remainingLegs(rules) : 0,
                breakthrough ? breakthroughRuntime.sectorNumber() : 0,
                breakthrough ? breakthroughRuntime.sectorCount(data().activeMap()) : 0,
                breakthrough ? breakthroughRuntime.subState() : "",
                captainId == null ? null : captainId.toString(),
                captainPlayer == null ? null : captainPlayer.getGameProfile().getName(),
                breakthrough ? breakthroughRuntime.electionSeconds() : 0,
                breakthrough && breakthroughRuntime.isCaptain(viewer.getUUID()),
                state.currentCaptainClass(data().selectedMode(), classSide),
                state.pendingCaptainClass(data().selectedMode(), classSide),
                captainClassViews, candidates,
                anchorCaptainId == null ? null : anchorCaptainId.toString(),
                anchorCaptainPlayer == null ? null : anchorCaptainPlayer.getGameProfile().getName(),
                anchorElectionSeconds, anchorCaptain, anchorCandidates,
                state.awaitingRespawnSelection(), respawnOptions,
                ctfVariant, ctfRestriction, economy, economy ? state.currency(data().selectedMode()) : 0,
                data().devMode(),
                ctfFlags, shopItems, List.copyOf(supplyItems));
    }

    public PlayerMatchState state(ServerPlayer player) {
        return players.computeIfAbsent(player.getUUID(), PlayerMatchState::new);
    }

    List<ServerPlayer> onlineParticipants() {
        if (server == null) return List.of();
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> state(player).participating()).toList();
    }

    List<ServerPlayer> onlineMatchViewers() {
        if (server == null) return List.of();
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> state(player).participating() || state(player).queued()).toList();
    }

    void supplyEvent(String event, TeamSide eventSide, int stage, String sectorId, String pointId) {
        if (!supportsSupply(data().selectedMode())) return;
        if (GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())) {
            supplyService.updateRoles(breakthroughRuntime.attacker(), breakthroughRuntime.defender());
        }
        Map<TeamSide, Map<String, Integer>> before = supplyQuantities();
        if (supplyService.fireEvent(event, eventSide, stage, sectorId, pointId)) {
            notifyNewSupplyChanges(before);
            syncAll();
        }
    }
    private Map<TeamSide, Map<String, Integer>> supplyQuantities() {
        Map<TeamSide, Map<String, Integer>> result = new HashMap<>();
        for (TeamSide side : TeamSide.PLAYABLE) {
            Map<String, Integer> quantities = new HashMap<>();
            for (SupplyService.PublishedSupply supply : supplyService.items(side)) {
                quantities.put(supply.id(), supply.quantity());
            }
            result.put(side, quantities);
        }
        return result;
    }

    private void notifyNewSupplyChanges(Map<TeamSide, Map<String, Integer>> before) {
        for (TeamSide side : TeamSide.PLAYABLE) {
            Map<String, Integer> previous = before.getOrDefault(side, Map.of());
            for (SupplyService.PublishedSupply supply : supplyService.items(side)) {
                if (supply.quantity() > previous.getOrDefault(supply.id(), 0)) {
                    notifySupplyAvailable(side, supply.id());
                }
            }
        }
    }

    private void notifySupplyAvailable(TeamSide side, String offerId) {
        if (server == null || phase != MatchPhase.RUNNING) return;
        SupplyService.PublishedSupply supply = supplyService.item(side, offerId).orElse(null);
        if (supply == null) return;
        String name;
        if (com.sfgame.data.SupplyOfferDefinition.ITEM.equals(supply.type())) {
            ItemStack stack = supply.stack();
            name = stack.isEmpty() ? supply.id() : stack.getHoverName().getString();
        } else {
            name = classRegistry.getEliteForTeam(data().selectedMode(), data().selectedMap(), side, supply.classId())
                    .map(ClassDefinition::displayName).orElse(supply.classId());
        }
        Component message = Component.translatable("sfgame.supply.available.colored", name);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!eligibleForSupplyNotification(player, side)) continue;
            player.sendSystemMessage(message);
        }
    }

    private boolean eligibleForSupplyNotification(ServerPlayer player, TeamSide side) {
        PlayerMatchState state = state(player);
        if (!state.participating() || state.respawning() || player.isSpectator() || player.isDeadOrDying()) return false;
        if (teams.sideOf(player, data()) != side) return false;
        return true;
    }


    void addCurrency(ServerPlayer player, int amount) {
        if (player == null || amount <= 0 || !economyEnabled()) return;
        PlayerMatchState state = state(player);
        String modeId = data().selectedMode();
        state.currency(modeId, Math.min(MAX_LIVE_SCORE, state.currency(modeId) + amount));
    }

    void addCurrencyToTeamPlayers(TeamSide side, int amount) {
        if (amount <= 0) return;
        for (ServerPlayer player : onlineParticipants()) {
            if (teams.sideOf(player, data()) == side) addCurrency(player, amount);
        }
    }

    private void tickCountdown() {
        if (phaseTicks > 0) phaseTicks--;
        if (phaseTicks % 20 == 0 && phaseTicks > 0) {
            Component title = Component.translatable("sfgame.match.countdown", phaseTicks / 20);
            forParticipants(player -> {
                sendTitle(player, title, Component.translatable("sfgame.match.starting.colored"), 24);
                playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), countdownPitch(phaseTicks / 20));
            });
        }
        if (phaseTicks <= 0) beginRunning();
    }

    private void tickPreparing() {
        if (mapRestoreSession != null) {
            tickMapRestore();
            return;
        }
        if (anchorPreparationStarted) {
            if (!teamCaptainService.tickPreparation(server, this, rules())) return;
            anchorPreparationStarted = false;
            beginCountdown();
            return;
        }
        if (modePreparationStarted && activeRuntime.tickPreparation(server, this, data().activeMap(), rules())) {
            modePreparationStarted = false;
            beginCountdown();
        }
    }

    private void tickMapRestore() {
        MatchRules rules = rules();
        if (mapRestoreCooldownTicks > 0) {
            mapRestoreCooldownTicks--;
            return;
        }

        float averageTickMillis = server.getAverageTickTime();
        boolean overloaded = rules.mapRestoreAdaptiveThrottling()
                && averageTickMillis > rules.mapRestoreTargetTickMillis();
        if (overloaded && ++mapRestoreAdaptiveSkips < 4) {
            double overload = averageTickMillis - rules.mapRestoreTargetTickMillis();
            mapRestoreCooldownTicks = Math.max(rules.mapRestorePartitionDelayTicks(),
                    Math.min(10, 1 + (int) Math.ceil(overload / 5.0)));
            return;
        }
        mapRestoreAdaptiveSkips = 0;

        double availableMillis = Math.max(1.0, rules.mapRestoreTargetTickMillis() - averageTickMillis);
        int adaptiveLimit = mapRestorePartitionMillisEstimate <= 0.0 ? 1
                : Math.max(1, (int) Math.floor(availableMillis / mapRestorePartitionMillisEstimate));
        int limit = rules.mapRestoreAdaptiveThrottling()
                ? Math.min(rules.mapRestoreMaxPartitionsPerTick(), adaptiveLimit)
                : rules.mapRestoreMaxPartitionsPerTick();
        if (overloaded || rules.mapRestorePartitionDelayTicks() > 0) limit = 1;
        long budgetNanos = (long) (availableMillis * 1_000_000.0);
        long started = System.nanoTime();
        try {
            for (int i = 0; i < limit && !mapRestoreSession.complete(); i++) {
                mapRestoreSession.restoreNext();
                double sample = mapRestoreSession.lastPartitionMillis();
                mapRestorePartitionMillisEstimate = mapRestorePartitionMillisEstimate <= 0.0 ? sample
                        : mapRestorePartitionMillisEstimate * 0.75 + sample * 0.25;
                if (rules.mapRestoreAdaptiveThrottling() && System.nanoTime() - started >= budgetNanos) break;
            }
        } catch (Exception exception) {
            SFGame.LOGGER.error("Map snapshot restore failed", exception);
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "sfgame.map_restore.failed", exception.getMessage()), false);
            stop(false, Component.literal("Map snapshot restore failed"));
            return;
        }

        updateMapRestoreBar();
        mapRestoreCooldownTicks = rules.mapRestorePartitionDelayTicks();
        if (!mapRestoreSession.complete()) return;

        long elapsedMillis = mapRestoreSession.elapsedMillis();
        String seconds = String.format(Locale.ROOT, "%.2f", elapsedMillis / 1000.0);
        Component complete = Component.translatable("sfgame.map_restore.complete.colored", seconds);
        forParticipants(player -> {
            player.sendSystemMessage(complete, true);
            playSound(player, SoundEvents.PLAYER_LEVELUP, 1.15F);
        });
        server.getPlayerList().broadcastSystemMessage(complete, false);
        clearMapRestoreState();
        beginModePreparationOrCountdown();
    }

    private void updateMapRestoreBar() {
        if (mapRestoreBar == null || mapRestoreSession == null) return;
        int percent = Math.round(mapRestoreSession.progress() * 100);
        mapRestoreBar.setProgress(mapRestoreSession.progress());
        mapRestoreBar.setName(Component.translatable("sfgame.map_restore.progress",
                mapRestoreSession.completedPartitions(), mapRestoreSession.totalPartitions(), percent));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState state = state(player);
            if (state.participating() || state.queued()) mapRestoreBar.addPlayer(player);
            else mapRestoreBar.removePlayer(player);
        }
    }

    private void beginModePreparationOrCountdown() {
        modePreparationStarted = false;
        anchorPreparationStarted = false;
        if (teamCaptainService.supports(data().selectedMode())) {
            phase = MatchPhase.PREPARING;
            anchorPreparationStarted = true;
            teamCaptainService.prepare(server, this, rules());
        } else if (activeRuntime.needsPreparation(data().activeMap(), rules())) {
            phase = MatchPhase.PREPARING;
            modePreparationStarted = true;
            activeRuntime.prepare(server, this, data().activeMap(), rules());
        } else {
            beginCountdown();
        }
    }

    private void clearMapRestoreState() {
        if (mapRestoreBar != null) mapRestoreBar.removeAllPlayers();
        mapRestoreBar = null;
        mapRestoreSession = null;
        mapRestoreCooldownTicks = 0;
        mapRestoreAdaptiveSkips = 0;
        mapRestorePartitionMillisEstimate = 0.0;
    }

    private void beginCountdown() {
        phase = MatchPhase.COUNTDOWN;
        phaseTicks = rules().startCountdownSeconds() * 20;
        if (phaseTicks == 0) { beginRunning(); return; }
        Component title = Component.translatable("sfgame.match.countdown", rules().startCountdownSeconds());
        forParticipants(player -> {
            sendTitle(player, title, Component.translatable("sfgame.match.starting.colored"), 24);
            playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), 1.0F);
        });
    }

    private void beginRunning() {
        phase = MatchPhase.RUNNING;
        squadService.beginRunning();
        beaconService.beginRunning(server);
        elapsedTicks = 0;
        activeRuntime = modeRuntime();
        // The match fixes its participant game type exactly once. Rule reloads,
        // GUI edits and runtime sub-phases must not keep overriding admins or
        // players every tick. Respawn deployment only restores this choice
        // after the required spectator countdown.
        matchParticipantGameType = participantGameTypeAtMatchStart(rules().mapBlockBreaking());
        activeRuntime.start(server, this, data().activeMap(), rules());
        TeamSide supplyAttacker = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                ? breakthroughRuntime.attacker()
                : GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode()) ? rules().ctfAttacker() : TeamSide.NONE;
        TeamSide supplyDefender = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                ? breakthroughRuntime.defender()
                : GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode()) ? rules().ctfDefender() : TeamSide.NONE;
        supplyService.beginRunning(data().activeMap(), data().selectedMode(), supplyAttacker, supplyDefender);
        forParticipants(player -> deploy(player, state(player), false));
        forParticipants(player -> {
            sendTitle(player, Component.translatable("sfgame.match.start.colored"),
                    Component.empty(), 30);
            playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0F);
        });
        server.getPlayerList().broadcastSystemMessage(Component.translatable("sfgame.match.started"), false);
        syncAll();
    }

    private void tickRunning() {
        elapsedTicks++;
        Map<TeamSide, Map<String, Integer>> before = supplyQuantities();
        if (supplyService.tick(elapsedTicks)) {
            notifyNewSupplyChanges(before);
            syncAll();
        }
        MatchRules rules = rules();
        ModeTickResult modeResult = activeRuntime.tick(server, this, data().activeMap(), rules);
        if (modeResult.finished() || activeRuntime.usesCommonTimeLimit() && commonTimeExpired(rules)) {
            result = modeResult.finished() ? modeResult.winner() : determineWinner();
            stop(true, Component.empty());
        }
    }

    private void tickResult() {
        if (phaseTicks > 0 && phaseTicks % 20 == 0) {
            int seconds = Math.max(1, phaseTicks / 20);
            Component returning = Component.translatable("sfgame.result.returning.colored", seconds);
            server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(returning, true));
        }
        if (--phaseTicks <= 0) finishToLobby();
    }

    private void tickPlayerTimers() {
        if (server == null) return;
        for (PlayerMatchState state : players.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerId());
            if (player == null) continue;
            if (state.protectionTicks() > 0) state.protectionTicks(state.protectionTicks() - 1);
            if (state.respawning() && phase == MatchPhase.RUNNING) {
                int ticks = state.respawnTicks();
                if (ticks > 0) {
                    state.respawnTicks(ticks - 1);
                    if (ticks % 20 == 0) {
                        Component countdown = Component.translatable("sfgame.respawn", ticks / 20);
                        player.sendSystemMessage(countdown, true);
                        playSound(player, SoundEvents.NOTE_BLOCK_HAT.get(), countdownPitch(ticks / 20));
                    }
                } else if (usesRespawnSelectionMode(data().selectedMode())) {
                    if (!state.awaitingRespawnSelection()) {
                        state.awaitingRespawnSelection(true);
                        player.sendSystemMessage(Component.translatable("sfgame.respawn.choose.colored"), true);
                        playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), 1.2F);
                        SFGameNetwork.openMenu(player);
                    }
                } else {
                    deploy(player, state, true);
                }
            }
        }
    }

    private void syncVanillaTeams() {
        if (server == null) return;
        for (PlayerMatchState state : players.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerId());
            if (player == null) continue;
            TeamSide actual = teams.sideOf(player, data());
            if (state.cachedSide() == actual) continue;
            TeamSide old = state.cachedSide();
            state.cachedSide(actual);
            squadService.remove(player);
            if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING) {
                teamCaptainService.onPlayerTeamChanged(player, this, rules());
            }
            state.clearGrantedEliteClasses();
            if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN) {
                activeRuntime.onPlayerTeamChanged(player, old, actual, this);
            }
            if (phase == MatchPhase.RUNNING && state.participating()) {
                if (data().enabledTeams().contains(actual)) {
                    deploy(player, state, false);
                } else {
                    state.participating(false);
                    state.queued(true);
                    state.respawning(false);
                    state.awaitingRespawnSelection(false);
                    state.respawnTicks(0);
                    loadoutService.clear(player);
                    player.setGameMode(GameType.SPECTATOR);
                }
            }
            sync(player);
        }
    }

    private void deploy(ServerPlayer player, PlayerMatchState state, boolean applyPendingClass) {
        deploy(player, state, applyPendingClass, null);
    }

    private void deploy(ServerPlayer player, PlayerMatchState state, boolean applyPendingClass,
                        @Nullable RespawnSourceResolver.RespawnTarget spawnOverride) {
        String modeId = data().selectedMode();
        TeamSide side = classSide(player, state);
        boolean captain = activeRuntime.isCaptain(player.getUUID());
        if (captain) ensureCaptainClass(player);
        if (applyPendingClass) {
            if (captain && classRegistry.containsCaptainForTeam(modeId, data().selectedMap(), side, state.pendingCaptainClass(modeId, side))) {
                state.currentCaptainClass(modeId, side, state.pendingCaptainClass(modeId, side));
            } else if (!captain && classRegistry.containsForTeam(modeId, data().selectedMap(), side, state.pendingClass(modeId, side))) {
                state.currentClass(modeId, side, state.pendingClass(modeId, side));
            }
        }
        Optional<ClassDefinition> definition = resolveDeploymentDefinition(
                modeId, data().selectedMap(), side, state, captain);
        RespawnSourceResolver.RespawnTarget spawn = spawnOverride != null
                ? spawnOverride : respawnSourceResolver.baseTarget(side);
        if (definition.isEmpty() || spawn == null) {
            state.participating(false);
            state.queued(true);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.respawnTicks(0);
            player.setGameMode(GameType.SPECTATOR);
            return;
        }
        if (captain && classRegistry.containsCaptainForTeam(modeId, data().selectedMap(), side, definition.get().id())) {
            state.currentCaptainClass(modeId, side, definition.get().id());
            state.pendingCaptainClass(modeId, side, definition.get().id());
        } else {
            state.currentClass(modeId, side, definition.get().id());
            state.pendingClass(modeId, side, definition.get().id());
        }
        state.respawning(false);
        state.awaitingRespawnSelection(false);
        state.respawnTicks(0);
        state.protectionTicks(rules().respawnProtectionSeconds() * 20);
        state.participating(true);
        state.queued(false);
        player.setGameMode(participantGameType());
        player.teleportTo(spawn.level(), spawn.position().x(), spawn.position().y(), spawn.position().z(),
                spawn.yaw(), spawn.pitch());
        if (!loadoutService.apply(player, definition.get())) {
            state.participating(false);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.translatable("sfgame.loadout.tacz_failed", definition.get().id()));
        }
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        if (applyPendingClass) playSound(player, SoundEvents.PLAYER_LEVELUP, 1.25F);
        sync(player);
    }

    private void activateImmediateJoin(ServerPlayer player, PlayerMatchState state) {
        state.pendingImmediateJoin(false);
        TeamSide side = classSide(player, state);
        state.currentClass(data().selectedMode(), side, state.pendingClass(data().selectedMode(), side));
        deploy(player, state, false);
    }

    private GameType participantGameType() {
        return phase == MatchPhase.RUNNING ? matchParticipantGameType : GameType.ADVENTURE;
    }

    static GameType participantGameTypeAtMatchStart(boolean mapBlockBreaking) {
        return mapBlockBreaking ? GameType.SURVIVAL : GameType.ADVENTURE;
    }

    private void finishToLobby() {
        phase = data().isArenaConfigured() && teams.bindingsValid(server, data()) ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        beaconService.clear(server);
        squadService.clear();
        teamCaptainService.clear(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState state = state(player);
            loadoutService.clear(player);
            state.participating(false);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.protectionTicks(0);
            state.pendingImmediateJoin(false);
            state.clearGrantedEliteClasses();
            state.currency(data().selectedMode(), 0);
            if (state.queued()) {
                if (teams.sideOf(player, data()) == TeamSide.NONE) teams.assign(player, teams.balancedSide(server, data()), data());
                state.queued(false);
            }
            player.setGameMode(GameType.ADVENTURE);
            if (data().lobby() != null) data().lobby().teleport(player);
        }
        resetRuntimeScores();
        syncAll();
    }

    private void announceResult() {
        Component title = resultTitle();
        Component subtitle = Component.translatable("sfgame.result.finished.colored");
        server.getPlayerList().broadcastSystemMessage(title, false);
        Component returning = Component.translatable("sfgame.result.returning.colored", rules().resultSeconds());
        server.getPlayerList().getPlayers().forEach(player -> {
            sendTitle(player, title, subtitle, rules().resultSeconds() * 20);
            player.sendSystemMessage(returning, true);
            playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
        });
    }
    private Component resultTitle() {
        if (result == TeamSide.NONE) return Component.translatable("sfgame.result.draw.colored");
        String mode = data().selectedMode();
        MatchRules currentRules = rules();
        if (GameModeRegistry.BREAKTHROUGH.equals(mode)) {
            return roleResult(result, currentRules.breakthroughAttacker(), currentRules.breakthroughDefender());
        }
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(mode) && currentRules.ctfVariant() == CtfVariant.ASSAULT) {
            return roleResult(result, currentRules.ctfAttacker(), currentRules.ctfDefender());
        }
        return Component.translatable("sfgame.result." + result.id() + ".colored");
    }
    private static Component roleResult(TeamSide winner, TeamSide attacker, TeamSide defender) {
        if (winner == attacker) return Component.translatable("sfgame.result.attacker." + attacker.id());
        if (winner == defender) return Component.translatable("sfgame.result.defender." + defender.id());
        return Component.translatable("sfgame.result." + winner.id() + ".colored");
    }
    static Component teamColored(TeamSide side, String key, Object... args) {
        return Component.translatable("sfgame.team_color." + side.id(), Component.translatable(key, args));
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle, int stayTicks) {
        sendTitle(player, title, subtitle, 0, stayTicks, 2);
    }
    private static void sendTitle(ServerPlayer player, Component title, Component subtitle,
                                  int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                Math.max(0, fadeInTicks), Math.max(2, stayTicks), Math.max(0, fadeOutTicks)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static void playSound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, 0.8F, pitch);
    }

    private static float countdownPitch(int seconds) {
        return Math.min(1.6F, 0.8F + (6 - Math.min(5, seconds)) * 0.15F);
    }

    private void resetRuntime() {
        clearMapRestoreState();
        modePreparationStarted = false;
        anchorPreparationStarted = false;
        squadService.clear();
        resetRuntimeScores();
        players.clear();
    }

    private void resetRuntimeScores() {
        phaseTicks = 0;
        elapsedTicks = 0;
        redScore = 0;
        blueScore = 0;
        yellowScore = 0;
        greenScore = 0;
        result = TeamSide.NONE;
    }

    Optional<ClassDefinition> resolveDeploymentDefinition(String modeId, String mapId, TeamSide side,
                                                          PlayerMatchState state, boolean captain) {
        String eliteId = state.grantedEliteClass(modeId, side);
        Optional<ClassDefinition> elite = classRegistry.getEliteForTeam(modeId, mapId, side, eliteId);
        if (elite.isPresent()) return elite;
        if (captain) {
            String pending = state.pendingCaptainClass(modeId, side);
            Optional<ClassDefinition> definition = classRegistry.getCaptainForTeam(modeId, mapId, side, pending);
            return definition.isPresent() ? definition
                    : classRegistry.getCaptainForTeam(modeId, mapId, side,
                    state.currentCaptainClass(modeId, side));
        }
        String pending = state.pendingClass(modeId, side);
        Optional<ClassDefinition> normal = classRegistry.getForTeam(modeId, mapId, side, pending);
        return normal.isPresent() ? normal
                : classRegistry.getForTeam(modeId, mapId, side, state.currentClass(modeId, side));
    }

    private TeamSide classSide(ServerPlayer player, PlayerMatchState state) {
        TeamSide side = teams.sideOf(player, data());
        return side == TeamSide.NONE ? state.cachedSide() : side;
    }

    private TeamSide classSide(PlayerMatchState state) {
        return state.cachedSide();
    }

    private String selectedClass(PlayerMatchState state) {
        return selectedClass(state, classSide(state));
    }

    private String selectedClass(PlayerMatchState state, TeamSide side) {
        String modeId = data().selectedMode();
        String pending = state.pendingClass(modeId, side);
        String current = state.currentClass(modeId, side);
        if (classRegistry.containsForTeam(modeId, data().selectedMap(), side, pending)) return pending;
        if (classRegistry.containsForTeam(modeId, data().selectedMap(), side, current)) return current;
        return null;
    }

    private String selectedCaptainClass(PlayerMatchState state) {
        return selectedCaptainClass(state, classSide(state));
    }

    private String selectedCaptainClass(PlayerMatchState state, TeamSide side) {
        String modeId = data().selectedMode();
        String pending = state.pendingCaptainClass(modeId, side);
        String current = state.currentCaptainClass(modeId, side);
        if (classRegistry.containsCaptainForTeam(modeId, data().selectedMap(), side, pending)) return pending;
        if (classRegistry.containsCaptainForTeam(modeId, data().selectedMap(), side, current)) return current;
        return null;
    }

    private void ensureDefaultClass(PlayerMatchState state) {
        ensureDefaultClass(state, classSide(state));
    }

    private void ensureDefaultClass(PlayerMatchState state, TeamSide side) {
        String modeId = data().selectedMode();
        if (classRegistry.containsForTeam(modeId, data().selectedMap(), side, selectedClass(state, side))) return;
        classRegistry.defaultClassForTeam(modeId, data().selectedMap(), side).ifPresent(definition -> {
            state.currentClass(modeId, side, definition.id());
            state.pendingClass(modeId, side, definition.id());
        });
    }

    void ensureCaptainClass(ServerPlayer player) {
        String modeId = data().selectedMode();
        PlayerMatchState state = state(player);
        TeamSide side = classSide(player, state);
        if (classRegistry.containsCaptainForTeam(modeId, data().selectedMap(), side, selectedCaptainClass(state, side))) return;
        classRegistry.defaultCaptainClassForTeam(modeId, data().selectedMap(), side).ifPresent(definition -> {
            state.currentCaptainClass(modeId, side, definition.id());
            state.pendingCaptainClass(modeId, side, definition.id());
        });
    }

    ServerPlayer serverPlayer(UUID playerId) { return server == null ? null : server.getPlayerList().getPlayer(playerId); }
    MinecraftServer server() { return server; }


    boolean setTeamScoreValue(TeamSide side, int value) {
        if (side == null || side == TeamSide.NONE || value < 0 || value > MAX_LIVE_SCORE) return false;
        switch (side) {
            case RED -> redScore = value;
            case BLUE -> blueScore = value;
            case YELLOW -> yellowScore = value;
            case GREEN -> greenScore = value;
            case NONE -> { return false; }
        }
        return true;
    }
    void addTeamScore(TeamSide side, int amount) {
        if (amount <= 0) return;
        switch (side) {
            case RED -> redScore += amount;
            case BLUE -> blueScore += amount;
            case YELLOW -> yellowScore += amount;
            case GREEN -> greenScore += amount;
            case NONE -> { }
        }
    }

    TeamSide determineWinner() {
        List<TeamSide> enabled = data().enabledTeams();
        int highest = enabled.stream().mapToInt(this::score).max().orElse(0);
        List<TeamSide> leaders = enabled.stream().filter(side -> score(side) == highest).toList();
        return leaders.size() == 1 ? leaders.get(0) : TeamSide.NONE;
    }

    private int participatingCount() {
        return (int) players.values().stream().filter(PlayerMatchState::participating).count();
    }

    private int countAssignedPlayers() {
        return TeamSide.PLAYABLE.stream().mapToInt(this::countSide).sum();
    }

    private int countSide(TeamSide side) {
        if (server == null) return 0;
        return (int) server.getPlayerList().getPlayers().stream().filter(p -> teams.sideOf(p, data()) == side).count();
    }

    void forParticipants(java.util.function.Consumer<ServerPlayer> consumer) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (state(player).participating()) consumer.accept(player);
        }
    }
    void announceTitleAndChat(Component title, Component subtitle, Component chat, int stayTicks) {
        announceTitleAndChatForSide(null, title, subtitle, chat, 0, stayTicks, 2);
    }
    void announceTitleAndChat(Component title, Component subtitle, Component chat,
                              int fadeInTicks, int stayTicks, int fadeOutTicks) {
        announceTitleAndChatForSide(null, title, subtitle, chat, fadeInTicks, stayTicks, fadeOutTicks);
    }
    void announceTitleAndChat(TeamSide side, Component title, Component subtitle, Component chat, int stayTicks) {
        announceTitleAndChatForSide(side, title, subtitle, chat, 0, stayTicks, 2);
    }
    void announceTitleAndChat(TeamSide side, Component title, Component subtitle, Component chat,
                              int fadeInTicks, int stayTicks, int fadeOutTicks) {
        announceTitleAndChatForSide(side, title, subtitle, chat, fadeInTicks, stayTicks, fadeOutTicks);
    }
    private void announceTitleAndChatForSide(@Nullable TeamSide target, Component title, Component subtitle,
                                             Component chat, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        for (ServerPlayer player : onlineMatchViewers()) {
            if (target != null && teams.sideOf(player, data()) != target) continue;
            sendTitle(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
            player.sendSystemMessage(chat, false);
            playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), 1.2F);
        }
    }
    void announceActionbar(Component message) {
        for (ServerPlayer player : onlineMatchViewers()) player.sendSystemMessage(message, true);
    }

    private SFGameSavedData data() {
        if (server == null) {
            server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) throw new IllegalStateException("Server is not running");
        }
        return SFGameSavedData.get(server);
    }

    public SFGameSavedData savedData() { return data(); }

    void swapBreakthroughTeamPlayers(TeamSide first, TeamSide second) {
        if (server == null || first == TeamSide.NONE || second == TeamSide.NONE || first == second) return;
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
        net.minecraft.world.scores.PlayerTeam firstTeam = scoreboard.getPlayerTeam(data().teamName(first));
        net.minecraft.world.scores.PlayerTeam secondTeam = scoreboard.getPlayerTeam(data().teamName(second));
        if (firstTeam == null || secondTeam == null) return;
        java.util.Set<String> firstPlayers = java.util.Set.copyOf(firstTeam.getPlayers());
        java.util.Set<String> secondPlayers = java.util.Set.copyOf(secondTeam.getPlayers());
        firstPlayers.forEach(name -> scoreboard.addPlayerToTeam(name, secondTeam));
        secondPlayers.forEach(name -> scoreboard.addPlayerToTeam(name, firstTeam));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getScoreboardName();
            TeamSide oldSide = firstPlayers.contains(name) ? first
                    : secondPlayers.contains(name) ? second : TeamSide.NONE;
            TeamSide newSide = swappedBreakthroughSide(oldSide, first, second);
            if (newSide == TeamSide.NONE) continue;
            PlayerMatchState state = state(player);
            state.cachedSide(newSide);
            ensureDefaultClass(state, newSide);
            player.refreshTabListName();
        }
    }

    static TeamSide swappedBreakthroughSide(TeamSide current, TeamSide first, TeamSide second) {
        if (current == first) return second;
        if (current == second) return first;
        return current;
    }

    void modeRedeployAll(int protectionTicks) {
        forParticipants(player -> {
            PlayerMatchState state = state(player);
            state.respawning(false); state.respawnTicks(0);
            state.awaitingRespawnSelection(false);
            deploy(player, state, true);
            state.protectionTicks(Math.max(state.protectionTicks(), protectionTicks));
        });
    }

    private static boolean usesRespawnSelectionMode(String modeId) {
        return GameModeRegistry.DOMINATION.equals(modeId)
                || GameModeRegistry.BREAKTHROUGH.equals(modeId)
                || GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId);
    }
    private MatchModeRuntime modeRuntime() {
        return runtimes.getOrDefault(data().selectedMode(), teamDeathmatchRuntime);
    }
    ArenaPosition runtimeSpawn(TeamSide side) {
        return side == TeamSide.NONE || data().activeMap() == null ? null
                : activeRuntime.spawnFor(side, data().activeMap());
    }

    private void sync(ServerPlayer player) {
        if (player.connection == null || player.connection.connection == null
                || player.connection.connection.channel() == null
                || !player.connection.connection.isConnected()) return;
        SFGameNetwork.sendSnapshot(player, snapshot(player));
        if (phase == MatchPhase.RUNNING) SFGameNetwork.sendSquadSnapshot(player, squadService.snapshot(player));
    }

    void syncAll() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    private boolean isActiveMatchPhase() {
        return phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN
                || phase == MatchPhase.RUNNING || phase == MatchPhase.RESULT;
    }
}

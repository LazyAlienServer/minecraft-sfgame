package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.classsystem.ClassRegistry;
import com.sfgame.classsystem.LoadoutService;
import com.sfgame.config.SFGameServerConfigPaths;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.MatchRules;
import com.sfgame.data.SFGameSavedData;
import com.sfgame.network.MatchSnapshot;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Path;

public final class MatchManager {
    private static final MatchManager INSTANCE = new MatchManager();

    private final Map<UUID, PlayerMatchState> players = new HashMap<>();
    private final ClassRegistry classRegistry = new ClassRegistry();
    private final LoadoutService loadoutService = new LoadoutService();
    private final VanillaTeamBindingService teams = new VanillaTeamBindingService();
    private final TeamDeathmatchRuntime teamDeathmatchRuntime = new TeamDeathmatchRuntime();
    private final DominationRuntime dominationRuntime = new DominationRuntime();
    private final BreakthroughRuntime breakthroughRuntime = new BreakthroughRuntime();
    private final CaptureTheFlagRuntime captureTheFlagRuntime = new CaptureTheFlagRuntime();
    private final CtfShopRegistry ctfShopRegistry = new CtfShopRegistry();
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
    private int redScore;
    private int blueScore;
    private int yellowScore;
    private int greenScore;
    private int syncTicker;
    private TeamSide result = TeamSide.NONE;

    public static MatchManager get() { return INSTANCE; }
    public MatchPhase phase() { return phase; }
    public ClassRegistry classes() { return classRegistry; }
    public LoadoutService loadouts() { return loadoutService; }
    public VanillaTeamBindingService teams() { return teams; }
    public BreakthroughRuntime breakthrough() { return breakthroughRuntime; }
    public CaptureTheFlagRuntime captureTheFlag() { return captureTheFlagRuntime; }
    public CtfShopRegistry ctfShop() { return ctfShopRegistry; }
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
    public boolean modeBlocksCombat() { return phase == MatchPhase.RUNNING && activeRuntime.blocksCombat(); }
    public boolean canBreakBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        return phase == MatchPhase.RUNNING && state(player).participating()
                && activeRuntime.canBreakBlock(player, pos, state, this);
    }
    /** Breakthrough uses Survival only while its block-breaking rule is active. */
    public boolean usesBreakthroughSurvival(ServerPlayer player) {
        return phase == MatchPhase.RUNNING && state(player).participating()
                && GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                && rules().breakthroughBlockBreaking();
    }
    public boolean canPlaceBlock(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        return phase == MatchPhase.RUNNING && state(player).participating()
                && activeRuntime.canPlaceBlock(player, pos, state, this);
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

    public boolean ctfPurchase(ServerPlayer player, String itemId) {
        if (!GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode()) || phase != MatchPhase.RUNNING
                || !state(player).participating() || state(player).respawning()) return false;
        CtfShopRegistry.ShopItem item = ctfShopRegistry.item(itemId);
        if (item == null) return false;
        ItemStack stack = item.stack();
        if (stack.isEmpty()) return false;
        PlayerMatchState playerState = state(player);
        int balance = playerState.currency(GameModeRegistry.CAPTURE_THE_FLAG);
        if (balance < item.price()) return false;
        if (player.getInventory().getFreeSlot() < 0 && player.getInventory().getSlotWithRemainingSpace(stack) < 0) return false;
        if (!player.getInventory().add(stack)) return false;
        playerState.currency(GameModeRegistry.CAPTURE_THE_FLAG, balance - item.price());
        player.sendSystemMessage(Component.translatable("sfgame.ctf.shop_bought", item.name()).withStyle(ChatFormatting.GREEN), true);
        sync(player);
        return true;
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
        SFGameSavedData data = data();
        Path configRoot = SFGameServerConfigPaths.root(server);
        classRegistry.useConfigRoot(configRoot);
        ctfShopRegistry.useConfigRoot(configRoot);
        ruleConfigRegistry.useConfigRoot(configRoot);
        teams.ensureDefaultTeams(server, data);
        List<String> errors = classRegistry.reload();
        if (!errors.isEmpty()) SFGame.LOGGER.warn("SFGame class configuration errors: {}", errors);
        List<String> shopErrors = ctfShopRegistry.reload();
        if (!shopErrors.isEmpty()) SFGame.LOGGER.warn("SFGame CTF shop configuration errors: {}", shopErrors);
        List<String> ruleErrors = ruleConfigRegistry.reload(data);
        if (!ruleErrors.isEmpty()) SFGame.LOGGER.warn("SFGame rule configuration errors: {}", ruleErrors);
        phase = data.isArenaConfigured() && teams.bindingsValid(server, data) ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        resetRuntime();
    }

    public void serverStopped() {
        activeRuntime.stop();
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
        boolean captainMode = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode()) && data.activeMap() != null
                && data.activeMap().breakthrough().variant() == BreakthroughVariant.CAPTAIN;
        List<TeamSide> enabledTeams = data.enabledTeams();
        errors.addAll(loadoutService.validate(classRegistry, data.selectedMode(), data.selectedMap(), enabledTeams, captainMode));
        if (data.activeMap() != null) errors.addAll(modeRuntime().validate(server, data.activeMap()));

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
        if (total > rules().maxPlayers()) errors.add("Player count exceeds maxPlayers");
        return errors;
    }

    public boolean start() {
        if (server == null || (phase != MatchPhase.LOBBY && phase != MatchPhase.UNCONFIGURED)) return false;
        if (!validateStart().isEmpty()) return false;
        redScore = 0;
        blueScore = 0;
        yellowScore = 0;
        greenScore = 0;
        elapsedTicks = 0;
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
        if (activeRuntime.needsPreparation(data().activeMap())) {
            phase = MatchPhase.PREPARING;
            activeRuntime.prepare(server, this, data().activeMap(), rules());
        } else beginCountdown();
        syncAll();
        return true;
    }

    public void stop(boolean showResult, Component reason) {
        if (server == null) return;
        activeRuntime.stop();
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
            state.queued(true);
            state.participating(false);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.respawnTicks(0);
            player.setGameMode(GameType.SPECTATOR);
            sync(player);
            return true;
        }
        TeamSide currentSide = teams.sideOf(player, data());
        if (!data().enabledTeams().contains(currentSide) && countAssignedPlayers() >= rules().maxPlayers()) return false;
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
                && data().activeMap().breakthrough().variant() == BreakthroughVariant.CAPTAIN
                && breakthroughRuntime.electionSeconds() > 0
                && teams.sideOf(player, data()) == breakthroughRuntime.attacker();
        if (state.participating() || attackerElectionLocked) {
            player.sendSystemMessage(Component.translatable(attackerElectionLocked
                    ? "sfgame.menu.locked.election" : "sfgame.menu.locked.participating").withStyle(ChatFormatting.RED));
            sync(player);
            return false;
        }
        return queueOrJoinLobby(player);
    }

    public boolean leaveFromMenu(ServerPlayer player) {
        if (isActiveMatchPhase()) {
            player.sendSystemMessage(Component.translatable("sfgame.menu.locked.command_leave").withStyle(ChatFormatting.RED));
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
        teams.remove(player);
        if (phase == MatchPhase.RUNNING || phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
            loadoutService.clear(player);
            player.setGameMode(GameType.SPECTATOR);
        }
        sync(player);
    }

    public boolean joinNow(ServerPlayer player) {
        if (phase != MatchPhase.RUNNING || participatingCount() >= rules().maxPlayers()) return false;
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
        state.pendingCaptainClass(modeId, side, definition.get().id());
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED || phase == MatchPhase.PREPARING) {
            state.currentCaptainClass(modeId, side, definition.get().id());
        }
        sync(player);
        return true;
    }

    public boolean selectRespawn(ServerPlayer player, String optionId) {
        PlayerMatchState state = state(player);
        if (phase != MatchPhase.RUNNING || !GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                || !state.participating() || !state.respawning() || !state.awaitingRespawnSelection()
                || data().activeMap() == null) return false;
        ArenaPosition position = breakthroughRuntime.respawnPosition(player, optionId, this, data().activeMap());
        if (position == null) {
            player.sendSystemMessage(Component.translatable("sfgame.respawn.option_unavailable").withStyle(ChatFormatting.RED));
            SFGameNetwork.openMenu(player);
            return false;
        }
        deploy(player, state, true, position);
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
        activeRuntime.onPlayerLoggedOut(player, this);
    }

    public void handleDeath(ServerPlayer victim, DamageSource source) {
        if (phase != MatchPhase.RUNNING) return;
        PlayerMatchState victimState = state(victim);
        if (!victimState.participating()) return;
        victimState.addDeath();
        TeamSide victimSide = teams.sideOf(victim, data());
        activeRuntime.onPlayerDeath(victim, victimSide, this);
        Player attacker = source.getEntity() instanceof Player player ? player : source.getDirectEntity() instanceof Player player ? player : null;
        if (attacker instanceof ServerPlayer serverAttacker && serverAttacker != victim) {
            PlayerMatchState attackerState = state(serverAttacker);
            TeamSide attackerSide = teams.sideOf(serverAttacker, data());
            if (attackerState.participating() && attackerSide != TeamSide.NONE && attackerSide != victimSide) {
                attackerState.addKill();
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
        boolean domination = GameModeRegistry.DOMINATION.equals(data().selectedMode());
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode());
        boolean ctf = GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode());
        if (!domination && !breakthrough && !ctf && key.startsWith("capture")) {
            throw new IllegalArgumentException(key + " is only available in a capture mode");
        }
        if (!domination && (key.equals("scoreIntervalSeconds") || key.equals("scorePerPoint") || key.equals("syncHoldSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in domination mode");
        }
        if (!breakthrough && !ctf && (key.equals("attackerTickets") || key.equals("sectorTransitionSeconds")
                || key.equals("captainVoteSeconds") || key.equals("captainReplacementVoteSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in breakthrough mode");
        }
        if (ctf && (key.equals("sectorTransitionSeconds") || key.equals("captainVoteSeconds") || key.equals("captainReplacementVoteSeconds"))) {
            throw new IllegalArgumentException(key + " is not available in CTF mode");
        }
        if (!ctf && (key.equals("ctfFlagReturnSeconds") || key.equals("ctfHomeCaptureTimeSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in CTF mode");
        }
        ruleConfigRegistry.setInt(data().selectedMode(), data().selectedMap(), key, value);
        activeRuntime.onRuleChanged(key, rules());
        syncAll();
    }

    public void setRule(String key, boolean value) {
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
            case "breakthroughBlockBreaking" -> {
                if (!breakthrough) throw new IllegalArgumentException(key + " is only available in breakthrough mode");
            }
            default -> throw new IllegalArgumentException("Unknown boolean rule " + key);
        }
        ruleConfigRegistry.setBoolean(data().selectedMode(), data().selectedMap(), key, value);
        activeRuntime.onRuleChanged(key, rules());
        if ("breakthroughBlockBreaking".equals(key)) refreshParticipantGameModes();
        syncAll();
    }

    public void setRule(String key, double value) {
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
        activeRuntime.onRuleChanged(key, rules());
        syncAll();
    }

    public void resetRules() {
        ruleConfigRegistry.resetMap(data().selectedMode(), data().selectedMap());
        activeRuntime.onRuleChanged("attackerTickets", rules());
        refreshParticipantGameModes();
        syncAll();
    }

    public void setRuleParent(String parent) {
        ruleConfigRegistry.setParent(data().selectedMode(), data().selectedMap(), parent);
        activeRuntime.onRuleChanged("attackerTickets", rules());
        refreshParticipantGameModes();
        syncAll();
    }

    public List<String> reloadRuleConfigurations() {
        List<String> errors = ruleConfigRegistry.reload(data());
        if (errors.isEmpty()) {
            activeRuntime.onRuleChanged("attackerTickets", rules());
            refreshParticipantGameModes();
            arenaSelectionChanged();
            syncAll();
        }
        return errors;
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
                .map(c -> new MatchSnapshot.ClassView(c.id(), c.displayName(), c.description(), c.icon(), c.gunId(),
                        c.maxHealth(), c.movementSpeedMultiplier(), c.reserveAmmo()))
                .toList();
        List<MatchSnapshot.ClassView> captainClassViews = visibleCaptainClasses.stream()
                .map(c -> new MatchSnapshot.ClassView(c.id(), c.displayName(), c.description(), c.icon(), c.gunId(),
                        c.maxHealth(), c.movementSpeedMultiplier(), c.reserveAmmo())).toList();
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode()) && data().activeMap() != null;
        TeamSide attackSide = breakthrough && (phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RUNNING)
                ? breakthroughRuntime.attacker() : breakthrough ? data().activeMap().breakthrough().attacker() : TeamSide.NONE;
        TeamSide defenseSide = breakthrough && (phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RUNNING)
                ? breakthroughRuntime.defender() : breakthrough ? data().activeMap().breakthrough().defender() : TeamSide.NONE;
        UUID captainId = breakthroughRuntime.captain();
        ServerPlayer captainPlayer = captainId == null || server == null ? null : server.getPlayerList().getPlayer(captainId);
        List<MatchSnapshot.CaptainCandidate> candidates = !breakthrough || breakthroughRuntime.electionSeconds() <= 0 || side != attackSide
                ? List.of() : server.getPlayerList().getPlayers().stream()
                .filter(player -> state(player).participating() && teams.sideOf(player, data()) == attackSide)
                .map(player -> new MatchSnapshot.CaptainCandidate(player.getUUID().toString(), player.getGameProfile().getName())).toList();
        List<MatchSnapshot.RespawnOption> respawnOptions = breakthrough && state.awaitingRespawnSelection()
                ? breakthroughRuntime.respawnOptions(viewer, this, data().activeMap()) : List.of();
        boolean ctf = GameModeRegistry.CAPTURE_THE_FLAG.equals(data().selectedMode()) && data().activeMap() != null;
        String ctfVariant = ctf ? data().activeMap().captureTheFlag().variant().id() : null;
        String ctfRestriction = ctf ? data().activeMap().captureTheFlag().carrierRestriction().id() : null;
        List<MatchSnapshot.CtfFlagView> ctfFlags = ctf
                ? captureTheFlagRuntime.flagViews(this).stream()
                .map(flag -> new MatchSnapshot.CtfFlagView(flag.id(), flag.owner(), flag.state(), flag.carrier(), flag.unlocked(), flag.depotTeam()))
                .toList() : List.of();
        // Currency and shop data only belong to an active CTF round.  Do not
        // expose them while the player is in the lobby, queue, countdown or
        // result screen; those states do not use the economy system.
        boolean ctfEconomy = ctf && phase == MatchPhase.RUNNING && !ctfShopRegistry.items().isEmpty();
        List<MatchSnapshot.ShopView> ctfShopItems = ctfEconomy ? ctfShopRegistry.items().stream()
                .map(item -> new MatchSnapshot.ShopView(item.id(), item.name(), item.icon(), item.price())).toList() : List.of();
        return new MatchSnapshot(data().selectedMode(), phase, side, redScore, blueScore, yellowScore, greenScore,
                rules.scoreLimit(), remaining, countSide(TeamSide.RED), countSide(TeamSide.BLUE),
                countSide(TeamSide.YELLOW), countSide(TeamSide.GREEN), state.currentClass(data().selectedMode(), classSide), state.pendingClass(data().selectedMode(), classSide),
                state.participating(), state.queued(), classViews,
                breakthrough ? data().activeMap().breakthrough().variant().name().toLowerCase(java.util.Locale.ROOT) : "",
                attackSide, defenseSide, breakthrough ? breakthroughRuntime.tickets() : 0,
                breakthrough ? breakthroughRuntime.leg() : 0, breakthrough ? breakthroughRuntime.sectorNumber() : 0,
                breakthrough ? breakthroughRuntime.sectorCount(data().activeMap()) : 0,
                breakthrough ? breakthroughRuntime.subState() : "", captainId == null ? null : captainId.toString(),
                captainPlayer == null ? null : captainPlayer.getGameProfile().getName(),
                breakthrough ? breakthroughRuntime.electionSeconds() : 0, breakthroughRuntime.isCaptain(viewer.getUUID()),
                state.currentCaptainClass(data().selectedMode(), classSide), state.pendingCaptainClass(data().selectedMode(), classSide),
                captainClassViews, candidates, state.awaitingRespawnSelection(), respawnOptions,
                ctfVariant, ctfRestriction, ctfEconomy ? state.currency(data().selectedMode()) : 0, ctfFlags, ctfShopItems);
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

    void addCurrency(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;
        state(player).addCurrency(data().selectedMode(), amount);
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
            Component title = Component.literal(Integer.toString(phaseTicks / 20)).withStyle(ChatFormatting.GOLD);
            forParticipants(player -> {
                sendTitle(player, title, Component.translatable("sfgame.match.starting"), 24);
                playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), countdownPitch(phaseTicks / 20));
            });
        }
        if (phaseTicks <= 0) beginRunning();
    }

    private void tickPreparing() {
        if (activeRuntime.tickPreparation(server, this, data().activeMap(), rules())) beginCountdown();
    }

    private void beginCountdown() {
        phase = MatchPhase.COUNTDOWN;
        phaseTicks = rules().startCountdownSeconds() * 20;
        if (phaseTicks == 0) { beginRunning(); return; }
        Component title = Component.literal(Integer.toString(rules().startCountdownSeconds())).withStyle(ChatFormatting.GOLD);
        forParticipants(player -> {
            sendTitle(player, title, Component.translatable("sfgame.match.starting"), 24);
            playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), 1.0F);
        });
    }

    private void beginRunning() {
        phase = MatchPhase.RUNNING;
        elapsedTicks = 0;
        activeRuntime = modeRuntime();
        activeRuntime.start(server, this, data().activeMap(), rules());
        forParticipants(player -> deploy(player, state(player), false));
        forParticipants(player -> {
            sendTitle(player, Component.translatable("sfgame.match.start").withStyle(ChatFormatting.GREEN),
                    Component.empty(), 30);
            playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0F);
        });
        server.getPlayerList().broadcastSystemMessage(Component.literal("SFGame match started").withStyle(ChatFormatting.GREEN), false);
        syncAll();
    }

    private void tickRunning() {
        elapsedTicks++;
        MatchRules rules = rules();
        ModeTickResult modeResult = activeRuntime.tick(server, this, data().activeMap(), rules);
        if (modeResult.finished() || activeRuntime.usesCommonTimeLimit() && elapsedTicks >= rules.timeLimitSeconds() * 20) {
            result = modeResult.finished() ? modeResult.winner() : determineWinner();
            stop(true, Component.empty());
        }
    }

    private void tickResult() {
        if (phaseTicks > 0 && phaseTicks % 20 == 0) {
            int seconds = Math.max(1, phaseTicks / 20);
            Component returning = Component.translatable("sfgame.result.returning", seconds).withStyle(ChatFormatting.YELLOW);
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
                } else if (GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())) {
                    if (!state.awaitingRespawnSelection()) {
                        state.awaitingRespawnSelection(true);
                        player.sendSystemMessage(Component.translatable("sfgame.respawn.choose").withStyle(ChatFormatting.YELLOW), true);
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
                        @Nullable ArenaPosition spawnOverride) {
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
        String classId = captain ? selectedCaptainClass(state, side) : selectedClass(state, side);
        Optional<ClassDefinition> definition = captain
                ? classRegistry.getCaptainForTeam(modeId, data().selectedMap(), side, classId)
                : classRegistry.getForTeam(modeId, data().selectedMap(), side, classId);
        ArenaPosition spawn = spawnOverride != null ? spawnOverride
                : side == TeamSide.NONE ? null : activeRuntime.spawnFor(side, data().activeMap());
        if (definition.isEmpty() || spawn == null) {
            state.participating(false);
            state.queued(true);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.respawnTicks(0);
            player.setGameMode(GameType.SPECTATOR);
            return;
        }
        if (captain) {
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
        spawn.teleport(player);
        if (!loadoutService.apply(player, definition.get())) {
            state.participating(false);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("Could not build TACZ loadout for " + definition.get().id()).withStyle(ChatFormatting.RED));
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
        return phase == MatchPhase.RUNNING && GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())
                && rules().breakthroughBlockBreaking() ? GameType.SURVIVAL : GameType.ADVENTURE;
    }

    private void refreshParticipantGameModes() {
        if (server == null) return;
        GameType gameType = participantGameType();
        forParticipants(player -> player.setGameMode(gameType));
    }

    private void finishToLobby() {
        phase = data().isArenaConfigured() && teams.bindingsValid(server, data()) ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState state = state(player);
            loadoutService.clear(player);
            state.participating(false);
            state.respawning(false);
            state.awaitingRespawnSelection(false);
            state.protectionTicks(0);
            state.pendingImmediateJoin(false);
            state.currency(GameModeRegistry.CAPTURE_THE_FLAG, 0);
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
        Component title = result == TeamSide.NONE ? Component.translatable("sfgame.result.draw").withStyle(ChatFormatting.GOLD)
                : Component.translatable("sfgame.result." + result.id()).withStyle(result.color());
        server.getPlayerList().broadcastSystemMessage(title, false);
        Component returning = Component.translatable("sfgame.result.returning", rules().resultSeconds())
                .withStyle(ChatFormatting.YELLOW);
        server.getPlayerList().getPlayers().forEach(player -> {
            sendTitle(player, title, Component.empty(), rules().resultSeconds() * 20);
            player.sendSystemMessage(returning, true);
            playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
        });
    }

    private static void sendTitle(ServerPlayer player, Component title, Component subtitle, int stayTicks) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, Math.max(2, stayTicks), 2));
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

    private SFGameSavedData data() {
        if (server == null) {
            server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) throw new IllegalStateException("Server is not running");
        }
        return SFGameSavedData.get(server);
    }

    SFGameSavedData savedData() { return data(); }

    void modeRedeployAll(int protectionTicks) {
        forParticipants(player -> {
            PlayerMatchState state = state(player);
            state.respawning(false); state.respawnTicks(0);
            state.awaitingRespawnSelection(false);
            deploy(player, state, true);
            state.protectionTicks(Math.max(state.protectionTicks(), protectionTicks));
        });
    }

    private MatchModeRuntime modeRuntime() {
        return runtimes.getOrDefault(data().selectedMode(), teamDeathmatchRuntime);
    }

    private void sync(ServerPlayer player) {
        SFGameNetwork.sendSnapshot(player, snapshot(player));
    }

    private void syncAll() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    private boolean isActiveMatchPhase() {
        return phase == MatchPhase.PREPARING || phase == MatchPhase.COUNTDOWN
                || phase == MatchPhase.RUNNING || phase == MatchPhase.RESULT;
    }
}

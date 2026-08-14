package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.classsystem.ClassRegistry;
import com.sfgame.classsystem.LoadoutService;
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

public final class MatchManager {
    private static final MatchManager INSTANCE = new MatchManager();

    private final Map<UUID, PlayerMatchState> players = new HashMap<>();
    private final ClassRegistry classRegistry = new ClassRegistry();
    private final LoadoutService loadoutService = new LoadoutService();
    private final VanillaTeamBindingService teams = new VanillaTeamBindingService();
    private final TeamDeathmatchRuntime teamDeathmatchRuntime = new TeamDeathmatchRuntime();
    private final DominationRuntime dominationRuntime = new DominationRuntime();
    private final BreakthroughRuntime breakthroughRuntime = new BreakthroughRuntime();
    private final Map<String, MatchModeRuntime> runtimes = Map.of(
            GameModeRegistry.TEAM_DEATHMATCH, teamDeathmatchRuntime,
            GameModeRegistry.DOMINATION, dominationRuntime,
            GameModeRegistry.BREAKTHROUGH, breakthroughRuntime);
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
    public int remainingSeconds() { return activeRuntime.remainingSeconds(this, data().rules()); }
    public boolean modeBlocksCombat() { return phase == MatchPhase.RUNNING && activeRuntime.blocksCombat(); }

    public boolean canChangeArena() {
        return phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED;
    }

    public void arenaSelectionChanged() {
        if (server == null || !canChangeArena()) return;
        phase = data().isArenaConfigured() && teams.bindingsValid(server, data())
                ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        syncAll();
    }

    public void serverStarted(MinecraftServer server) {
        this.server = server;
        SFGameSavedData data = data();
        teams.ensureDefaultTeams(server, data);
        List<String> errors = classRegistry.reload();
        if (!errors.isEmpty()) SFGame.LOGGER.warn("SFGame class configuration errors: {}", errors);
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
        if (!data.isArenaConfigured()) errors.add("Lobby and spawn points for at least two teams must be set");
        if (!teams.bindingsValid(server, data)) errors.add("All four SFGame sides must bind different existing vanilla teams");
        boolean captainMode = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode()) && data.activeMap() != null
                && data.activeMap().breakthrough().variant() == BreakthroughVariant.CAPTAIN;
        errors.addAll(loadoutService.validate(classRegistry, data.selectedMode(), captainMode));
        if (data.activeMap() != null) errors.addAll(modeRuntime().validate(server, data.activeMap()));

        List<TeamSide> enabledTeams = data.enabledTeams();
        if (enabledTeams.size() < 2) errors.add("The selected map needs spawn points for at least two teams");
        int total = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeamSide side = teams.sideOf(player, data);
            if (!enabledTeams.contains(side)) continue;
            total++;
            PlayerMatchState state = state(player);
            ensureDefaultClass(state);
            String selected = selectedClass(state);
            if (!classRegistry.contains(data.selectedMode(), selected)) errors.add(player.getGameProfile().getName() + " has no valid class");
        }
        for (TeamSide side : enabledTeams) {
            if (countSide(side) == 0) errors.add(side.id() + " team needs at least one player");
        }
        if (total > data.rules().maxPlayers()) errors.add("Player count exceeds maxPlayers");
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
                if (state.currentClass(modeId) == null) state.currentClass(modeId, state.pendingClass(modeId));
                player.setGameMode(GameType.ADVENTURE);
            } else {
                state.participating(false);
                player.setGameMode(GameType.SPECTATOR);
            }
        }
        activeRuntime = modeRuntime();
        if (activeRuntime.needsPreparation(data().activeMap())) {
            phase = MatchPhase.PREPARING;
            activeRuntime.prepare(server, this, data().activeMap(), data().rules());
        } else beginCountdown();
        syncAll();
        return true;
    }

    public void stop(boolean showResult, Component reason) {
        if (server == null) return;
        activeRuntime.stop();
        if (showResult) {
            phase = MatchPhase.RESULT;
            phaseTicks = data().rules().resultSeconds() * 20;
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
        if (!data().enabledTeams().contains(currentSide) && countAssignedPlayers() >= data().rules().maxPlayers()) return false;
        if (!data().enabledTeams().contains(currentSide)) teams.assign(player, teams.balancedSide(server, data()), data());
        if (!data().enabledTeams().contains(teams.sideOf(player, data()))) return false;
        ensureDefaultClass(state);
        state.queued(false);
        state.participating(false);
        state.pendingImmediateJoin(false);
        state.respawning(false);
        state.awaitingRespawnSelection(false);
        state.respawnTicks(0);
        state.respawnTicks(0);
        state.protectionTicks(0);
        state.cachedSide(teams.sideOf(player, data()));
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
        if (phase != MatchPhase.RUNNING || participatingCount() >= data().rules().maxPlayers()) return false;
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
        if (classRegistry.contains(data().selectedMode(), selectedClass(state))) {
            activateImmediateJoin(player, state);
        } else {
            SFGameNetwork.openMenu(player);
        }
        sync(player);
        return true;
    }

    public boolean selectClass(ServerPlayer player, String classId) {
        String modeId = data().selectedMode();
        Optional<ClassDefinition> definition = classRegistry.get(modeId, classId);
        if (definition.isEmpty()) return false;
        PlayerMatchState state = state(player);
        state.pendingClass(modeId, definition.get().id());
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED) state.currentClass(modeId, definition.get().id());
        if (state.pendingImmediateJoin() && phase == MatchPhase.RUNNING) activateImmediateJoin(player, state);
        sync(player);
        return true;
    }

    public boolean selectCaptainClass(ServerPlayer player, String classId) {
        String modeId = data().selectedMode();
        Optional<ClassDefinition> definition = classRegistry.getCaptain(modeId, classId);
        if (definition.isEmpty()) return false;
        PlayerMatchState state = state(player);
        state.pendingCaptainClass(modeId, definition.get().id());
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED || phase == MatchPhase.PREPARING) {
            state.currentCaptainClass(modeId, definition.get().id());
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
    }

    public void handleDeath(ServerPlayer victim, DamageSource source) {
        if (phase != MatchPhase.RUNNING) return;
        PlayerMatchState victimState = state(victim);
        if (!victimState.participating()) return;
        victimState.addDeath();
        TeamSide victimSide = teams.sideOf(victim, data());
        activeRuntime.onPlayerDeath(victimSide, this);
        Player attacker = source.getEntity() instanceof Player player ? player : source.getDirectEntity() instanceof Player player ? player : null;
        if (attacker instanceof ServerPlayer serverAttacker && serverAttacker != victim) {
            PlayerMatchState attackerState = state(serverAttacker);
            TeamSide attackerSide = teams.sideOf(serverAttacker, data());
            if (attackerState.participating() && attackerSide != TeamSide.NONE && attackerSide != victimSide) {
                attackerState.addKill();
                activeRuntime.onKill(attackerSide, this);
            }
        }
        victimState.respawning(true);
        victimState.awaitingRespawnSelection(false);
        victimState.respawnTicks(data().rules().respawnSeconds() * 20);
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
        boolean captain = activeRuntime.isCaptain(player.getUUID());
        return (captain ? classRegistry.getCaptain(modeId, state.currentCaptainClass(modeId))
                : classRegistry.get(modeId, state.currentClass(modeId))).map(ClassDefinition::allowDrop).orElse(false);
    }

    public void setRule(String key, int value) {
        MatchRules rules = data().rules();
        boolean domination = GameModeRegistry.DOMINATION.equals(data().selectedMode());
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode());
        if (!domination && !breakthrough && key.startsWith("capture")) {
            throw new IllegalArgumentException(key + " is only available in a capture mode");
        }
        if (!domination && (key.equals("scoreIntervalSeconds") || key.equals("scorePerPoint") || key.equals("syncHoldSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in domination mode");
        }
        if (!breakthrough && (key.equals("attackerTickets") || key.equals("sectorTransitionSeconds")
                || key.equals("captainVoteSeconds") || key.equals("captainReplacementVoteSeconds"))) {
            throw new IllegalArgumentException(key + " is only available in breakthrough mode");
        }
        switch (key) {
            case "maxPlayers" -> rules.maxPlayers(value);
            case "scoreLimit" -> rules.scoreLimit(value);
            case "timeLimitSeconds" -> rules.timeLimitSeconds(value);
            case "startCountdownSeconds" -> rules.startCountdownSeconds(value);
            case "respawnSeconds" -> rules.respawnSeconds(value);
            case "respawnProtectionSeconds" -> rules.respawnProtectionSeconds(value);
            case "resultSeconds" -> rules.resultSeconds(value);
            case "captureTimeSeconds" -> rules.captureTimeSeconds(value);
            case "captureMaxMultiplier" -> rules.captureMaxMultiplier(value);
            case "scoreIntervalSeconds" -> rules.scoreIntervalSeconds(value);
            case "scorePerPoint" -> rules.scorePerPoint(value);
            case "syncHoldSeconds" -> rules.syncHoldSeconds(value);
            case "attackerTickets" -> rules.attackerTickets(value);
            case "sectorTransitionSeconds" -> rules.sectorTransitionSeconds(value);
            case "captainVoteSeconds" -> rules.captainVoteSeconds(value);
            case "captainReplacementVoteSeconds" -> rules.captainReplacementVoteSeconds(value);
            default -> throw new IllegalArgumentException("Unknown rule " + key);
        }
        data().setDirty();
        activeRuntime.onRuleChanged(key, rules);
        syncAll();
    }

    public void setRule(String key, boolean value) {
        boolean domination = GameModeRegistry.DOMINATION.equals(data().selectedMode());
        boolean breakthrough = GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode());
        switch (key) {
            case "captureUsePlayerDifference" -> {
                if (!domination && !breakthrough) throw new IllegalArgumentException(key + " is only available in a capture mode");
                data().rules().captureUsePlayerDifference(value);
            }
            case "attackerCaptainGlowing" -> {
                if (!breakthrough) throw new IllegalArgumentException(key + " is only available in breakthrough mode");
                data().rules().attackerCaptainGlowing(value);
            }
            default -> throw new IllegalArgumentException("Unknown boolean rule " + key);
        }
        data().setDirty();
        activeRuntime.onRuleChanged(key, data().rules()); syncAll();
    }

    public void setRule(String key, double value) {
        if (!GameModeRegistry.DOMINATION.equals(data().selectedMode()) && !GameModeRegistry.BREAKTHROUGH.equals(data().selectedMode())) throw new IllegalArgumentException(key + " is only available in a capture mode");
        switch (key) {
            case "captureDifferenceCoefficient" -> data().rules().captureDifferenceCoefficient(value);
            case "attackerCaptainCaptureWeight" -> data().rules().attackerCaptainCaptureWeight(value);
            case "defenderCaptureWeight" -> data().rules().defenderCaptureWeight(value);
            default -> throw new IllegalArgumentException("Unknown decimal rule " + key);
        }
        data().setDirty(); activeRuntime.onRuleChanged(key, data().rules()); syncAll();
    }

    public void resetRules() {
        data().rules().reset();
        data().setDirty();
        activeRuntime.onRuleChanged("attackerTickets", data().rules());
        syncAll();
    }

    public MatchSnapshot snapshot(ServerPlayer viewer) {
        MatchRules rules = data().rules();
        PlayerMatchState state = state(viewer);
        TeamSide side = teams.sideOf(viewer, data());
        int remaining = activeRuntime.remainingSeconds(this, rules);
        List<MatchSnapshot.ClassView> classViews = classRegistry.all(data().selectedMode()).stream()
                .map(c -> new MatchSnapshot.ClassView(c.id(), c.displayName(), c.description(), c.icon(), c.gunId(),
                        c.maxHealth(), c.movementSpeedMultiplier(), c.reserveAmmo()))
                .toList();
        List<MatchSnapshot.ClassView> captainClassViews = classRegistry.captainClasses(data().selectedMode()).stream()
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
        return new MatchSnapshot(data().selectedMode(), phase, side, redScore, blueScore, yellowScore, greenScore,
                rules.scoreLimit(), remaining, countSide(TeamSide.RED), countSide(TeamSide.BLUE),
                countSide(TeamSide.YELLOW), countSide(TeamSide.GREEN), state.currentClass(data().selectedMode()), state.pendingClass(data().selectedMode()),
                state.participating(), state.queued(), classViews,
                breakthrough ? data().activeMap().breakthrough().variant().name().toLowerCase(java.util.Locale.ROOT) : "",
                attackSide, defenseSide, breakthrough ? breakthroughRuntime.tickets() : 0,
                breakthrough ? breakthroughRuntime.leg() : 0, breakthrough ? breakthroughRuntime.sectorNumber() : 0,
                breakthrough ? breakthroughRuntime.sectorCount(data().activeMap()) : 0,
                breakthrough ? breakthroughRuntime.subState() : "", captainId == null ? null : captainId.toString(),
                captainPlayer == null ? null : captainPlayer.getGameProfile().getName(),
                breakthrough ? breakthroughRuntime.electionSeconds() : 0, breakthroughRuntime.isCaptain(viewer.getUUID()),
                state.currentCaptainClass(data().selectedMode()), state.pendingCaptainClass(data().selectedMode()),
                captainClassViews, candidates, state.awaitingRespawnSelection(), respawnOptions);
    }

    public PlayerMatchState state(ServerPlayer player) {
        return players.computeIfAbsent(player.getUUID(), PlayerMatchState::new);
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
        if (activeRuntime.tickPreparation(server, this, data().activeMap(), data().rules())) beginCountdown();
    }

    private void beginCountdown() {
        phase = MatchPhase.COUNTDOWN;
        phaseTicks = data().rules().startCountdownSeconds() * 20;
        if (phaseTicks == 0) { beginRunning(); return; }
        Component title = Component.literal(Integer.toString(data().rules().startCountdownSeconds())).withStyle(ChatFormatting.GOLD);
        forParticipants(player -> {
            sendTitle(player, title, Component.translatable("sfgame.match.starting"), 24);
            playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), 1.0F);
        });
    }

    private void beginRunning() {
        phase = MatchPhase.RUNNING;
        elapsedTicks = 0;
        activeRuntime = modeRuntime();
        activeRuntime.start(server, this, data().activeMap(), data().rules());
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
        MatchRules rules = data().rules();
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
        boolean captain = activeRuntime.isCaptain(player.getUUID());
        if (captain) ensureCaptainClass(player);
        if (applyPendingClass) {
            if (captain && classRegistry.containsCaptain(modeId, state.pendingCaptainClass(modeId))) {
                state.currentCaptainClass(modeId, state.pendingCaptainClass(modeId));
            } else if (!captain && classRegistry.contains(modeId, state.pendingClass(modeId))) {
                state.currentClass(modeId, state.pendingClass(modeId));
            }
        }
        String classId = captain ? selectedCaptainClass(state) : selectedClass(state);
        Optional<ClassDefinition> definition = captain ? classRegistry.getCaptain(modeId, classId) : classRegistry.get(modeId, classId);
        TeamSide side = teams.sideOf(player, data());
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
            state.currentCaptainClass(modeId, definition.get().id());
            state.pendingCaptainClass(modeId, definition.get().id());
        } else {
            state.currentClass(modeId, definition.get().id());
            state.pendingClass(modeId, definition.get().id());
        }
        state.respawning(false);
        state.awaitingRespawnSelection(false);
        state.respawnTicks(0);
        state.protectionTicks(data().rules().respawnProtectionSeconds() * 20);
        state.participating(true);
        state.queued(false);
        player.setGameMode(GameType.ADVENTURE);
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
        state.currentClass(data().selectedMode(), state.pendingClass(data().selectedMode()));
        deploy(player, state, false);
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
        Component returning = Component.translatable("sfgame.result.returning", data().rules().resultSeconds())
                .withStyle(ChatFormatting.YELLOW);
        server.getPlayerList().getPlayers().forEach(player -> {
            sendTitle(player, title, Component.empty(), data().rules().resultSeconds() * 20);
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

    private String selectedClass(PlayerMatchState state) {
        String modeId = data().selectedMode();
        if (classRegistry.contains(modeId, state.pendingClass(modeId))) return state.pendingClass(modeId);
        if (classRegistry.contains(modeId, state.currentClass(modeId))) return state.currentClass(modeId);
        return null;
    }

    private String selectedCaptainClass(PlayerMatchState state) {
        String modeId = data().selectedMode();
        if (classRegistry.containsCaptain(modeId, state.pendingCaptainClass(modeId))) return state.pendingCaptainClass(modeId);
        if (classRegistry.containsCaptain(modeId, state.currentCaptainClass(modeId))) return state.currentCaptainClass(modeId);
        return null;
    }

    private void ensureDefaultClass(PlayerMatchState state) {
        String modeId = data().selectedMode();
        if (classRegistry.contains(modeId, selectedClass(state))) return;
        classRegistry.defaultClass(modeId).ifPresent(definition -> {
            state.currentClass(modeId, definition.id());
            state.pendingClass(modeId, definition.id());
        });
    }

    void ensureCaptainClass(ServerPlayer player) {
        String modeId = data().selectedMode();
        PlayerMatchState state = state(player);
        if (classRegistry.containsCaptain(modeId, selectedCaptainClass(state))) return;
        classRegistry.defaultCaptainClass(modeId).ifPresent(definition -> {
            state.currentCaptainClass(modeId, definition.id());
            state.pendingCaptainClass(modeId, definition.id());
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

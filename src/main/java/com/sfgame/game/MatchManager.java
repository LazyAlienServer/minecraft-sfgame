package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.classsystem.ClassRegistry;
import com.sfgame.classsystem.LoadoutService;
import com.sfgame.data.ArenaPosition;
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
        players.clear();
        server = null;
        phase = MatchPhase.UNCONFIGURED;
    }

    public void tick() {
        if (server == null) return;
        SFGameSavedData data = data();
        if (!teams.bindingsValid(server, data)) {
            if (phase == MatchPhase.RUNNING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
                stop(false, Component.literal("Bound vanilla team was removed"));
            }
            phase = MatchPhase.UNCONFIGURED;
            return;
        }
        if (phase == MatchPhase.UNCONFIGURED && data.isArenaConfigured()) phase = MatchPhase.LOBBY;
        if (phase == MatchPhase.LOBBY && !data.isArenaConfigured()) phase = MatchPhase.UNCONFIGURED;

        syncVanillaTeams();
        tickPlayerTimers();
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
        errors.addAll(loadoutService.validate(classRegistry));

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
            if (!classRegistry.contains(selected)) errors.add(player.getGameProfile().getName() + " has no valid class");
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
                if (state.currentClass() == null) state.currentClass(state.pendingClass());
                player.setGameMode(GameType.ADVENTURE);
            } else {
                state.participating(false);
                player.setGameMode(GameType.SPECTATOR);
            }
        }
        phase = MatchPhase.COUNTDOWN;
        phaseTicks = data().rules().startCountdownSeconds() * 20;
        if (phaseTicks == 0) {
            beginRunning();
        } else {
            Component title = Component.literal(Integer.toString(data().rules().startCountdownSeconds())).withStyle(ChatFormatting.GOLD);
            forParticipants(player -> {
                sendTitle(player, title, Component.translatable("sfgame.match.starting"), 24);
                playSound(player, SoundEvents.NOTE_BLOCK_PLING.get(), 1.0F);
            });
        }
        syncAll();
        return true;
    }

    public void stop(boolean showResult, Component reason) {
        if (server == null) return;
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
        if (phase == MatchPhase.RUNNING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
            state.queued(true);
            state.participating(false);
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
        state.respawnTicks(0);
        state.protectionTicks(0);
        state.cachedSide(teams.sideOf(player, data()));
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        sync(player);
        return true;
    }

    public void leave(ServerPlayer player) {
        PlayerMatchState state = state(player);
        state.participating(false);
        state.queued(false);
        state.pendingImmediateJoin(false);
        state.respawning(false);
        teams.remove(player);
        if (phase == MatchPhase.RUNNING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
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
        state.resetRoundStats();
        player.setGameMode(GameType.SPECTATOR);
        if (classRegistry.contains(selectedClass(state))) {
            activateImmediateJoin(player, state);
        } else {
            SFGameNetwork.openMenu(player);
        }
        sync(player);
        return true;
    }

    public boolean selectClass(ServerPlayer player, String classId) {
        Optional<ClassDefinition> definition = classRegistry.get(classId);
        if (definition.isEmpty()) return false;
        PlayerMatchState state = state(player);
        state.pendingClass(definition.get().id());
        if (phase == MatchPhase.LOBBY || phase == MatchPhase.UNCONFIGURED) state.currentClass(definition.get().id());
        if (state.pendingImmediateJoin() && phase == MatchPhase.RUNNING) activateImmediateJoin(player, state);
        sync(player);
        return true;
    }

    public void playerLoggedIn(ServerPlayer player) {
        PlayerMatchState state = state(player);
        state.connected(true);
        TeamSide side = teams.sideOf(player, data());
        state.cachedSide(side);
        if (phase == MatchPhase.RUNNING && state.participating() && side != TeamSide.NONE) {
            deploy(player, state, false);
        } else if (phase == MatchPhase.RUNNING || phase == MatchPhase.COUNTDOWN || phase == MatchPhase.RESULT) {
            state.queued(true);
            state.participating(false);
            player.setGameMode(GameType.SPECTATOR);
        } else {
            loadoutService.clear(player);
            state.participating(false);
            state.respawning(false);
            player.setGameMode(GameType.ADVENTURE);
            if (data().lobby() != null) data().lobby().teleport(player);
        }
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
        Player attacker = source.getEntity() instanceof Player player ? player : source.getDirectEntity() instanceof Player player ? player : null;
        if (attacker instanceof ServerPlayer serverAttacker && serverAttacker != victim) {
            PlayerMatchState attackerState = state(serverAttacker);
            TeamSide attackerSide = teams.sideOf(serverAttacker, data());
            TeamSide victimSide = teams.sideOf(victim, data());
            if (attackerState.participating() && attackerSide != TeamSide.NONE && attackerSide != victimSide) {
                attackerState.addKill();
                addScore(attackerSide);
            }
        }
        victimState.respawning(true);
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
        return classRegistry.get(state.currentClass()).map(ClassDefinition::allowDrop).orElse(false);
    }

    public void setRule(String key, int value) {
        MatchRules rules = data().rules();
        switch (key) {
            case "maxPlayers" -> rules.maxPlayers(value);
            case "scoreLimit" -> rules.scoreLimit(value);
            case "timeLimitSeconds" -> rules.timeLimitSeconds(value);
            case "startCountdownSeconds" -> rules.startCountdownSeconds(value);
            case "respawnSeconds" -> rules.respawnSeconds(value);
            case "respawnProtectionSeconds" -> rules.respawnProtectionSeconds(value);
            case "resultSeconds" -> rules.resultSeconds(value);
            default -> throw new IllegalArgumentException("Unknown rule " + key);
        }
        data().setDirty();
        syncAll();
    }

    public void resetRules() {
        data().rules().reset();
        data().setDirty();
        syncAll();
    }

    public MatchSnapshot snapshot(ServerPlayer viewer) {
        MatchRules rules = data().rules();
        PlayerMatchState state = state(viewer);
        TeamSide side = teams.sideOf(viewer, data());
        int remaining = Math.max(0, rules.timeLimitSeconds() - elapsedTicks / 20);
        List<MatchSnapshot.ClassView> classViews = classRegistry.all().stream()
                .map(c -> new MatchSnapshot.ClassView(c.id(), c.displayName(), c.description(), c.icon(), c.gunId(),
                        c.maxHealth(), c.movementSpeedMultiplier(), c.reserveAmmo()))
                .toList();
        return new MatchSnapshot(phase, side, redScore, blueScore, yellowScore, greenScore,
                rules.scoreLimit(), remaining, countSide(TeamSide.RED), countSide(TeamSide.BLUE),
                countSide(TeamSide.YELLOW), countSide(TeamSide.GREEN), state.currentClass(), state.pendingClass(),
                state.participating(), state.queued(), classViews);
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

    private void beginRunning() {
        phase = MatchPhase.RUNNING;
        elapsedTicks = 0;
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
        if (data().enabledTeams().stream().anyMatch(side -> score(side) >= rules.scoreLimit())
                || elapsedTicks >= rules.timeLimitSeconds() * 20) {
            result = determineWinner();
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
                    loadoutService.clear(player);
                    player.setGameMode(GameType.SPECTATOR);
                }
            }
            sync(player);
        }
    }

    private void deploy(ServerPlayer player, PlayerMatchState state, boolean applyPendingClass) {
        if (applyPendingClass && classRegistry.contains(state.pendingClass())) state.currentClass(state.pendingClass());
        String classId = selectedClass(state);
        Optional<ClassDefinition> definition = classRegistry.get(classId);
        TeamSide side = teams.sideOf(player, data());
        ArenaPosition spawn = side == TeamSide.NONE ? null : data().randomSpawn(side);
        if (definition.isEmpty() || spawn == null) {
            state.participating(false);
            state.queued(true);
            player.setGameMode(GameType.SPECTATOR);
            return;
        }
        state.currentClass(definition.get().id());
        state.pendingClass(definition.get().id());
        state.respawning(false);
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
        state.currentClass(state.pendingClass());
        deploy(player, state, false);
    }

    private void finishToLobby() {
        phase = data().isArenaConfigured() && teams.bindingsValid(server, data()) ? MatchPhase.LOBBY : MatchPhase.UNCONFIGURED;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMatchState state = state(player);
            loadoutService.clear(player);
            state.participating(false);
            state.respawning(false);
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
        if (classRegistry.contains(state.pendingClass())) return state.pendingClass();
        if (classRegistry.contains(state.currentClass())) return state.currentClass();
        return null;
    }

    private void ensureDefaultClass(PlayerMatchState state) {
        if (classRegistry.contains(selectedClass(state))) return;
        classRegistry.defaultClass().ifPresent(definition -> {
            state.currentClass(definition.id());
            state.pendingClass(definition.id());
        });
    }

    private void addScore(TeamSide side) {
        switch (side) {
            case RED -> redScore++;
            case BLUE -> blueScore++;
            case YELLOW -> yellowScore++;
            case GREEN -> greenScore++;
            case NONE -> { }
        }
    }

    private TeamSide determineWinner() {
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

    private void forParticipants(java.util.function.Consumer<ServerPlayer> consumer) {
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

    private void sync(ServerPlayer player) {
        SFGameNetwork.sendSnapshot(player, snapshot(player));
    }

    private void syncAll() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }
}

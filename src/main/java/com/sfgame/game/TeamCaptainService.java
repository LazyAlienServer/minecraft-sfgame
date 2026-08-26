package com.sfgame.game;

import com.sfgame.data.MatchRules;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Match-scoped respawn-anchor captains for domination and CTF.
 *
 * <p>Breakthrough's attacker captain remains owned by BreakthroughRuntime;
 * this service never supplies a captain class role.</p>
 */
public final class TeamCaptainService {
    private final EnumMap<TeamSide, Election> elections = new EnumMap<>(TeamSide.class);
    private String modeId = "";

    public void prepare(MinecraftServer server, MatchManager manager, MatchRules rules) {
        clearState();
        modeId = manager.savedData().selectedMode();
        if (!supports(modeId)) return;
        for (TeamSide side : enabledSides(manager)) {
            Election election = new Election();
            election.electionTicks = Math.max(1, rules.captainVoteSeconds() * 20);
            elections.put(side, election);
        }
        manager.forParticipants(player -> {
            if (elections.containsKey(manager.teams().sideOf(player, manager.savedData()))) {
                SFGameNetwork.openMenu(player);
            }
        });
    }

    /** Returns true while any initial election is still running. */
    public boolean tickPreparation(MinecraftServer server, MatchManager manager, MatchRules rules) {
        if (!supports(manager.savedData().selectedMode())) return true;
        boolean pending = false;
        for (TeamSide side : enabledSides(manager)) {
            Election election = elections.computeIfAbsent(side, ignored -> new Election());
            if (election.electionTicks > 0) {
                election.electionTicks--;
                pending = true;
                if (election.electionTicks == 0) resolve(server, manager, side, false);
            }
        }
        return !pending && elections.values().stream().allMatch(election -> election.electionTicks <= 0);
    }

    public boolean vote(ServerPlayer voter, @Nullable ServerPlayer candidate, boolean abstain, MatchManager manager) {
        TeamSide side = manager.teams().sideOf(voter, manager.savedData());
        Election election = elections.get(side);
        if (election == null || election.electionTicks <= 0 || !isEligibleCandidate(voter, side, manager)) return false;
        election.votes.remove(voter.getUUID());
        election.abstentions.remove(voter.getUUID());
        if (abstain) {
            election.abstentions.add(voter.getUUID());
            return true;
        }
        if (candidate == null || !isEligibleCandidate(candidate, side, manager)) return false;
        election.votes.put(voter.getUUID(), candidate.getUUID());
        return true;
    }

    @Nullable
    public UUID captain(TeamSide side) {
        Election election = elections.get(side);
        return election == null ? null : election.captain;
    }

    public boolean isCaptain(UUID playerId) {
        return elections.values().stream().anyMatch(election -> playerId.equals(election.captain));
    }

    public int electionSeconds(TeamSide side) {
        Election election = elections.get(side);
        return election == null ? 0 : Math.max(0, (election.electionTicks + 19) / 20);
    }

    public List<MatchSnapshotCandidate> candidates(ServerPlayer viewer, MatchManager manager) {
        TeamSide side = manager.teams().sideOf(viewer, manager.savedData());
        Election election = elections.get(side);
        if (election == null || election.electionTicks <= 0 || !isEligibleCandidate(viewer, side, manager)) return List.of();
        return eligible(manager, side).stream()
                .sorted(Comparator.comparing((ServerPlayer player) -> player.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(player -> player.getUUID().toString()))
                .map(player -> new MatchSnapshotCandidate(player.getUUID().toString(), player.getGameProfile().getName()))
                .toList();
    }

    public void maintain(MinecraftServer server, MatchManager manager, MatchRules rules) {
        String currentMode = manager.savedData().selectedMode();
        if (!supports(currentMode)) {
            clearState();
            return;
        }
        modeId = currentMode;
        Set<TeamSide> enabled = new HashSet<>(enabledSides(manager));
        elections.keySet().removeIf(side -> !enabled.contains(side));
        for (TeamSide side : enabled) {
            Election election = elections.computeIfAbsent(side, ignored -> new Election());
            if (election.captain != null && !captainStillOwnsRole(election.captain, side, manager)) {
                election.captain = null;
                election.votes.clear();
                election.abstentions.clear();
                election.electionTicks = Math.max(1, rules.captainReplacementVoteSeconds() * 20);
                announceElection(server, manager, side, true);
            } else if (election.captain == null && election.electionTicks <= 0) {
                election.electionTicks = Math.max(1, rules.captainReplacementVoteSeconds() * 20);
                election.votes.clear();
                election.abstentions.clear();
                announceElection(server, manager, side, true);
            }
            if (election.electionTicks > 0) {
                election.electionTicks--;
                if (election.electionTicks == 0) resolve(server, manager, side, true);
            }
        }
    }

    /** Death never revokes anchor-captain ownership. */
    public void onPlayerDeath(ServerPlayer player, MatchManager manager) {
        // Intentionally no-op: respawning captains retain the role.
    }

    public void onPlayerLoggedOut(ServerPlayer player, MatchManager manager, MatchRules rules) {
        if (manager.phase() == MatchPhase.RUNNING) maintain(player.getServer(), manager, rules);
    }

    public void onPlayerTeamChanged(ServerPlayer player, MatchManager manager, MatchRules rules) {
        if (manager.phase() == MatchPhase.RUNNING) maintain(player.getServer(), manager, rules);
    }
    public void onRuleChanged(String key, MatchRules rules) {
        // Active elections keep their deadline; the new duration applies to the next election.
    }
    public void clear(MinecraftServer server) {
        clearState();
    }

    public boolean supports(String selectedMode) {
        return GameModeRegistry.DOMINATION.equals(selectedMode)
                || GameModeRegistry.CAPTURE_THE_FLAG.equals(selectedMode);
    }

    public record MatchSnapshotCandidate(String uuid, String name) { }

    private void resolve(MinecraftServer server, MatchManager manager, TeamSide side, boolean replacement) {
        Election election = elections.get(side);
        if (election == null) return;
        List<ServerPlayer> eligible = eligible(manager, side);
        UUID selected = null;
        if (eligible.size() == 1) {
            selected = eligible.get(0).getUUID();
        } else if (!eligible.isEmpty()) {
            Set<UUID> eligibleIds = eligible.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
            Map<UUID, Integer> counts = new HashMap<>();
            for (UUID candidate : election.votes.values()) {
                if (eligibleIds.contains(candidate)) counts.merge(candidate, 1, Integer::sum);
            }
            int cast = counts.values().stream().mapToInt(Integer::intValue).sum();
            int abstainCount = election.abstentions.size()
                    + Math.max(0, eligible.size() - election.votes.size() - election.abstentions.size());
            if (abstainCount > cast || counts.isEmpty()) {
                selected = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())).getUUID();
            } else {
                int high = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                List<UUID> leaders = counts.entrySet().stream()
                        .filter(entry -> entry.getValue() == high)
                        .map(Map.Entry::getKey)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList();
                selected = leaders.get(ThreadLocalRandom.current().nextInt(leaders.size()));
            }
        }
        election.captain = selected;
        election.votes.clear();
        election.abstentions.clear();
        election.electionTicks = 0;
        if (selected != null) {
            ServerPlayer player = manager.serverPlayer(selected);
            if (player != null) {
                player.sendSystemMessage(Component.translatable(replacement
                        ? "sfgame.anchor_captain.reelected" : "sfgame.anchor_captain.selected",
                        player.getDisplayName(), side.id()));
            }
        }
        manager.syncAll();
    }

    private void announceElection(MinecraftServer server, MatchManager manager, TeamSide side, boolean replacement) {
        Election election = elections.get(side);
        if (election == null) return;
        Component message = Component.translatable(replacement
                ? "sfgame.anchor_captain.reelect" : "sfgame.anchor_captain.vote",
                Math.max(1, (election.electionTicks + 19) / 20), side.id());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (manager.state(player).participating() && manager.teams().sideOf(player, manager.savedData()) == side) {
                player.sendSystemMessage(message, true);
                SFGameNetwork.openMenu(player);
            }
        }
    }

    private boolean captainStillOwnsRole(UUID playerId, TeamSide side, MatchManager manager) {
        ServerPlayer player = manager.serverPlayer(playerId);
        return player != null && manager.state(player).participating()
                && manager.teams().sideOf(player, manager.savedData()) == side;
    }

    private List<ServerPlayer> eligible(MatchManager manager, TeamSide side) {
        if (manager.server() == null) return List.of();
        return manager.server().getPlayerList().getPlayers().stream()
                .filter(player -> isEligibleCandidate(player, side, manager))
                .toList();
    }

    private boolean isEligibleCandidate(ServerPlayer player, TeamSide side, MatchManager manager) {
        return player != null && manager.state(player).participating()
                && !manager.state(player).respawning() && !player.isSpectator() && !player.isDeadOrDying()
                && manager.teams().sideOf(player, manager.savedData()) == side;
    }

    private List<TeamSide> enabledSides(MatchManager manager) {
        return manager.savedData().enabledTeams().stream().filter(TeamSide.PLAYABLE::contains).toList();
    }

    private void clearState() {
        elections.clear();
        modeId = "";
    }

    private static final class Election {
        private UUID captain;
        private final Map<UUID, UUID> votes = new HashMap<>();
        private final Set<UUID> abstentions = new HashSet<>();
        private int electionTicks;
    }
}

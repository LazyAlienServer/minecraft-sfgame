package com.sfgame.game;

import com.sfgame.data.MatchRules;
import com.sfgame.network.SquadSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Match-only, authority-free squad membership and deterministic layout. */
public final class SquadService {
    private final MatchManager manager;
    private final EnumMap<TeamSide, List<Squad>> squads = new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, Set<UUID>> knownPlayers = new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, Integer> knownCaps = new EnumMap<>(TeamSide.class);

    public SquadService(MatchManager manager) {
        this.manager = manager;
    }

    public void beginRunning() {
        clear();
    }

    public void clear() {
        squads.clear();
        knownPlayers.clear();
        knownCaps.clear();
    }

    public void tick() {
        if (manager.phase() != MatchPhase.RUNNING || manager.server() == null) {
            clear();
            return;
        }
        for (TeamSide side : manager.savedData().enabledTeams()) ensureSide(side);
        squads.keySet().removeIf(side -> !manager.savedData().enabledTeams().contains(side));
    }

    public boolean join(ServerPlayer player, int squadIndex) {
        if (manager.phase() != MatchPhase.RUNNING || manager.server() == null) return false;
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        if (side == TeamSide.NONE || !manager.savedData().enabledTeams().contains(side)) return false;
        ensureSide(side);
        List<Squad> sideSquads = squads.getOrDefault(side, List.of());
        Squad destination = sideSquads.stream().filter(squad -> squad.index == squadIndex).findFirst().orElse(null);
        if (destination == null || destination.members.size() >= manager.rules().squadMaxMembers()) return false;
        Squad current = find(player.getUUID(), side);
        if (current == destination) return true;
        if (current != null) current.members.remove(player.getUUID());
        destination.members.add(player.getUUID());
        trimSide(side);
        return true;
    }

    public boolean leave(ServerPlayer player) {
        if (manager.phase() != MatchPhase.RUNNING) return false;
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        if (side == TeamSide.NONE) return false;
        ensureSide(side);
        Squad current = find(player.getUUID(), side);
        if (current == null) return true;
        current.members.remove(player.getUUID());
        trimSide(side);
        return true;
    }

    public void remove(ServerPlayer player) {
        UUID id = player.getUUID();
        for (TeamSide side : TeamSide.PLAYABLE) {
            List<Squad> sideSquads = squads.get(side);
            if (sideSquads == null) continue;
            sideSquads.forEach(squad -> squad.members.remove(id));
            trimSide(side);
        }
    }

    public Integer squadIndex(UUID playerId, TeamSide side) {
        if (side == TeamSide.NONE || manager.phase() != MatchPhase.RUNNING) return null;
        ensureSide(side);
        Squad squad = find(playerId, side);
        return squad == null ? null : squad.index;
    }

    public SquadSnapshot snapshot(ServerPlayer viewer) {
        if (manager.phase() != MatchPhase.RUNNING || manager.server() == null) {
            return SquadSnapshot.empty(manager.teams().sideOf(viewer, manager.savedData()));
        }
        TeamSide side = manager.teams().sideOf(viewer, manager.savedData());
        if (side == TeamSide.NONE || !manager.savedData().enabledTeams().contains(side)) {
            return SquadSnapshot.empty(side);
        }
        ensureSide(side);
        MatchRules rules = manager.rules();
        int health = manager.beacons().health(side);
        int maxHealth = manager.beacons().maxHealth(side);
        Integer current = squadIndex(viewer.getUUID(), side);
        List<SquadSnapshot.SquadView> views = new ArrayList<>();
        for (Squad squad : squads.getOrDefault(side, List.of())) {
            List<SquadSnapshot.MemberView> members = squad.members.stream()
                    .map(id -> memberView(id))
                    .toList();
            views.add(new SquadSnapshot.SquadView(squad.index, members.size(), members));
        }
        return new SquadSnapshot(side, currentPlayerCount(side), rules.squadMaxMembers(), current,
                health, maxHealth, List.copyOf(views));
    }
    public List<UUID> members(TeamSide side) {
        if (side == TeamSide.NONE || manager.phase() != MatchPhase.RUNNING) return List.of();
        ensureSide(side);
        return squads.getOrDefault(side, List.of()).stream()
                .flatMap(squad -> squad.members.stream()).toList();
    }

    public int currentPlayerCount(TeamSide side) {
        return onlinePlayers(side).size();
    }

    private SquadSnapshot.MemberView memberView(UUID id) {
        ServerPlayer player = manager.serverPlayer(id);
        if (player == null) return new SquadSnapshot.MemberView(id, id.toString(), false, false, false);
        PlayerMatchState state = manager.state(player);
        return new SquadSnapshot.MemberView(id, player.getGameProfile().getName(), true,
                state.participating(), state.respawning());
    }

    private void ensureSide(TeamSide side) {
        if (side == TeamSide.NONE || manager.phase() != MatchPhase.RUNNING || manager.server() == null) return;
        int cap = manager.rules().squadMaxMembers();
        Set<UUID> currentPlayers = onlinePlayers(side);
        int required = requiredCount(currentPlayers.size(), cap);
        List<Squad> sideSquads = squads.computeIfAbsent(side, ignored -> new ArrayList<>());
        boolean changed = cap != knownCaps.getOrDefault(side, cap)
                || !currentPlayers.equals(knownPlayers.getOrDefault(side, Set.of()))
                || sideSquads.stream().anyMatch(squad -> squad.members.size() > cap)
                || sideSquads.size() < required;
        sideSquads.forEach(squad -> squad.members.removeIf(id -> !currentPlayers.contains(id)));
        if (changed) {
            List<UUID> existing = sideSquads.stream().flatMap(squad -> squad.members.stream())
                    .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
            sideSquads.clear();
            for (int i = 1; i <= required; i++) sideSquads.add(new Squad(i));
            int cursor = 0;
            for (UUID id : existing) {
                while (cursor < sideSquads.size() && sideSquads.get(cursor).members.size() >= cap) cursor++;
                if (cursor >= sideSquads.size()) break;
                sideSquads.get(cursor).members.add(id);
            }
            knownCaps.put(side, cap);
            knownPlayers.put(side, Set.copyOf(currentPlayers));
        } else {
            while (sideSquads.size() < required) sideSquads.add(new Squad(sideSquads.size() + 1));
            trimSide(side);
        }
        for (int i = 0; i < sideSquads.size(); i++) sideSquads.get(i).index = i + 1;
    }

    private void trimSide(TeamSide side) {
        List<Squad> sideSquads = squads.get(side);
        if (sideSquads == null) return;
        int cap = manager.rules().squadMaxMembers();
        int required = requiredCount(currentPlayerCount(side), cap);
        while (sideSquads.size() > required) {
            Squad last = sideSquads.get(sideSquads.size() - 1);
            if (last.members.isEmpty()) {
                sideSquads.remove(sideSquads.size() - 1);
                continue;
            }
            List<UUID> members = sideSquads.stream().flatMap(squad -> squad.members.stream())
                    .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
            sideSquads.clear();
            for (int i = 1; i <= required; i++) sideSquads.add(new Squad(i));
            int cursor = 0;
            for (UUID id : members) {
                while (cursor < sideSquads.size() && sideSquads.get(cursor).members.size() >= cap) cursor++;
                if (cursor >= sideSquads.size()) break;
                sideSquads.get(cursor).members.add(id);
            }
        }
        for (int i = 0; i < sideSquads.size(); i++) sideSquads.get(i).index = i + 1;
    }

    private Squad find(UUID id, TeamSide side) {
        return squads.getOrDefault(side, List.of()).stream()
                .filter(squad -> squad.members.contains(id)).findFirst().orElse(null);
    }

    private Set<UUID> onlinePlayers(TeamSide side) {
        if (manager.server() == null) return Set.of();
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer player : manager.server().getPlayerList().getPlayers()) {
            if (manager.teams().sideOf(player, manager.savedData()) == side) ids.add(player.getUUID());
        }
        return ids;
    }

    static int requiredCount(int players, int cap) {
        return players == 0 ? 0 : (players + cap - 1) / cap;
    }

    private static final class Squad {
        private int index;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        private Squad(int index) {
            this.index = index;
        }
    }
}

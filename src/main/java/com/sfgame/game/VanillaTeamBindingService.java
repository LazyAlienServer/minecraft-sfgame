package com.sfgame.game;

import com.sfgame.data.SFGameSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class VanillaTeamBindingService {
    public void ensureDefaultTeams(MinecraftServer server, SFGameSavedData data) {
        Scoreboard scoreboard = server.getScoreboard();
        for (TeamSide side : TeamSide.PLAYABLE) {
            String name = data.teamName(side);
            if (scoreboard.getPlayerTeam(name) == null) {
                PlayerTeam team = scoreboard.addPlayerTeam(name);
                team.setColor(side.color());
            }
        }
    }

    public boolean bindingsValid(MinecraftServer server, SFGameSavedData data) {
        List<String> names = TeamSide.PLAYABLE.stream().map(data::teamName).toList();
        return names.stream().distinct().count() == names.size()
                && names.stream().allMatch(name -> server.getScoreboard().getPlayerTeam(name) != null);
    }

    public TeamSide sideOf(ServerPlayer player, SFGameSavedData data) {
        PlayerTeam team = player.getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null) return TeamSide.NONE;
        return TeamSide.PLAYABLE.stream().filter(side -> team.getName().equals(data.teamName(side)))
                .findFirst().orElse(TeamSide.NONE);
    }

    public boolean assign(ServerPlayer player, TeamSide side, SFGameSavedData data) {
        if (side == TeamSide.NONE) return false;
        PlayerTeam team = player.getServer().getScoreboard().getPlayerTeam(data.teamName(side));
        if (team == null || !player.getServer().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team)) {
            return false;
        }
        refreshTabName(player);
        return true;
    }

    public void remove(ServerPlayer player) {
        player.getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        refreshTabName(player);
    }

    public TeamSide balancedSide(MinecraftServer server, SFGameSavedData data) {
        List<TeamSide> enabled = data.enabledTeams();
        // Team commands are also useful while a map is still being prepared,
        // before any spawn has been added.  In that case there is no spawned
        // team list yet, so balance across the currently bound vanilla teams
        // instead of returning NONE and making `team set ... random` fail.
        if (enabled.isEmpty()) {
            enabled = TeamSide.PLAYABLE.stream()
                    .filter(side -> server.getScoreboard().getPlayerTeam(data.teamName(side)) != null)
                    .toList();
        }
        if (enabled.isEmpty()) return TeamSide.NONE;
        int minimum = enabled.stream().mapToInt(side -> count(server, data, side)).min().orElse(0);
        List<TeamSide> candidates = enabled.stream().filter(side -> count(server, data, side) == minimum).toList();
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private int count(MinecraftServer server, SFGameSavedData data, TeamSide side) {
        return (int) server.getPlayerList().getPlayers().stream().filter(player -> sideOf(player, data) == side).count();
    }

    private static void refreshTabName(ServerPlayer player) {
        // SFGame supplies a custom tab-list component with K/D appended. Forge
        // caches that component, so vanilla scoreboard packets alone cannot
        // update its embedded team color after a menu-driven team change.
        player.refreshTabListName();
    }
}

package com.sfgame.game;

import com.sfgame.data.SFGameSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.concurrent.ThreadLocalRandom;

public final class VanillaTeamBindingService {
    public void ensureDefaultTeams(MinecraftServer server, SFGameSavedData data) {
        Scoreboard scoreboard = server.getScoreboard();
        if (scoreboard.getPlayerTeam(data.redTeam()) == null) {
            PlayerTeam red = scoreboard.addPlayerTeam(data.redTeam());
            red.setColor(ChatFormatting.RED);
        }
        if (scoreboard.getPlayerTeam(data.blueTeam()) == null) {
            PlayerTeam blue = scoreboard.addPlayerTeam(data.blueTeam());
            blue.setColor(ChatFormatting.BLUE);
        }
    }

    public boolean bindingsValid(MinecraftServer server, SFGameSavedData data) {
        return !data.redTeam().equals(data.blueTeam())
                && server.getScoreboard().getPlayerTeam(data.redTeam()) != null
                && server.getScoreboard().getPlayerTeam(data.blueTeam()) != null;
    }

    public TeamSide sideOf(ServerPlayer player, SFGameSavedData data) {
        PlayerTeam team = player.getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null) return TeamSide.NONE;
        if (team.getName().equals(data.redTeam())) return TeamSide.RED;
        if (team.getName().equals(data.blueTeam())) return TeamSide.BLUE;
        return TeamSide.NONE;
    }

    public boolean assign(ServerPlayer player, TeamSide side, SFGameSavedData data) {
        String teamName = side == TeamSide.RED ? data.redTeam() : side == TeamSide.BLUE ? data.blueTeam() : null;
        if (teamName == null) return false;
        PlayerTeam team = player.getServer().getScoreboard().getPlayerTeam(teamName);
        return team != null && player.getServer().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
    }

    public void remove(ServerPlayer player) {
        player.getServer().getScoreboard().removePlayerFromTeam(player.getScoreboardName());
    }

    public TeamSide balancedSide(MinecraftServer server, SFGameSavedData data) {
        int red = 0;
        int blue = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeamSide side = sideOf(player, data);
            if (side == TeamSide.RED) red++;
            if (side == TeamSide.BLUE) blue++;
        }
        if (red < blue) return TeamSide.RED;
        if (blue < red) return TeamSide.BLUE;
        return ThreadLocalRandom.current().nextBoolean() ? TeamSide.RED : TeamSide.BLUE;
    }
}

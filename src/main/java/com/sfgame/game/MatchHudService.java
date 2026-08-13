package com.sfgame.game;

import com.sfgame.data.MatchRules;
import com.sfgame.data.SFGameSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class MatchHudService {
    private static final String OBJECTIVE_NAME = "sfgame_tdm";
    private static final String RED_LINE = ChatFormatting.RED + "RED";
    private static final String BLUE_LINE = ChatFormatting.BLUE + "BLUE";
    private static final String TIME_LINE = ChatFormatting.GOLD + "TIME";

    public void update(MinecraftServer server, MatchManager manager) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        MatchRules rules = SFGameSavedData.get(server).rules();
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
                    Component.literal("SFGame TDM / " + rules.scoreLimit()), ObjectiveCriteria.RenderType.INTEGER);
        } else {
            objective.setDisplayName(Component.literal("SFGame TDM / " + rules.scoreLimit()));
        }
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        scoreboard.getOrCreatePlayerScore(RED_LINE, objective).setScore(manager.redScore());
        scoreboard.getOrCreatePlayerScore(BLUE_LINE, objective).setScore(manager.blueScore());
        int remaining = Math.max(0, rules.timeLimitSeconds() - manager.elapsedTicks() / 20);
        scoreboard.getOrCreatePlayerScore(TIME_LINE, objective).setScore(remaining);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.refreshTabListName();

    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) scoreboard.removeObjective(objective);
    }
}

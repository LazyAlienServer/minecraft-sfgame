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
    private static final String OBJECTIVE_NAME = "sfgame_match";
    private static final String TIME_LINE = ChatFormatting.GOLD + "TIME";

    public void update(MinecraftServer server, MatchManager manager) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (manager.phase() == MatchPhase.LOBBY || manager.phase() == MatchPhase.UNCONFIGURED) {
            if (objective != null && scoreboard.getDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR) == objective) {
                scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
            }
            return;
        }
        SFGameSavedData data = SFGameSavedData.get(server);
        MatchRules rules = data.rules();
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
                    Component.literal(title(data.selectedMode(), rules.scoreLimit())), ObjectiveCriteria.RenderType.INTEGER);
        } else {
            objective.setDisplayName(Component.literal(title(data.selectedMode(), rules.scoreLimit())));
        }
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        for (TeamSide side : TeamSide.PLAYABLE) {
            String line = side.color() + side.id().toUpperCase();
            if (data.enabledTeams().contains(side)) {
                scoreboard.getOrCreatePlayerScore(line, objective).setScore(manager.score(side));
            } else {
                scoreboard.resetPlayerScore(line, objective);
            }
        }
        int remaining = Math.max(0, rules.timeLimitSeconds() - manager.elapsedTicks() / 20);
        scoreboard.getOrCreatePlayerScore(TIME_LINE, objective).setScore(remaining);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.refreshTabListName();

    }

    private static String title(String modeId, int scoreLimit) {
        return "SFGame " + (GameModeRegistry.DOMINATION.equals(modeId) ? "DOMINATION" : "TDM") + " / " + scoreLimit;
    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) scoreboard.removeObjective(objective);
    }
}

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
    private static final String TICKETS_LINE = ChatFormatting.RED + "TICKETS";
    private static final String LEG_LINE = ChatFormatting.AQUA + "LEG";
    private static final String SECTOR_LINE = ChatFormatting.YELLOW + "SECTOR";

    public void update(MinecraftServer server, MatchManager manager) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        // Also refresh in the lobby so direct vanilla /team changes and team
        // color modifications invalidate Forge's cached custom tab-list names.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.refreshTabListName();
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
            if (!GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode()) && data.enabledTeams().contains(side)) {
                scoreboard.getOrCreatePlayerScore(line, objective).setScore(manager.score(side));
            } else {
                scoreboard.resetPlayerScore(line, objective);
            }
        }
        scoreboard.getOrCreatePlayerScore(TIME_LINE, objective).setScore(manager.remainingSeconds());
        if (GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())) {
            BreakthroughRuntime runtime = manager.breakthrough();
            scoreboard.getOrCreatePlayerScore(TICKETS_LINE, objective).setScore(runtime.tickets());
            scoreboard.getOrCreatePlayerScore(LEG_LINE, objective).setScore(runtime.leg());
            scoreboard.getOrCreatePlayerScore(SECTOR_LINE, objective).setScore(runtime.sectorNumber());
        } else if (GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode())
                && data.activeMap() != null
                && data.activeMap().captureTheFlag().variant() == com.sfgame.data.CtfVariant.ASSAULT) {
            scoreboard.getOrCreatePlayerScore(TICKETS_LINE, objective).setScore(manager.captureTheFlag().attackerTickets());
            scoreboard.resetPlayerScore(LEG_LINE, objective);
            scoreboard.resetPlayerScore(SECTOR_LINE, objective);
        } else {
            scoreboard.resetPlayerScore(TICKETS_LINE, objective);
            scoreboard.resetPlayerScore(LEG_LINE, objective);
            scoreboard.resetPlayerScore(SECTOR_LINE, objective);
        }
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode())
                && manager.phase() == MatchPhase.RUNNING
                && !manager.ctfShop().items().isEmpty()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (manager.state(player).respawning()) continue;
                String currency = Integer.toString(manager.state(player).currency(GameModeRegistry.CAPTURE_THE_FLAG));
                player.sendSystemMessage(Component.literal(manager.captureTheFlag().hudLine(player) + " · $" + currency), true);
            }
        }
    }

    private static String title(String modeId, int scoreLimit) {
        if (GameModeRegistry.BREAKTHROUGH.equals(modeId)) return "SFGame BREAKTHROUGH";
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)) return "SFGame CTF / " + scoreLimit;
        return "SFGame " + (GameModeRegistry.DOMINATION.equals(modeId) ? "DOMINATION" : "TDM") + " / " + scoreLimit;
    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) scoreboard.removeObjective(objective);
    }
}

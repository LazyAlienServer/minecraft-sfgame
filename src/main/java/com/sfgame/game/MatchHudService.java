package com.sfgame.game;

import com.sfgame.data.MatchRules;
import com.sfgame.data.SFGameSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class MatchHudService {
    private static final String OBJECTIVE_NAME = "sfgame_match";
    private static final String TIME_LINE = "sfgame_time";
    private static final String TICKETS_LINE = "sfgame_tickets";
    private static final String LEG_LINE = "sfgame_leg";
    private static final String ROUND_LINE = "sfgame_round";
    private static final String SECTOR_LINE = "sfgame_sector";

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
        MatchRules rules = manager.rules();
        String mapName = data.activeMap() == null ? data.selectedMap() : data.activeMap().displayName();
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
                    title(data.selectedMode(), mapName), ObjectiveCriteria.RenderType.INTEGER);
        } else {
            objective.setDisplayName(title(data.selectedMode(), mapName));
        }
        // The client draws this objective from MatchSnapshot so labels and
        // formatted values stay as Components and resolve in each client locale.
        if (scoreboard.getDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR) == objective) {
            scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
        }
        for (TeamSide side : TeamSide.PLAYABLE) {
            String line = side.id().toUpperCase();
            if (!GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode()) && data.enabledTeams().contains(side)) {
                scoreboard.getOrCreatePlayerScore(line, objective).setScore(manager.score(side));
            } else {
                scoreboard.resetPlayerScore(line, objective);
            }
        }

        int remainingSeconds = manager.remainingSeconds();
        setLine(scoreboard, objective, TIME_LINE,
                remainingSeconds != MatchRules.UNLIMITED_TIME_SECONDS || rules.showUnlimitedTime(),
                remainingSeconds == MatchRules.UNLIMITED_TIME_SECONDS ? 0 : remainingSeconds);
        if (GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())) {
            BreakthroughRuntime runtime = manager.breakthrough();
            setLine(scoreboard, objective, TICKETS_LINE,
                    runtime.tickets() != MatchRules.UNLIMITED_TICKETS || rules.showUnlimitedTickets(),
                    runtime.tickets() == MatchRules.UNLIMITED_TICKETS ? 0 : runtime.tickets());
            setLine(scoreboard, objective, LEG_LINE, true, runtime.remainingLegs(rules));
            setLine(scoreboard, objective, ROUND_LINE, true, runtime.attackRoundsRemaining());
            setLine(scoreboard, objective, SECTOR_LINE, manager.devMode(), runtime.sectorNumber());
        } else if (GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode())
                && data.activeMap() != null
                && rules.ctfVariant() == com.sfgame.data.CtfVariant.ASSAULT) {
            int tickets = manager.captureTheFlag().attackerTickets();
            setLine(scoreboard, objective, TICKETS_LINE,
                    tickets != MatchRules.UNLIMITED_TICKETS || rules.showUnlimitedTickets(),
                    tickets == MatchRules.UNLIMITED_TICKETS ? 0 : tickets);
            setLine(scoreboard, objective, LEG_LINE, false, 0);
            setLine(scoreboard, objective, ROUND_LINE, false, 0);
            setLine(scoreboard, objective, SECTOR_LINE, false, 0);
        } else {
            setLine(scoreboard, objective, TICKETS_LINE, false, 0);
            setLine(scoreboard, objective, LEG_LINE, false, 0);
            setLine(scoreboard, objective, ROUND_LINE, false, 0);
            setLine(scoreboard, objective, SECTOR_LINE, false, 0);
        }
        if (manager.economyEnabled()
                && manager.phase() == MatchPhase.RUNNING
                && !manager.modeBlocksCombat()
                && !manager.shop().items(data.selectedMode()).isEmpty()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (manager.state(player).respawning()) continue;
                String currency = Integer.toString(manager.state(player).currency(data.selectedMode()));
                String prefix = GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode())
                        ? manager.captureTheFlag().hudLine(player) + " · " : "";
                player.sendSystemMessage(Component.literal(prefix + "$" + currency), true);
            }
        }
    }

    private static void setLine(Scoreboard scoreboard, Objective objective,
                                String holder, boolean visible, int score) {
        if (!visible) {
            scoreboard.resetPlayerScore(holder, objective);
            return;
        }
        scoreboard.getOrCreatePlayerScore(holder, objective).setScore(score);
    }

    public static String formatRemainingTime(int seconds) {
        if (seconds == MatchRules.UNLIMITED_TIME_SECONDS) return "∞";
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600L;
        long minutes = safeSeconds % 3600L / 60L;
        long remainder = safeSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    private static Component title(String modeId, String mapName) {
        String key = "sfgame.mode." + modeId;
        Component mode = Component.translatable(key);
        if (mode.getString().equals(key)) mode = Component.literal(modeId);
        return Component.empty().append(mode).append("/").append(Component.literal(mapName));
    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) scoreboard.removeObjective(objective);
    }
}

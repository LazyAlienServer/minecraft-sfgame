package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.MatchRules;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public final class TeamDeathmatchRuntime implements MatchModeRuntime {
    @Override public List<String> validate(MinecraftServer server, ArenaMap map, MatchRules rules) { return List.of(); }
    @Override public void start(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) { }

    @Override
    public ModeTickResult tick(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        return map.enabledTeams().stream().anyMatch(side -> manager.score(side) >= rules.scoreLimit())
                ? ModeTickResult.finish(manager.determineWinner()) : ModeTickResult.CONTINUE;
    }

    @Override public void onKill(TeamSide killer, MatchManager manager) { manager.addTeamScore(killer, 1); }
    @Override public void stop() { }
}

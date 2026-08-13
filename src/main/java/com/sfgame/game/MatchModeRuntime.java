package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.MatchRules;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public interface MatchModeRuntime {
    List<String> validate(MinecraftServer server, ArenaMap map);
    void start(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules);
    ModeTickResult tick(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules);
    default void onKill(TeamSide killer, MatchManager manager) { }
    void stop();
}

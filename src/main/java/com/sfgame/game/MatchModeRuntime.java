package com.sfgame.game;

import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.MatchRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public interface MatchModeRuntime {
    List<String> validate(MinecraftServer server, ArenaMap map);
    default boolean needsPreparation(ArenaMap map) { return false; }
    default void prepare(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) { }
    default boolean tickPreparation(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) { return true; }
    void start(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules);
    ModeTickResult tick(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules);
    default void onKill(TeamSide killer, MatchManager manager) { }
    default void onPlayerDeath(TeamSide victim, MatchManager manager) { }
    default void onKill(ServerPlayer killer, TeamSide side, MatchManager manager) { onKill(side, manager); }
    default void onPlayerDeath(ServerPlayer victim, TeamSide side, MatchManager manager) { onPlayerDeath(side, manager); }
    default void onPlayerTeamChanged(ServerPlayer player, TeamSide oldSide, TeamSide newSide, MatchManager manager) { }
    default void onPlayerLoggedOut(ServerPlayer player, MatchManager manager) { }
    /** Mode sub-states may temporarily suspend the common map editing system. */
    default boolean allowsMapEditing() { return true; }
    default void onRuleChanged(String key, MatchRules rules) { }
    default ArenaPosition spawnFor(TeamSide side, ArenaMap map) { return map.randomSpawn(side); }
    default int remainingSeconds(MatchManager manager, MatchRules rules) {
        return Math.max(0, rules.timeLimitSeconds() - manager.elapsedTicks() / 20);
    }
    default boolean isCaptain(UUID playerId) { return false; }
    default boolean usesCommonTimeLimit() { return true; }
    default boolean blocksCombat() { return false; }
    void stop();
}

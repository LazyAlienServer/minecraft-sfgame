package com.sfgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.BoxCaptureRegion;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
final class SFGameCommandsTest {
    @Test
    void selectedBoxIncludesBothTargetedBlocksAtNegativeCoordinates() {
        ArenaPosition first = new ArenaPosition("minecraft:overworld", -25, 73, -71, 0, 0);
        ArenaPosition second = new ArenaPosition("minecraft:overworld", -19, 75, -72, 0, 0);

        BoxCaptureRegion region = SFGameCommands.selectedBlockBox(first, second, null, null);

        assertEquals(-25.0, region.minX());
        assertEquals(-19, Math.floor(region.maxX()));
        assertEquals(-72.0, region.minZ());
        assertEquals(-71, Math.floor(region.maxZ()));
    }

    @Test
    void buildStatusFormatsBothSetboxCorners() {
        BoxCaptureRegion bounded = new BoxCaptureRegion("minecraft:overworld",
                -25, Math.nextDown(-18.0), -72, Math.nextDown(-70.0), 60, 75);
        assertEquals("minecraft:overworld -25 60 -72", SFGameCommands.buildCornerText(bounded, true));
        assertEquals("minecraft:overworld -19 75 -71", SFGameCommands.buildCornerText(bounded, false));

        BoxCaptureRegion fullHeight = (BoxCaptureRegion) bounded.withHeight(null, null);
        assertEquals("minecraft:overworld -25 full-height -72",
                SFGameCommands.buildCornerText(fullHeight, true));
        assertEquals("minecraft:overworld -19 full-height -71",
                SFGameCommands.buildCornerText(fullHeight, false));
    }

    @Test
    void scoreCommandParsesTimeAndTeamUpdates() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SFGameCommands.scoreCommands());

        assertParses(dispatcher, "score");
        assertParses(dispatcher, "score time 300");
        assertParses(dispatcher, "score currency Dev 500");
        assertParses(dispatcher, "score tickets 75");
        assertParses(dispatcher, "score leg 10");
        assertParses(dispatcher, "score sector 3");
        assertParses(dispatcher, "score red 12");
        assertParses(dispatcher, "score green 1000000");

        ParseResults<CommandSourceStack> negativeTime = dispatcher.parse("score time -1", null);
        assertFalse(negativeTime.getExceptions().isEmpty());
    }

    @Test
    void breakthroughReportsRuntimeStateAndRejectsTeamScores() {
        assertFalse(SFGameCommands.supportsTeamScores(GameModeRegistry.BREAKTHROUGH));
        assertTrue(SFGameCommands.supportsTeamScores(GameModeRegistry.TEAM_DEATHMATCH));
        assertEquals("Remaining time=420s, attacker=red, defender=blue, tickets=73, leg=2, sector=3/5",
                SFGameCommands.breakthroughScoreStatus(
                        420, TeamSide.RED, TeamSide.BLUE, 73, 2, 3, 5));
        assertTrue(SFGameCommands.scoreFieldVisible(GameModeRegistry.BREAKTHROUGH, "tickets"));
        assertFalse(SFGameCommands.scoreFieldVisible(GameModeRegistry.BREAKTHROUGH, "red"));
        assertFalse(SFGameCommands.scoreFieldVisible(GameModeRegistry.CAPTURE_THE_FLAG, "tickets"));
        assertTrue(SFGameCommands.scoreFieldVisible(GameModeRegistry.CAPTURE_THE_FLAG, "red"));
        assertTrue(SFGameCommands.scoreFieldVisible(GameModeRegistry.CAPTURE_THE_FLAG, "time"));
        assertTrue(SFGameCommands.scoreFieldVisible(GameModeRegistry.CAPTURE_THE_FLAG, "currency"));
        assertFalse(SFGameCommands.scoreFieldVisible(GameModeRegistry.TEAM_DEATHMATCH, "currency"));
        assertFalse(SFGameCommands.scoreFieldVisible(GameModeRegistry.BREAKTHROUGH, "currency"));
    }

    private static void assertParses(CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        ParseResults<CommandSourceStack> result = dispatcher.parse(command, null);
        assertTrue(result.getExceptions().isEmpty(), result.getExceptions().toString());
        assertFalse(result.getReader().canRead(), "Unparsed input: " + result.getReader().getRemaining());
    }
}

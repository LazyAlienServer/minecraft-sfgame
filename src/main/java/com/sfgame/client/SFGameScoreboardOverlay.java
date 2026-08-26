package com.sfgame.client;

import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import com.sfgame.network.MatchSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SFGameScoreboardOverlay {
    private static final int BACKGROUND = 0x900B0D10;
    private static final int LINE_HEIGHT = 10;
    private static final int HORIZONTAL_PADDING = 4;
    private static final int VERTICAL_PADDING = 3;

    private SFGameScoreboardOverlay() {
    }

    static void render(GuiGraphics graphics, int screenWidth, int screenHeight, MatchSnapshot snapshot) {
        if (snapshot == null || snapshot.phase() == MatchPhase.LOBBY
                || snapshot.phase() == MatchPhase.UNCONFIGURED) return;

        List<ScoreboardRow> rows = new ArrayList<>();
        Component scoreboardTitle = title(snapshot);
        addTime(rows, snapshot);
        if (GameModeRegistry.BREAKTHROUGH.equals(snapshot.modeId())) {
            addTickets(rows, snapshot);
            addValue(rows, "sfgame.scoreboard.label.leg", snapshot.leg());
            addValue(rows, "sfgame.scoreboard.label.round", snapshot.attackRoundsRemaining());
            if (snapshot.devMode()) addValue(rows, "sfgame.scoreboard.label.sector", snapshot.sector());
        } else if (GameModeRegistry.CAPTURE_THE_FLAG.equals(snapshot.modeId())
                && "assault".equals(snapshot.ctfVariant())) {
            addTickets(rows, snapshot);
            addTeamScores(rows, snapshot);
        } else {
            addTeamScores(rows, snapshot);
        }

        Font font = Minecraft.getInstance().font;
        int maxWidth = font.width(scoreboardTitle);
        for (ScoreboardRow row : rows) maxWidth = Math.max(maxWidth, row.width(font));
        int right = screenWidth - 3;
        int left = Math.max(0, right - maxWidth - HORIZONTAL_PADDING * 2);
        int lineCount = rows.size() + 1;
        int top = topFor(screenHeight, lineCount);
        int bottom = top + lineCount * LINE_HEIGHT + VERTICAL_PADDING * 2;
        graphics.fill(left, top, screenWidth, bottom, BACKGROUND);
        int titleX = centeredX(left, screenWidth, font.width(scoreboardTitle));
        int y = top + VERTICAL_PADDING;
        graphics.drawString(font, scoreboardTitle, titleX, y, SFGameText.colorOf(scoreboardTitle), true);
        y += LINE_HEIGHT;
        for (ScoreboardRow row : rows) {
            graphics.drawString(font, row.label(), left + HORIZONTAL_PADDING, y,
                    SFGameText.colorOf(row.label()), true);
            graphics.drawString(font, row.value(), right - font.width(row.value()), y,
                    SFGameText.colorOf(row.value()), true);
            y += LINE_HEIGHT;
        }
    }
    static int topFor(int screenHeight, int lineCount) {
        int contentHeight = Math.max(0, lineCount) * LINE_HEIGHT + VERTICAL_PADDING * 2;
        return Math.max(0, (screenHeight - contentHeight) / 2);
    }
    static int centeredX(int left, int right, int textWidth) {
        return left + Math.max(0, (right - left - textWidth) / 2);
    }
    private static void addTime(List<ScoreboardRow> rows, MatchSnapshot snapshot) {
        if (snapshot.remainingSeconds() == -1 && !snapshot.showUnlimitedTime()) return;
        Component value = snapshot.remainingSeconds() == -1
                ? Component.translatable("sfgame.scoreboard.label.unlimited")
                : Component.literal(formatRemainingTime(snapshot.remainingSeconds()));
        rows.add(line("sfgame.scoreboard.label.time", scoreValue(value)));
    }

    private static void addTickets(List<ScoreboardRow> rows, MatchSnapshot snapshot) {
        if (snapshot.attackerTickets() == -1 && !snapshot.showUnlimitedTickets()) return;
        Component value = snapshot.attackerTickets() == -1
                ? Component.translatable("sfgame.scoreboard.label.unlimited")
                : Component.literal(Integer.toString(snapshot.attackerTickets()));
        rows.add(line("sfgame.scoreboard.label.tickets", scoreValue(value)));
    }

    private static void addTeamScores(List<ScoreboardRow> rows, MatchSnapshot snapshot) {
        for (TeamSide side : TeamSide.PLAYABLE) {
            if (snapshot.players(side) <= 0 && snapshot.score(side) == 0) continue;
            Component label = Component.translatable("sfgame.scoreboard.team." + side.id());
            rows.add(new ScoreboardRow(label, scoreValue(Integer.toString(snapshot.score(side)))));
        }
    }

    private static void addValue(List<ScoreboardRow> rows, String key, int value) {
        rows.add(line(key, scoreValue(Integer.toString(value))));
    }

    private static ScoreboardRow line(String key, Component value) {
        return new ScoreboardRow(Component.translatable(key), value);
    }

    private static Component scoreValue(Object value) {
        return Component.translatable("sfgame.scoreboard.value", value);
    }

    private record ScoreboardRow(Component label, Component value) {
        int width(Font font) {
            return font.width(label) + font.width(" ") + font.width(value);
        }
    }

    private static Component title(MatchSnapshot snapshot) {
        String key = "sfgame.scoreboard.mode." + snapshot.modeId();
        MutableComponent mode = Component.translatable(key);
        if (mode.getString().equals(key)) {
            mode = Component.translatable("sfgame.scoreboard.mode.fallback", snapshot.modeId());
        }
        return Component.empty().append(mode)
                .append(Component.translatable("sfgame.scoreboard.separator"))
                .append(Component.translatable("sfgame.scoreboard.map", snapshot.mapName()));
    }


    private static String formatRemainingTime(int seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600L;
        long minutes = safeSeconds % 3600L / 60L;
        long remainder = safeSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }
}

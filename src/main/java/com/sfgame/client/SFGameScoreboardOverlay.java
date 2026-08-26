package com.sfgame.client;

import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import com.sfgame.network.MatchSnapshot;
import net.minecraft.ChatFormatting;
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
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TITLE = 0xFFFFD65C;
    private static final int LINE_HEIGHT = 10;
    private static final int HORIZONTAL_PADDING = 4;
    private static final int VERTICAL_PADDING = 3;

    private SFGameScoreboardOverlay() {
    }

    static void render(GuiGraphics graphics, int screenWidth, MatchSnapshot snapshot) {
        if (snapshot == null || snapshot.phase() == MatchPhase.LOBBY
                || snapshot.phase() == MatchPhase.UNCONFIGURED) return;

        List<Component> lines = new ArrayList<>();
        lines.add(title(snapshot));
        addTime(lines, snapshot);
        if (GameModeRegistry.BREAKTHROUGH.equals(snapshot.modeId())) {
            addTickets(lines, snapshot);
            addValue(lines, "sfgame.scoreboard.leg", snapshot.leg(), ChatFormatting.AQUA);
            addValue(lines, "sfgame.scoreboard.round", snapshot.attackRoundsRemaining(), ChatFormatting.LIGHT_PURPLE);
            if (snapshot.devMode()) addValue(lines, "sfgame.scoreboard.sector", snapshot.sector(), ChatFormatting.YELLOW);
        } else if (GameModeRegistry.CAPTURE_THE_FLAG.equals(snapshot.modeId())
                && "assault".equals(snapshot.ctfVariant())) {
            addTickets(lines, snapshot);
            addTeamScores(lines, snapshot);
        } else {
            addTeamScores(lines, snapshot);
        }

        Font font = Minecraft.getInstance().font;
        int maxWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int right = screenWidth - 4;
        int left = Math.max(0, right - maxWidth - HORIZONTAL_PADDING * 2);
        int top = 4;
        int bottom = top + lines.size() * LINE_HEIGHT + VERTICAL_PADDING * 2;
        graphics.fill(left, top, right + HORIZONTAL_PADDING, bottom, BACKGROUND);
        int y = top + VERTICAL_PADDING;
        for (Component line : lines) {
            graphics.drawString(font, line, right - font.width(line), y, TEXT, true);
            y += LINE_HEIGHT;
        }
    }

    private static Component title(MatchSnapshot snapshot) {
        String key = "sfgame.mode." + snapshot.modeId();
        MutableComponent mode = Component.translatable(key);
        if (mode.getString().equals(key)) mode = Component.literal(snapshot.modeId());
        return Component.empty().append(mode.withStyle(ChatFormatting.GOLD))
                .append("/").append(Component.literal(snapshot.mapName()).withStyle(ChatFormatting.WHITE));
    }

    private static void addTime(List<Component> lines, MatchSnapshot snapshot) {
        if (snapshot.remainingSeconds() == -1 && !snapshot.showUnlimitedTime()) return;
        Component value = snapshot.remainingSeconds() == -1
                ? Component.translatable("sfgame.scoreboard.unlimited")
                : Component.literal(formatRemainingTime(snapshot.remainingSeconds()));
        lines.add(line("sfgame.scoreboard.time", ChatFormatting.GOLD, value));
    }

    private static void addTickets(List<Component> lines, MatchSnapshot snapshot) {
        if (snapshot.attackerTickets() == -1 && !snapshot.showUnlimitedTickets()) return;
        Component value = snapshot.attackerTickets() == -1
                ? Component.translatable("sfgame.scoreboard.unlimited")
                : Component.literal(Integer.toString(snapshot.attackerTickets()));
        lines.add(line("sfgame.scoreboard.tickets", ChatFormatting.RED, value));
    }

    private static void addTeamScores(List<Component> lines, MatchSnapshot snapshot) {
        for (TeamSide side : TeamSide.PLAYABLE) {
            if (snapshot.players(side) <= 0 && snapshot.score(side) == 0) continue;
            Component label = Component.translatable("sfgame.team." + side.id()).withStyle(side.color());
            lines.add(Component.empty().append(label).append(" ")
                    .append(Integer.toString(snapshot.score(side))));
        }
    }

    private static void addValue(List<Component> lines, String key, int value, ChatFormatting color) {
        lines.add(line(key, color, Component.literal(Integer.toString(value))));
    }

    private static Component line(String key, ChatFormatting color, Component value) {
        return Component.empty().append(Component.translatable(key).withStyle(color)).append(" ").append(value);
    }

    private static String formatRemainingTime(int seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600L;
        long minutes = safeSeconds % 3600L / 60L;
        long remainder = safeSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }
}

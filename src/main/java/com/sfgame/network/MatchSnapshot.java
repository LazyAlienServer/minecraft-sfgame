package com.sfgame.network;

import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MatchSnapshot(MatchPhase phase, TeamSide side, int redScore, int blueScore, int yellowScore,
                            int greenScore, int scoreLimit, int remainingSeconds, int redPlayers, int bluePlayers,
                            int yellowPlayers, int greenPlayers, String currentClass, String pendingClass,
                            boolean participating, boolean queued, List<ClassView> classes) {
    public record ClassView(String id, String name, String description, String icon, String gunId,
                            double health, double speed, int reserveAmmo) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id); buffer.writeUtf(name); buffer.writeUtf(description); buffer.writeUtf(icon);
            buffer.writeUtf(gunId); buffer.writeDouble(health); buffer.writeDouble(speed); buffer.writeVarInt(reserveAmmo);
        }
        static ClassView decode(FriendlyByteBuf buffer) {
            return new ClassView(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
        }
    }

    public int score(TeamSide team) {
        return switch (team) { case RED -> redScore; case BLUE -> blueScore; case YELLOW -> yellowScore;
            case GREEN -> greenScore; case NONE -> 0; };
    }
    public int players(TeamSide team) {
        return switch (team) { case RED -> redPlayers; case BLUE -> bluePlayers; case YELLOW -> yellowPlayers;
            case GREEN -> greenPlayers; case NONE -> 0; };
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(phase); buffer.writeEnum(side);
        buffer.writeVarInt(redScore); buffer.writeVarInt(blueScore); buffer.writeVarInt(yellowScore); buffer.writeVarInt(greenScore);
        buffer.writeVarInt(scoreLimit); buffer.writeVarInt(remainingSeconds);
        buffer.writeVarInt(redPlayers); buffer.writeVarInt(bluePlayers); buffer.writeVarInt(yellowPlayers); buffer.writeVarInt(greenPlayers);
        buffer.writeBoolean(currentClass != null); if (currentClass != null) buffer.writeUtf(currentClass);
        buffer.writeBoolean(pendingClass != null); if (pendingClass != null) buffer.writeUtf(pendingClass);
        buffer.writeBoolean(participating); buffer.writeBoolean(queued);
        buffer.writeVarInt(classes.size()); classes.forEach(view -> view.encode(buffer));
    }

    public static MatchSnapshot decode(FriendlyByteBuf buffer) {
        MatchPhase phase = buffer.readEnum(MatchPhase.class); TeamSide side = buffer.readEnum(TeamSide.class);
        int redScore = buffer.readVarInt(), blueScore = buffer.readVarInt(), yellowScore = buffer.readVarInt(), greenScore = buffer.readVarInt();
        int scoreLimit = buffer.readVarInt(), remaining = buffer.readVarInt();
        int redPlayers = buffer.readVarInt(), bluePlayers = buffer.readVarInt(), yellowPlayers = buffer.readVarInt(), greenPlayers = buffer.readVarInt();
        String current = buffer.readBoolean() ? buffer.readUtf() : null;
        String pending = buffer.readBoolean() ? buffer.readUtf() : null;
        boolean participating = buffer.readBoolean(), queued = buffer.readBoolean();
        int size = buffer.readVarInt(); List<ClassView> classes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) classes.add(ClassView.decode(buffer));
        return new MatchSnapshot(phase, side, redScore, blueScore, yellowScore, greenScore, scoreLimit, remaining,
                redPlayers, bluePlayers, yellowPlayers, greenPlayers, current, pending, participating, queued, List.copyOf(classes));
    }
}

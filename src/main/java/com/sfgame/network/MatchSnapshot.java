package com.sfgame.network;

import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MatchSnapshot(MatchPhase phase, TeamSide side, int redScore, int blueScore, int scoreLimit,
                            int remainingSeconds, int redPlayers, int bluePlayers, String currentClass,
                            String pendingClass, boolean participating, boolean queued, List<ClassView> classes) {
    public record ClassView(String id, String name, String description, String icon, String gunId,
                            double health, double speed, int reserveAmmo) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id);
            buffer.writeUtf(name);
            buffer.writeUtf(description);
            buffer.writeUtf(icon);
            buffer.writeUtf(gunId);
            buffer.writeDouble(health);
            buffer.writeDouble(speed);
            buffer.writeVarInt(reserveAmmo);
        }

        static ClassView decode(FriendlyByteBuf buffer) {
            return new ClassView(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(phase);
        buffer.writeEnum(side);
        buffer.writeVarInt(redScore);
        buffer.writeVarInt(blueScore);
        buffer.writeVarInt(scoreLimit);
        buffer.writeVarInt(remainingSeconds);
        buffer.writeVarInt(redPlayers);
        buffer.writeVarInt(bluePlayers);
        buffer.writeBoolean(currentClass != null);
        if (currentClass != null) buffer.writeUtf(currentClass);
        buffer.writeBoolean(pendingClass != null);
        if (pendingClass != null) buffer.writeUtf(pendingClass);
        buffer.writeBoolean(participating);
        buffer.writeBoolean(queued);
        buffer.writeVarInt(classes.size());
        classes.forEach(view -> view.encode(buffer));
    }

    public static MatchSnapshot decode(FriendlyByteBuf buffer) {
        MatchPhase phase = buffer.readEnum(MatchPhase.class);
        TeamSide side = buffer.readEnum(TeamSide.class);
        int redScore = buffer.readVarInt();
        int blueScore = buffer.readVarInt();
        int scoreLimit = buffer.readVarInt();
        int remaining = buffer.readVarInt();
        int redPlayers = buffer.readVarInt();
        int bluePlayers = buffer.readVarInt();
        String current = buffer.readBoolean() ? buffer.readUtf() : null;
        String pending = buffer.readBoolean() ? buffer.readUtf() : null;
        boolean participating = buffer.readBoolean();
        boolean queued = buffer.readBoolean();
        int size = buffer.readVarInt();
        List<ClassView> classes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) classes.add(ClassView.decode(buffer));
        return new MatchSnapshot(phase, side, redScore, blueScore, scoreLimit, remaining, redPlayers, bluePlayers,
                current, pending, participating, queued, List.copyOf(classes));
    }
}


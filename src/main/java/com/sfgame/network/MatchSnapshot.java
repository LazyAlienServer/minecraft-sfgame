package com.sfgame.network;

import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MatchSnapshot(String modeId, MatchPhase phase, TeamSide side, int redScore, int blueScore, int yellowScore,
                            int greenScore, int scoreLimit, int remainingSeconds, int redPlayers, int bluePlayers,
                            int yellowPlayers, int greenPlayers, String currentClass, String pendingClass,
                            boolean participating, boolean queued, List<ClassView> classes,
                            String breakthroughVariant, TeamSide attacker, TeamSide defender, int attackerTickets,
                            int leg, int sector, int sectorCount, String modeSubState, String captainId,
                            String captainName, int electionSeconds, boolean captain,
                            String currentCaptainClass, String pendingCaptainClass, List<ClassView> captainClasses,
                            List<CaptainCandidate> captainCandidates) {
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

    public record CaptainCandidate(String uuid, String name) {
        void encode(FriendlyByteBuf buffer) { buffer.writeUtf(uuid); buffer.writeUtf(name); }
        static CaptainCandidate decode(FriendlyByteBuf buffer) { return new CaptainCandidate(buffer.readUtf(), buffer.readUtf()); }
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
        buffer.writeUtf(modeId); buffer.writeEnum(phase); buffer.writeEnum(side);
        buffer.writeVarInt(redScore); buffer.writeVarInt(blueScore); buffer.writeVarInt(yellowScore); buffer.writeVarInt(greenScore);
        buffer.writeVarInt(scoreLimit); buffer.writeVarInt(remainingSeconds);
        buffer.writeVarInt(redPlayers); buffer.writeVarInt(bluePlayers); buffer.writeVarInt(yellowPlayers); buffer.writeVarInt(greenPlayers);
        writeNullable(buffer, currentClass); writeNullable(buffer, pendingClass);
        buffer.writeBoolean(participating); buffer.writeBoolean(queued);
        writeClasses(buffer, classes);
        buffer.writeUtf(breakthroughVariant); buffer.writeEnum(attacker); buffer.writeEnum(defender);
        buffer.writeVarInt(attackerTickets); buffer.writeVarInt(leg); buffer.writeVarInt(sector); buffer.writeVarInt(sectorCount);
        buffer.writeUtf(modeSubState); writeNullable(buffer, captainId); writeNullable(buffer, captainName);
        buffer.writeVarInt(electionSeconds); buffer.writeBoolean(captain);
        writeNullable(buffer, currentCaptainClass); writeNullable(buffer, pendingCaptainClass);
        writeClasses(buffer, captainClasses);
        buffer.writeVarInt(captainCandidates.size()); captainCandidates.forEach(candidate -> candidate.encode(buffer));
    }

    public static MatchSnapshot decode(FriendlyByteBuf buffer) {
        String modeId = buffer.readUtf(); MatchPhase phase = buffer.readEnum(MatchPhase.class); TeamSide side = buffer.readEnum(TeamSide.class);
        int redScore = buffer.readVarInt(), blueScore = buffer.readVarInt(), yellowScore = buffer.readVarInt(), greenScore = buffer.readVarInt();
        int scoreLimit = buffer.readVarInt(), remaining = buffer.readVarInt();
        int redPlayers = buffer.readVarInt(), bluePlayers = buffer.readVarInt(), yellowPlayers = buffer.readVarInt(), greenPlayers = buffer.readVarInt();
        String current = readNullable(buffer), pending = readNullable(buffer);
        boolean participating = buffer.readBoolean(), queued = buffer.readBoolean();
        List<ClassView> classes = readClasses(buffer);
        String variant = buffer.readUtf(); TeamSide attacker = buffer.readEnum(TeamSide.class), defender = buffer.readEnum(TeamSide.class);
        int tickets = buffer.readVarInt(), leg = buffer.readVarInt(), sector = buffer.readVarInt(), sectors = buffer.readVarInt();
        String subState = buffer.readUtf(), captainId = readNullable(buffer), captainName = readNullable(buffer);
        int election = buffer.readVarInt(); boolean isCaptain = buffer.readBoolean();
        String currentCaptain = readNullable(buffer), pendingCaptain = readNullable(buffer);
        List<ClassView> captainClasses = readClasses(buffer);
        int candidateCount = buffer.readVarInt(); List<CaptainCandidate> candidates = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) candidates.add(CaptainCandidate.decode(buffer));
        return new MatchSnapshot(modeId, phase, side, redScore, blueScore, yellowScore, greenScore, scoreLimit, remaining,
                redPlayers, bluePlayers, yellowPlayers, greenPlayers, current, pending, participating, queued, classes,
                variant, attacker, defender, tickets, leg, sector, sectors, subState, captainId, captainName, election,
                isCaptain, currentCaptain, pendingCaptain, captainClasses, List.copyOf(candidates));
    }

    private static void writeNullable(FriendlyByteBuf buffer, String value) {
        buffer.writeBoolean(value != null); if (value != null) buffer.writeUtf(value);
    }
    private static String readNullable(FriendlyByteBuf buffer) { return buffer.readBoolean() ? buffer.readUtf() : null; }
    private static void writeClasses(FriendlyByteBuf buffer, List<ClassView> views) {
        buffer.writeVarInt(views.size()); views.forEach(view -> view.encode(buffer));
    }
    private static List<ClassView> readClasses(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt(); List<ClassView> views = new ArrayList<>(size);
        for (int i = 0; i < size; i++) views.add(ClassView.decode(buffer));
        return List.copyOf(views);
    }
}

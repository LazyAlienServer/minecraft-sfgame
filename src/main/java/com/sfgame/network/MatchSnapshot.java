package com.sfgame.network;

import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record MatchSnapshot(String modeId, String mapName, MatchPhase phase, TeamSide side, int redScore, int blueScore, int yellowScore,
                            int greenScore, int scoreLimit, int remainingSeconds, boolean showUnlimitedTime,
                            int redPlayers, int bluePlayers,
                            int yellowPlayers, int greenPlayers, String currentClass, String pendingClass,
                            boolean participating, boolean queued, List<ClassView> classes,
                            String breakthroughVariant, TeamSide attacker, TeamSide defender, int attackerTickets,
                            boolean showUnlimitedTickets, int attackRoundsRemaining, int leg, int sector, int sectorCount,
                            String modeSubState, String captainId,
                            String captainName, int electionSeconds, boolean captain,
                            String currentCaptainClass, String pendingCaptainClass, List<ClassView> captainClasses,
                            List<CaptainCandidate> captainCandidates, boolean awaitingRespawnSelection,
                            List<RespawnOption> respawnOptions, String ctfVariant, String ctfCarrierRestriction,
                            boolean economyEnabled, int currency, boolean devMode,
                            List<CtfFlagView> ctfFlags, List<ShopView> shopItems,
                            List<SupplyView> supplyItems) {
    public record ClassView(String id, String name, String description, String icon, String iconRender,
                            String iconTexture, String gunId, double health, double speed, int reserveAmmo) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id); buffer.writeUtf(name); buffer.writeUtf(description); buffer.writeUtf(icon);
            buffer.writeUtf(iconRender); buffer.writeUtf(iconTexture); buffer.writeUtf(gunId);
            buffer.writeDouble(health); buffer.writeDouble(speed); buffer.writeVarInt(reserveAmmo);
        }
        static ClassView decode(FriendlyByteBuf buffer) {
            return new ClassView(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt());
        }
    }

    public record CaptainCandidate(String uuid, String name) {
        void encode(FriendlyByteBuf buffer) { buffer.writeUtf(uuid); buffer.writeUtf(name); }
        static CaptainCandidate decode(FriendlyByteBuf buffer) { return new CaptainCandidate(buffer.readUtf(), buffer.readUtf()); }
    }

    public record RespawnOption(String id, String pointId) {
        void encode(FriendlyByteBuf buffer) { buffer.writeUtf(id); buffer.writeUtf(pointId); }
        static RespawnOption decode(FriendlyByteBuf buffer) { return new RespawnOption(buffer.readUtf(), buffer.readUtf()); }
    }

    public record CtfFlagView(String id, TeamSide owner, String state, String carrier,
                              boolean unlocked, TeamSide depotTeam) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id); buffer.writeEnum(owner); buffer.writeUtf(state);
            writeNullable(buffer, carrier); buffer.writeBoolean(unlocked); buffer.writeEnum(depotTeam);
        }
        static CtfFlagView decode(FriendlyByteBuf buffer) {
            return new CtfFlagView(buffer.readUtf(), buffer.readEnum(TeamSide.class), buffer.readUtf(),
                    readNullable(buffer), buffer.readBoolean(), buffer.readEnum(TeamSide.class));
        }
    }

    public record ShopView(String id, String name, String icon, int price) {
        void encode(FriendlyByteBuf buffer) { buffer.writeUtf(id); buffer.writeUtf(name); buffer.writeUtf(icon); buffer.writeVarInt(price); }
        static ShopView decode(FriendlyByteBuf buffer) { return new ShopView(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readVarInt()); }
    }
    public record SupplyView(String id, String type, String name, String icon, int quantity) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(id); buffer.writeUtf(type); buffer.writeUtf(name); buffer.writeUtf(icon);
            buffer.writeVarInt(quantity);
        }
        static SupplyView decode(FriendlyByteBuf buffer) {
            return new SupplyView(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readVarInt());
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
        buffer.writeUtf(modeId); buffer.writeUtf(mapName); buffer.writeEnum(phase); buffer.writeEnum(side);
        buffer.writeVarInt(redScore); buffer.writeVarInt(blueScore); buffer.writeVarInt(yellowScore); buffer.writeVarInt(greenScore);
        buffer.writeVarInt(scoreLimit); buffer.writeVarInt(remainingSeconds); buffer.writeBoolean(showUnlimitedTime);
        buffer.writeVarInt(redPlayers); buffer.writeVarInt(bluePlayers); buffer.writeVarInt(yellowPlayers); buffer.writeVarInt(greenPlayers);
        writeNullable(buffer, currentClass); writeNullable(buffer, pendingClass);
        buffer.writeBoolean(participating); buffer.writeBoolean(queued);
        writeClasses(buffer, classes);
        buffer.writeUtf(breakthroughVariant); buffer.writeEnum(attacker); buffer.writeEnum(defender);
        buffer.writeVarInt(attackerTickets); buffer.writeBoolean(showUnlimitedTickets);
        buffer.writeVarInt(attackRoundsRemaining);
        buffer.writeVarInt(leg); buffer.writeVarInt(sector); buffer.writeVarInt(sectorCount);
        buffer.writeUtf(modeSubState); writeNullable(buffer, captainId); writeNullable(buffer, captainName);
        buffer.writeVarInt(electionSeconds); buffer.writeBoolean(captain);
        writeNullable(buffer, currentCaptainClass); writeNullable(buffer, pendingCaptainClass);
        writeClasses(buffer, captainClasses);
        buffer.writeVarInt(captainCandidates.size()); captainCandidates.forEach(candidate -> candidate.encode(buffer));
        buffer.writeBoolean(awaitingRespawnSelection);
        buffer.writeVarInt(respawnOptions.size()); respawnOptions.forEach(option -> option.encode(buffer));
        writeNullable(buffer, ctfVariant); writeNullable(buffer, ctfCarrierRestriction);
        buffer.writeBoolean(economyEnabled); buffer.writeVarInt(currency);
        buffer.writeBoolean(devMode);
        buffer.writeVarInt(ctfFlags.size()); ctfFlags.forEach(flag -> flag.encode(buffer));
        buffer.writeVarInt(shopItems.size()); shopItems.forEach(item -> item.encode(buffer));
        buffer.writeVarInt(supplyItems.size()); supplyItems.forEach(item -> item.encode(buffer));
    }

    public static MatchSnapshot decode(FriendlyByteBuf buffer) {
        String modeId = buffer.readUtf(); String mapName = buffer.readUtf();
        MatchPhase phase = buffer.readEnum(MatchPhase.class); TeamSide side = buffer.readEnum(TeamSide.class);
        int redScore = buffer.readVarInt(), blueScore = buffer.readVarInt(), yellowScore = buffer.readVarInt(), greenScore = buffer.readVarInt();
        int scoreLimit = buffer.readVarInt(), remaining = buffer.readVarInt(); boolean showUnlimitedTime = buffer.readBoolean();
        int redPlayers = buffer.readVarInt(), bluePlayers = buffer.readVarInt(), yellowPlayers = buffer.readVarInt(), greenPlayers = buffer.readVarInt();
        String current = readNullable(buffer), pending = readNullable(buffer);
        boolean participating = buffer.readBoolean(), queued = buffer.readBoolean();
        List<ClassView> classes = readClasses(buffer);
        String variant = buffer.readUtf(); TeamSide attacker = buffer.readEnum(TeamSide.class), defender = buffer.readEnum(TeamSide.class);
        int tickets = buffer.readVarInt(); boolean showUnlimitedTickets = buffer.readBoolean();
        int attackRounds = buffer.readVarInt();
        int leg = buffer.readVarInt(), sector = buffer.readVarInt(), sectors = buffer.readVarInt();
        String subState = buffer.readUtf(), captainId = readNullable(buffer), captainName = readNullable(buffer);
        int election = buffer.readVarInt(); boolean isCaptain = buffer.readBoolean();
        String currentCaptain = readNullable(buffer), pendingCaptain = readNullable(buffer);
        List<ClassView> captainClasses = readClasses(buffer);
        int candidateCount = buffer.readVarInt(); List<CaptainCandidate> candidates = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) candidates.add(CaptainCandidate.decode(buffer));
        boolean awaitingRespawn = buffer.readBoolean();
        int optionCount = buffer.readVarInt(); List<RespawnOption> options = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) options.add(RespawnOption.decode(buffer));
        String ctfVariant = readNullable(buffer), ctfRestriction = readNullable(buffer);
        boolean economyEnabled = buffer.readBoolean();
        int currency = buffer.readVarInt();
        boolean devMode = buffer.readBoolean();
        int flagCount = buffer.readVarInt(); List<CtfFlagView> ctfFlags = new ArrayList<>(flagCount);
        for (int i = 0; i < flagCount; i++) ctfFlags.add(CtfFlagView.decode(buffer));
        int shopCount = buffer.readVarInt(); List<ShopView> shopItems = new ArrayList<>(shopCount);
        for (int i = 0; i < shopCount; i++) shopItems.add(ShopView.decode(buffer));
        int supplyCount = buffer.readVarInt(); List<SupplyView> supplyItems = new ArrayList<>(supplyCount);
        for (int i = 0; i < supplyCount; i++) supplyItems.add(SupplyView.decode(buffer));
        return new MatchSnapshot(modeId, mapName, phase, side, redScore, blueScore, yellowScore, greenScore, scoreLimit, remaining,
                showUnlimitedTime, redPlayers, bluePlayers, yellowPlayers, greenPlayers, current, pending,
                participating, queued, classes, variant, attacker, defender, tickets, showUnlimitedTickets,
                attackRounds, leg, sector, sectors, subState, captainId, captainName, election,
                isCaptain, currentCaptain, pendingCaptain, captainClasses, List.copyOf(candidates), awaitingRespawn,
                List.copyOf(options), ctfVariant, ctfRestriction, economyEnabled, currency, devMode,
                List.copyOf(ctfFlags), List.copyOf(shopItems), List.copyOf(supplyItems));
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

package com.sfgame.network;

import com.sfgame.game.TeamSide;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SquadSnapshot(TeamSide viewerSide, int factionPlayerCount, int maxMembers,
                            Integer currentSquadIndex, float beaconHealth, float beaconMaxHealth,
                            List<SquadView> squads) {
    public record SquadView(int index, int memberCount, List<MemberView> members) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(index);
            buffer.writeVarInt(memberCount);
            buffer.writeVarInt(members.size());
            members.forEach(member -> member.encode(buffer));
        }

        static SquadView decode(FriendlyByteBuf buffer) {
            int index = buffer.readVarInt();
            int memberCount = buffer.readVarInt();
            int size = buffer.readVarInt();
            List<MemberView> members = new ArrayList<>(size);
            for (int i = 0; i < size; i++) members.add(MemberView.decode(buffer));
            return new SquadView(index, memberCount, List.copyOf(members));
        }
    }

    public record MemberView(UUID uuid, String name, boolean online, boolean participating, boolean respawning) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(uuid);
            buffer.writeUtf(name, 64);
            buffer.writeBoolean(online);
            buffer.writeBoolean(participating);
            buffer.writeBoolean(respawning);
        }

        static MemberView decode(FriendlyByteBuf buffer) {
            return new MemberView(buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean());
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(viewerSide);
        buffer.writeVarInt(factionPlayerCount);
        buffer.writeVarInt(maxMembers);
        buffer.writeBoolean(currentSquadIndex != null);
        if (currentSquadIndex != null) buffer.writeVarInt(currentSquadIndex);
        buffer.writeFloat(beaconHealth);
        buffer.writeFloat(beaconMaxHealth);
        buffer.writeVarInt(squads.size());
        squads.forEach(squad -> squad.encode(buffer));
    }

    public static SquadSnapshot decode(FriendlyByteBuf buffer) {
        TeamSide viewerSide = buffer.readEnum(TeamSide.class);
        int factionPlayerCount = buffer.readVarInt();
        int maxMembers = buffer.readVarInt();
        Integer currentSquadIndex = buffer.readBoolean() ? buffer.readVarInt() : null;
        float beaconHealth = buffer.readFloat();
        float beaconMaxHealth = buffer.readFloat();
        int size = buffer.readVarInt();
        List<SquadView> squads = new ArrayList<>(size);
        for (int i = 0; i < size; i++) squads.add(SquadView.decode(buffer));
        return new SquadSnapshot(viewerSide, factionPlayerCount, maxMembers, currentSquadIndex,
                beaconHealth, beaconMaxHealth, List.copyOf(squads));
    }

    public static SquadSnapshot empty(TeamSide side) {
        return new SquadSnapshot(side, 0, 0, null, 0.0F, 0.0F, List.of());
    }
}

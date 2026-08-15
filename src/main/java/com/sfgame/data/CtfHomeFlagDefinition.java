package com.sfgame.data;

import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public final class CtfHomeFlagDefinition {
    private final TeamSide team;
    @Nullable private ArenaPosition flagPosition;
    @Nullable private CaptureRegion captureRegion;
    @Nullable private ArenaPosition depotPosition;

    public CtfHomeFlagDefinition(TeamSide team) {
        if (team == null || team == TeamSide.NONE) throw new IllegalArgumentException("Home flag team must be playable");
        this.team = team;
    }

    public TeamSide team() { return team; }
    @Nullable public ArenaPosition flagPosition() { return flagPosition; }
    @Nullable public CaptureRegion captureRegion() { return captureRegion; }
    @Nullable public ArenaPosition depotPosition() { return depotPosition; }
    public void flagPosition(ArenaPosition value) { flagPosition = value; }
    public void captureRegion(CaptureRegion value) { captureRegion = value; }
    public void depotPosition(ArenaPosition value) { depotPosition = value; }

    public boolean configured() { return flagPosition != null && captureRegion != null && depotPosition != null; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Team", team.id());
        if (flagPosition != null) tag.put("FlagPosition", flagPosition.save());
        if (captureRegion != null) tag.put("CaptureRegion", captureRegion.save());
        if (depotPosition != null) tag.put("DepotPosition", depotPosition.save());
        return tag;
    }

    public static CtfHomeFlagDefinition load(CompoundTag tag) {
        TeamSide team = TeamSide.fromId(tag.getString("Team"));
        CtfHomeFlagDefinition definition = new CtfHomeFlagDefinition(team);
        if (tag.contains("FlagPosition")) definition.flagPosition(ArenaPosition.load(tag.getCompound("FlagPosition")));
        if (tag.contains("CaptureRegion")) definition.captureRegion(CaptureRegion.load(tag.getCompound("CaptureRegion")));
        if (tag.contains("DepotPosition")) definition.depotPosition(ArenaPosition.load(tag.getCompound("DepotPosition")));
        return definition;
    }
}

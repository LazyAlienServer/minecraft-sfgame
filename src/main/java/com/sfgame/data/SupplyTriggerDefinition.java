package com.sfgame.data;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/** Immutable publication trigger attached to a map supply offer. */
public record SupplyTriggerDefinition(String id, String event, String offerId, String target, int quantity,
                                      int stage, String sectorId, int atSeconds, int repeatSeconds, String pointId) {
    public static final String MATCH_TIME = "match_time";
    public static final String BREAKTHROUGH_STAGE = "breakthrough_stage";
    public static final String BREAKTHROUGH_SECTOR = "breakthrough_sector";
    public static final String CTF_CAPTURE = "ctf_capture";
    public static final String DOMINATION_CAPTURE = "domination_capture";

    public SupplyTriggerDefinition {
        id = SFGameId.normalize(id);
        event = normalize(event);
        offerId = SFGameId.normalize(offerId);
        target = normalize(target);
        sectorId = sectorId == null || sectorId.isBlank() ? "" : SFGameId.normalize(sectorId);
        pointId = pointId == null || pointId.isBlank() ? "" : SFGameId.normalize(pointId);
        if (quantity < 1 || quantity > 100_000) throw new IllegalArgumentException(id + ": quantity must be 1..100000");
        switch (event) {
            case MATCH_TIME -> {
                if (atSeconds < 0 || repeatSeconds < 0) throw new IllegalArgumentException(id + ": invalid match time");
            }
            case BREAKTHROUGH_STAGE -> {
                if (stage < 1) throw new IllegalArgumentException(id + ": stage must be at least 1");
            }
            case BREAKTHROUGH_SECTOR -> {
                if (sectorId.isEmpty()) throw new IllegalArgumentException(id + ": breakthrough_sector needs sectorId");
            }
            case CTF_CAPTURE, DOMINATION_CAPTURE -> { }
            default -> throw new IllegalArgumentException(id + ": unknown supply event " + event);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Event", event);
        tag.putString("OfferId", offerId);
        tag.putString("Target", target);
        tag.putInt("Quantity", quantity);
        if (stage > 0) tag.putInt("Stage", stage);
        if (!sectorId.isEmpty()) tag.putString("SectorId", sectorId);
        if (MATCH_TIME.equals(event)) {
            tag.putInt("AtSeconds", atSeconds);
            tag.putInt("RepeatSeconds", repeatSeconds);
        }
        if (!pointId.isEmpty()) tag.putString("PointId", pointId);
        return tag;
    }

    static SupplyTriggerDefinition load(CompoundTag tag) {
        return new SupplyTriggerDefinition(tag.getString("Id"), tag.getString("Event"), tag.getString("OfferId"),
                tag.getString("Target"), tag.getInt("Quantity"), tag.getInt("Stage"), tag.getString("SectorId"),
                tag.getInt("AtSeconds"), tag.getInt("RepeatSeconds"), tag.getString("PointId"));
    }
}

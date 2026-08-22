package com.sfgame.data;

import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CaptureTheFlagMapConfig {
    public static final int MAX_FORWARD_FLAGS = 16;
    private CtfVariant variant = CtfVariant.CLASSIC;
    private CarrierRestriction carrierRestriction = CarrierRestriction.NORMAL;
    private TeamSide attacker = TeamSide.RED;
    private TeamSide defender = TeamSide.BLUE;
    private final Map<TeamSide, CtfHomeFlagDefinition> homes = new EnumMap<>(TeamSide.class);
    private final List<CtfForwardFlagDefinition> forwardFlags = new ArrayList<>();

    public CtfVariant variant() { return variant; }
    public void variant(CtfVariant value) { variant = value == null ? CtfVariant.CLASSIC : value; }
    public CarrierRestriction carrierRestriction() { return carrierRestriction; }
    public void carrierRestriction(CarrierRestriction value) { carrierRestriction = value == null ? CarrierRestriction.NORMAL : value; }
    public TeamSide attacker() { return attacker; }
    public TeamSide defender() { return defender; }
    public void roles(TeamSide attack, TeamSide defend) {
        if (attack == null || defend == null || attack == TeamSide.NONE || defend == TeamSide.NONE || attack == defend) {
            throw new IllegalArgumentException("CTF attacker and defender must be different playable teams");
        }
        attacker = attack; defender = defend;
    }
    public CtfHomeFlagDefinition home(TeamSide side) { return homes.computeIfAbsent(side, CtfHomeFlagDefinition::new); }
    public Optional<CtfHomeFlagDefinition> homeOptional(TeamSide side) { return Optional.ofNullable(homes.get(side)); }
    public List<CtfHomeFlagDefinition> homes() { return homes.values().stream().toList(); }
    public List<CtfForwardFlagDefinition> forwardFlags() {
        return forwardFlags.stream().sorted(Comparator.comparingInt(CtfForwardFlagDefinition::order)
                .thenComparing(CtfForwardFlagDefinition::id)).toList();
    }
    public Optional<CtfForwardFlagDefinition> forward(String id) {
        String normalized = SFGameId.normalize(id);
        return forwardFlags.stream().filter(flag -> flag.id().equals(normalized)).findFirst();
    }
    public void addForward(CtfForwardFlagDefinition flag) {
        if (forwardFlags.size() >= MAX_FORWARD_FLAGS) throw new IllegalArgumentException("A CTF map can have at most 16 forward flags");
        if (forward(flag.id()).isPresent()) throw new IllegalArgumentException("Duplicate forward flag id: " + flag.id());
        validateNoOverlap(flag, null); forwardFlags.add(flag);
    }
    public void replaceForward(String id, CtfForwardFlagDefinition replacement) {
        int index = indexOf(id); validateNoOverlap(replacement, replacement.id()); forwardFlags.set(index, replacement);
    }
    public boolean removeForward(String id) {
        String normalized = SFGameId.normalize(id); return forwardFlags.removeIf(flag -> flag.id().equals(normalized));
    }
    public void clearForward() { forwardFlags.clear(); }

    public void validateHomeCaptureRegion(TeamSide side, CaptureRegion candidate) {
        if (candidate == null) return;
        for (CtfForwardFlagDefinition flag : forwardFlags) {
            if (candidate.overlaps(flag.region())) throw new IllegalArgumentException("Home capture region overlaps forward flag " + flag.id());
        }
        for (CtfHomeFlagDefinition home : homes.values()) {
            if (home.team() != side && home.captureRegion() != null && candidate.overlaps(home.captureRegion())) {
                throw new IllegalArgumentException("Home capture regions overlap: " + side.id() + " and " + home.team().id());
            }
        }
    }

    public List<TeamSide> teams(List<TeamSide> spawnedTeams) {
        return teams(spawnedTeams, variant, attacker, defender);
    }

    public List<TeamSide> teams(List<TeamSide> spawnedTeams, CtfVariant selectedVariant,
                                TeamSide selectedAttacker, TeamSide selectedDefender) {
        if (selectedVariant == CtfVariant.ASSAULT) return List.of(selectedAttacker, selectedDefender);
        return spawnedTeams.stream().filter(side -> side != TeamSide.NONE).toList();
    }

    public List<String> validate(List<TeamSide> spawnedTeams) {
        return validate(spawnedTeams, variant, attacker, defender);
    }

    /** Base map topology independent of the rule-selected CTF variant. */
    public boolean topologyConfigured(List<TeamSide> spawnedTeams) {
        List<TeamSide> enabled = spawnedTeams.stream().filter(side -> side != TeamSide.NONE).toList();
        return enabled.size() >= 2 && enabled.size() <= 4
                && enabled.stream().allMatch(side -> homes.containsKey(side) && homes.get(side).configured());
    }

    public List<String> validate(List<TeamSide> spawnedTeams, CtfVariant selectedVariant,
                                 TeamSide selectedAttacker, TeamSide selectedDefender) {
        List<String> errors = new ArrayList<>();
        List<TeamSide> enabled = teams(spawnedTeams, selectedVariant, selectedAttacker, selectedDefender);
        if (selectedVariant == CtfVariant.ASSAULT) {
            if (selectedAttacker == TeamSide.NONE || selectedDefender == TeamSide.NONE || selectedAttacker == selectedDefender) errors.add("Assault needs different attacker and defender teams");
            if (spawnedTeams.stream().filter(side -> side != TeamSide.NONE).count() != 2) errors.add("Assault CTF requires exactly two spawned teams");
            if (!spawnedTeams.contains(selectedAttacker)) errors.add("Assault attacker needs a configured spawn");
            if (!spawnedTeams.contains(selectedDefender)) errors.add("Assault defender needs a configured spawn");
        } else if (enabled.size() < 2 || enabled.size() > 4) errors.add("CTF needs between two and four enabled teams");
        for (TeamSide side : enabled) {
            CtfHomeFlagDefinition home = homes.get(side);
            if (home == null || !home.configured()) errors.add("Missing home flag, capture region or depot for " + side.id());
        }
        if (selectedVariant == CtfVariant.TERRITORY) {
            if (forwardFlags.isEmpty()) errors.add("Territory CTF needs at least one forward flag");
            for (CtfForwardFlagDefinition flag : forwardFlags) if (!enabled.contains(flag.owner())) {
                errors.add("Forward flag " + flag.id() + " belongs to a disabled team");
            }
        }
        if (forwardFlags.size() > MAX_FORWARD_FLAGS) errors.add("CTF has more than 16 forward flags");
        List<RegionEntry> regions = new ArrayList<>();
        for (TeamSide side : enabled) {
            CtfHomeFlagDefinition home = homes.get(side);
            if (home != null && home.captureRegion() != null) regions.add(new RegionEntry("home " + side.id(), home.captureRegion()));
        }
        for (CtfForwardFlagDefinition flag : forwardFlags) regions.add(new RegionEntry("forward " + flag.id(), flag.region()));
        for (int i = 0; i < regions.size(); i++) for (int j = i + 1; j < regions.size(); j++) {
            if (regions.get(i).region.overlaps(regions.get(j).region)) {
                errors.add("CTF target regions overlap: " + regions.get(i).name + " and " + regions.get(j).name);
            }
        }
        return errors;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Variant", variant.id()); tag.putString("CarrierRestriction", carrierRestriction.id());
        tag.putString("Attacker", attacker.id()); tag.putString("Defender", defender.id());
        ListTag homeList = new ListTag(); homes.values().forEach(home -> homeList.add(home.save())); tag.put("Homes", homeList);
        ListTag forwardList = new ListTag(); forwardFlags().forEach(flag -> forwardList.add(flag.save())); tag.put("ForwardFlags", forwardList);
        return tag;
    }

    public static CaptureTheFlagMapConfig load(CompoundTag tag) {
        CaptureTheFlagMapConfig config = new CaptureTheFlagMapConfig();
        config.variant = CtfVariant.fromId(tag.getString("Variant"));
        config.carrierRestriction = CarrierRestriction.fromId(tag.getString("CarrierRestriction"));
        TeamSide attack = TeamSide.fromId(tag.getString("Attacker")), defend = TeamSide.fromId(tag.getString("Defender"));
        if (attack != TeamSide.NONE && defend != TeamSide.NONE && attack != defend) config.roles(attack, defend);
        if (tag.contains("Homes", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Homes", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try { CtfHomeFlagDefinition home = CtfHomeFlagDefinition.load(list.getCompound(i)); config.homes.put(home.team(), home); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        if (tag.contains("ForwardFlags", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ForwardFlags", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_FORWARD_FLAGS, list.size()); i++) {
                try { config.addForward(CtfForwardFlagDefinition.load(list.getCompound(i))); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        return config;
    }

    private int indexOf(String id) {
        String normalized = SFGameId.normalize(id);
        for (int i = 0; i < forwardFlags.size(); i++) if (forwardFlags.get(i).id().equals(normalized)) return i;
        throw new IllegalArgumentException("Unknown forward flag: " + id);
    }
    private void validateNoOverlap(CtfForwardFlagDefinition candidate, @Nullable String ignoredId) {
        for (CtfForwardFlagDefinition flag : forwardFlags) {
            if (flag.id().equals(ignoredId)) continue;
            if (candidate.region().overlaps(flag.region())) throw new IllegalArgumentException("Forward flag overlaps " + flag.id());
        }
        for (CtfHomeFlagDefinition home : homes.values()) {
            if (home.captureRegion() != null && candidate.region().overlaps(home.captureRegion())) {
                throw new IllegalArgumentException("Forward flag overlaps home capture region for " + home.team().id());
            }
        }
    }

    private record RegionEntry(String name, CaptureRegion region) { }
}

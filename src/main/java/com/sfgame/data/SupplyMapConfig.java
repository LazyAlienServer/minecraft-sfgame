package com.sfgame.data;

import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

/** Map-owned supply offers and their publication triggers. */
public final class SupplyMapConfig {
    private final Map<String, SupplyOfferDefinition> offers = new LinkedHashMap<>();
    private final Map<String, SupplyTriggerDefinition> triggers = new LinkedHashMap<>();

    public List<SupplyOfferDefinition> offers() { return List.copyOf(offers.values()); }
    public List<SupplyTriggerDefinition> triggers() { return List.copyOf(triggers.values()); }
    public Optional<SupplyOfferDefinition> offer(String id) {
        if (!SFGameId.isValid(id)) return Optional.empty();
        return Optional.ofNullable(offers.get(SFGameId.normalize(id)));
    }

    public void addOffer(SupplyOfferDefinition offer) {
        if (offers.putIfAbsent(offer.id(), offer) != null) {
            throw new IllegalArgumentException("Duplicate supply offer id: " + offer.id());
        }
    }

    public void addTrigger(SupplyTriggerDefinition trigger) {
        if (triggers.putIfAbsent(trigger.id(), trigger) != null) {
            throw new IllegalArgumentException("Duplicate supply trigger id: " + trigger.id());
        }
    }

    public void clear() {
        offers.clear();
        triggers.clear();
    }

    public boolean configured() { return !offers.isEmpty() || !triggers.isEmpty(); }

    public List<String> validate(String modeId, List<TeamSide> enabledTeams, TeamSide attacker, TeamSide defender,
                                 BiPredicate<TeamSide, String> eliteContains) {
        List<String> errors = new ArrayList<>();
        for (SupplyTriggerDefinition trigger : triggers.values()) {
            SupplyOfferDefinition offer = offers.get(trigger.offerId());
            if (offer == null) {
                errors.add(trigger.id() + ": missing supply offer " + trigger.offerId());
                continue;
            }
            if (!eventMatchesMode(trigger.event(), modeId)) {
                errors.add(trigger.id() + ": event " + trigger.event() + " is unavailable in " + modeId);
                continue;
            }
            List<TeamSide> targets = resolveTargets(trigger, enabledTeams, attacker, defender, errors);
            if (SupplyOfferDefinition.ELITE_CLASS.equals(offer.type()) && eliteContains != null) {
                for (TeamSide side : targets) {
                    if (!eliteContains.test(side, offer.classId())) {
                        errors.add(trigger.id() + ": elite class " + offer.classId()
                                + " is unavailable for " + side.id());
                    }
                }
            }
        }
        return List.copyOf(errors);
    }

    public List<TeamSide> resolveTargets(SupplyTriggerDefinition trigger, List<TeamSide> enabledTeams,
                                         TeamSide attacker, TeamSide defender, List<String> errors) {
        String target = trigger.target();
        TeamSide explicit = TeamSide.fromId(target);
        if (explicit != TeamSide.NONE) {
            if (!enabledTeams.contains(explicit)) {
                errors.add(trigger.id() + ": target team " + target + " is not enabled");
                return List.of();
            }
            return List.of(explicit);
        }
        if ("attacker".equals(target) || "defender".equals(target)) {
            TeamSide role = "attacker".equals(target) ? attacker : defender;
            if (role == null || role == TeamSide.NONE || !enabledTeams.contains(role)) {
                errors.add(trigger.id() + ": unresolved target role " + target);
                return List.of();
            }
            return List.of(role);
        }
        if ("event".equals(target)) {
            if (!SupplyTriggerDefinition.CTF_CAPTURE.equals(trigger.event())
                    && !SupplyTriggerDefinition.DOMINATION_CAPTURE.equals(trigger.event())) {
                errors.add(trigger.id() + ": event target is only valid for capture triggers");
                return List.of();
            }
            return List.copyOf(enabledTeams);
        }
        errors.add(trigger.id() + ": invalid supply target " + target);
        return List.of();
    }

    private static boolean eventMatchesMode(String event, String modeId) {
        return switch (event) {
            case SupplyTriggerDefinition.MATCH_TIME -> GameModeRegistry.BREAKTHROUGH.equals(modeId)
                    || GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId)
                    || GameModeRegistry.DOMINATION.equals(modeId);
            case SupplyTriggerDefinition.BREAKTHROUGH_STAGE, SupplyTriggerDefinition.BREAKTHROUGH_SECTOR ->
                    GameModeRegistry.BREAKTHROUGH.equals(modeId);
            case SupplyTriggerDefinition.CTF_CAPTURE -> GameModeRegistry.CAPTURE_THE_FLAG.equals(modeId);
            case SupplyTriggerDefinition.DOMINATION_CAPTURE -> GameModeRegistry.DOMINATION.equals(modeId);
            default -> false;
        };
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag offerTags = new ListTag();
        offers.values().forEach(offer -> offerTags.add(offer.save()));
        tag.put("Offers", offerTags);
        ListTag triggerTags = new ListTag();
        triggers.values().forEach(trigger -> triggerTags.add(trigger.save()));
        tag.put("Triggers", triggerTags);
        return tag;
    }

    static SupplyMapConfig load(CompoundTag tag) {
        SupplyMapConfig config = new SupplyMapConfig();
        ListTag offers = tag.getList("Offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offers.size(); i++) config.addOffer(SupplyOfferDefinition.load(offers.getCompound(i)));
        ListTag triggers = tag.getList("Triggers", Tag.TAG_COMPOUND);
        for (int i = 0; i < triggers.size(); i++) config.addTrigger(SupplyTriggerDefinition.load(triggers.getCompound(i)));
        return config;
    }
}

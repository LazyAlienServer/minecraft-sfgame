package com.sfgame.game;

import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.classsystem.ClassRegistry;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.ItemStrings;
import com.sfgame.data.SFGameId;
import com.sfgame.data.SupplyMapConfig;
import com.sfgame.data.SupplyOfferDefinition;
import com.sfgame.data.SupplyTriggerDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Round-scoped shared supply stock and map trigger scheduler. */
public final class SupplyService {
    public static final int MAX_STOCK = 100_000;

    private final EnumMap<TeamSide, LinkedHashMap<String, PublishedSupply>> supplies =
            new EnumMap<>(TeamSide.class);
    private final Set<String> firedOneShots = new HashSet<>();
    private final Map<String, Long> nextFireTicks = new HashMap<>();
    private SupplyMapConfig config = new SupplyMapConfig();
    private String modeId = "";
    private List<TeamSide> enabledTeams = List.of();
    private TeamSide attacker = TeamSide.NONE;
    private TeamSide defender = TeamSide.NONE;

    public SupplyService() {
        TeamSide.PLAYABLE.forEach(side -> supplies.put(side, new LinkedHashMap<>()));
    }

    public List<String> validate(ArenaMap map, String modeId, TeamSide attacker, TeamSide defender,
                                 ClassRegistry classes, String mapId) {
        if (map == null) return List.of();
        return map.supply().validate(modeId, map.enabledTeams(), attacker, defender,
                (side, classId) -> classes.containsEliteForTeam(modeId, mapId, side, classId));
    }

    public void beginRunning(ArenaMap map, String modeId, TeamSide attacker, TeamSide defender) {
        clear();
        if (map == null) return;
        this.config = map.supply();
        this.modeId = modeId;
        this.enabledTeams = List.copyOf(map.enabledTeams());
        this.attacker = attacker == null ? TeamSide.NONE : attacker;
        this.defender = defender == null ? TeamSide.NONE : defender;
        for (SupplyTriggerDefinition trigger : config.triggers()) {
            if (SupplyTriggerDefinition.MATCH_TIME.equals(trigger.event())) {
                nextFireTicks.put(trigger.id(), (long) trigger.atSeconds() * 20L);
            }
        }
    }
    public void updateRoles(TeamSide attacker, TeamSide defender) {
        this.attacker = attacker == null ? TeamSide.NONE : attacker;
        this.defender = defender == null ? TeamSide.NONE : defender;
    }

    public void clear() {
        supplies.values().forEach(Map::clear);
        firedOneShots.clear();
        nextFireTicks.clear();
        config = new SupplyMapConfig();
        modeId = "";
        enabledTeams = List.of();
        attacker = TeamSide.NONE;
        defender = TeamSide.NONE;
    }

    public List<PublishedSupply> items(TeamSide side) {
        if (side == null || side == TeamSide.NONE) return List.of();
        return List.copyOf(supplies.get(side).values());
    }

    public Optional<PublishedSupply> item(TeamSide side, String id) {
        if (side == null || side == TeamSide.NONE || !SFGameId.isValid(id)) return Optional.empty();
        return Optional.ofNullable(supplies.get(side).get(SFGameId.normalize(id)));
    }

    public boolean publishPreset(TeamSide side, String offerId, int quantity) {
        SupplyOfferDefinition offer = config.offer(offerId).orElse(null);
        if (offer == null) return false;
        PublishedSupply payload = PublishedSupply.from(offer, 0);
        if (SupplyOfferDefinition.ITEM.equals(offer.type()) && payload.stack().isEmpty()) return false;
        return publish(side, payload, quantity);
    }

    public boolean publishItem(TeamSide side, String offerId, String item, int count, String nbt, int quantity) {
        try {
            PublishedSupply supply = PublishedSupply.from(
                    SupplyOfferDefinition.item(offerId, item, count, nbt), 0);
            return !supply.stack().isEmpty() && publish(side, supply, quantity);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean publishElite(TeamSide side, String offerId, ClassDefinition definition, int quantity) {
        if (definition == null) return false;
        return publish(side, PublishedSupply.from(SupplyOfferDefinition.elite(offerId, definition.id()), 0), quantity);
    }

    public boolean publish(TeamSide side, PublishedSupply payload, int quantity) {
        if (side == null || side == TeamSide.NONE || payload == null
                || quantity < 1 || quantity > MAX_STOCK) return false;
        LinkedHashMap<String, PublishedSupply> team = supplies.get(side);
        PublishedSupply existing = team.get(payload.id());
        if (existing != null && !existing.samePayload(payload)) return false;
        int stock = existing == null ? quantity
                : (int) Math.min(MAX_STOCK, (long) existing.quantity() + quantity);
        team.put(payload.id(), payload.withQuantity(stock));
        return true;
    }

    public boolean consume(TeamSide side, String id) {
        PublishedSupply current = item(side, id).orElse(null);
        if (current == null || current.quantity() <= 0) return false;
        LinkedHashMap<String, PublishedSupply> team = supplies.get(side);
        if (current.quantity() == 1) team.remove(current.id());
        else team.put(current.id(), current.withQuantity(current.quantity() - 1));
        return true;
    }

    public boolean remove(TeamSide side, String id) {
        return side != null && side != TeamSide.NONE && SFGameId.isValid(id)
                && supplies.get(side).remove(SFGameId.normalize(id)) != null;
    }

    public int clear(TeamSide side) {
        if (side == null || side == TeamSide.NONE) return 0;
        int count = supplies.get(side).size();
        supplies.get(side).clear();
        return count;
    }

    public boolean tick(long elapsedTicks) {
        boolean changed = false;
        for (SupplyTriggerDefinition trigger : config.triggers()) {
            if (!SupplyTriggerDefinition.MATCH_TIME.equals(trigger.event())) continue;
            long due = nextFireTicks.getOrDefault(trigger.id(), Long.MAX_VALUE);
            if (elapsedTicks < due) continue;
            changed |= fire(trigger, TeamSide.NONE);
            if (trigger.repeatSeconds() > 0) {
                long interval = (long) trigger.repeatSeconds() * 20L;
                do due += interval; while (due <= elapsedTicks);
                nextFireTicks.put(trigger.id(), due);
            } else {
                firedOneShots.add(trigger.id());
                nextFireTicks.remove(trigger.id());
            }
        }
        return changed;
    }

    public boolean fireEvent(String event, TeamSide eventSide, int stage, String sectorId, String pointId) {
        boolean changed = false;
        for (SupplyTriggerDefinition trigger : config.triggers()) {
            if (!trigger.event().equals(event) || firedOneShots.contains(trigger.id())) continue;
            if (SupplyTriggerDefinition.BREAKTHROUGH_STAGE.equals(event) && trigger.stage() != stage) continue;
            if (SupplyTriggerDefinition.BREAKTHROUGH_SECTOR.equals(event)
                    && !trigger.sectorId().equals(sectorId == null ? "" : sectorId)) continue;
            if (SupplyTriggerDefinition.DOMINATION_CAPTURE.equals(event) && !trigger.pointId().isEmpty()
                    && !trigger.pointId().equals(pointId == null ? "" : pointId)) continue;
            changed |= fire(trigger, eventSide);
            firedOneShots.add(trigger.id());
        }
        return changed;
    }

    private boolean fire(SupplyTriggerDefinition trigger, TeamSide eventSide) {
        SupplyOfferDefinition offer = config.offer(trigger.offerId()).orElse(null);
        if (offer == null) return false;
        TeamSide target = resolveTarget(trigger.target(), eventSide);
        return target != TeamSide.NONE && publish(target, PublishedSupply.from(offer, 0), trigger.quantity());
    }

    private TeamSide resolveTarget(String target, TeamSide eventSide) {
        TeamSide explicit = TeamSide.fromId(target);
        if (explicit != TeamSide.NONE) return enabledTeams.contains(explicit) ? explicit : TeamSide.NONE;
        return switch (target) {
            case "attacker" -> attacker;
            case "defender" -> defender;
            case "event" -> eventSide == null ? TeamSide.NONE : eventSide;
            default -> TeamSide.NONE;
        };
    }

    public record PublishedSupply(String id, String type, String item, int count, String nbt,
                                  String classId, int quantity) {
        public PublishedSupply {
            id = SFGameId.normalize(id);
            quantity = Math.max(0, Math.min(MAX_STOCK, quantity));
        }

        static PublishedSupply from(SupplyOfferDefinition offer, int quantity) {
            return new PublishedSupply(offer.id(), offer.type(), offer.item(), offer.count(), offer.nbt(),
                    offer.classId(), quantity);
        }

        public boolean samePayload(PublishedSupply other) {
            return other != null && id.equals(other.id) && type.equals(other.type) && item.equals(other.item)
                    && count == other.count && nbt.equals(other.nbt) && classId.equals(other.classId);
        }

        PublishedSupply withQuantity(int quantity) {
            return new PublishedSupply(id, type, item, count, nbt, classId, quantity);
        }

        public ItemStack stack() {
            ItemStrings.Parsed parsed = ItemStrings.parse(item);
            if (parsed.id() == null || !BuiltInRegistries.ITEM.containsKey(parsed.id())) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(parsed.id()), count);
            String tag = parsed.hasNbt() ? parsed.nbt() : nbt;
            return ItemStrings.applyTag(stack, tag) ? stack : ItemStack.EMPTY;
        }
    }
}

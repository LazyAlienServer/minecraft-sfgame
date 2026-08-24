package com.sfgame.classsystem;

import com.sfgame.SFGame;
import com.sfgame.data.ItemStrings;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.TeamSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.nbt.AmmoBoxItemDataAccessor;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LoadoutService {
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("8a6a21da-baf8-41ce-b3aa-6827a7be1101");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("8a6a21da-baf8-41ce-b3aa-6827a7be1102");

    public List<String> validate(ClassRegistry registry) {
        return validate(registry, GameModeRegistry.TEAM_DEATHMATCH, false);
    }

    public List<String> validate(ClassRegistry registry, String modeId, boolean includeCaptain) {
        return validate(registry, modeId, null, List.of(TeamSide.NONE), includeCaptain);
    }

    /** Validate every team-specific pool used by the selected map. */
    public List<String> validate(ClassRegistry registry, String modeId, String mapId,
                                  java.util.Collection<TeamSide> sides, boolean includeCaptain) {
        List<String> errors = new ArrayList<>(registry.loadErrors());
        java.util.LinkedHashMap<String, ClassDefinition> definitions = new java.util.LinkedHashMap<>();
        java.util.Collection<TeamSide> requested = sides == null || sides.isEmpty() ? List.of(TeamSide.NONE) : sides;
        for (TeamSide side : requested) {
            registry.allForTeam(modeId, mapId, side).forEach(definition -> definitions.putIfAbsent(
                    side.id() + "/" + definition.id(), definition));
            if (includeCaptain) registry.captainClassesForTeam(modeId, mapId, side).forEach(definition -> definitions.putIfAbsent(
                    side.id() + "/captain/" + definition.id(), definition));
        }
        for (ClassDefinition definition : definitions.values()) {
            errors.addAll(validateInventoryShape(definition));
            for (ItemDefinition item : definition.inventory()) {
                if (item == null) continue;
                if (item.isGun()) validateGun(definition.id(), item, errors);
                if (item.isAmmoBox()) validateAmmoBox(definition.id(), item, errors);
            }
        }
        return errors;
    }

    public boolean apply(ServerPlayer player, ClassDefinition definition) {
        clear(player);
        applyAttributes(player, definition);

        for (ItemDefinition item : definition.inventory()) {
            if (item != null && item.slot() >= 0 && !giveInventoryItem(player, item)) return false;
        }
        for (ItemDefinition item : definition.inventory()) {
            if (item != null && item.slot() < 0 && !giveInventoryItem(player, item)) return false;
        }
        equipArmor(player, definition.armor());
        if (definition.offhand() != null) {
            player.setItemSlot(EquipmentSlot.OFFHAND, buildItem(definition.offhand()));
        }
        for (EffectDefinition effect : definition.effects()) {
            ResourceLocation id = ResourceLocation.tryParse(effect.id());
            if (id == null) continue;
            MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(id);
            if (mobEffect != null) {
                player.addEffect(new MobEffectInstance(mobEffect, effect.durationTicks(), effect.amplifier(),
                        effect.ambient(), effect.visible()));
            }
        }
        player.setHealth(player.getMaxHealth());
        return true;
    }

    public void clear(ServerPlayer player) {
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.removeAllEffects();
        removeModifier(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID);
        removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private void applyAttributes(ServerPlayer player, ClassDefinition definition) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (health != null) {
            health.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID, "SFGame class health",
                    definition.maxHealth() - 20.0, AttributeModifier.Operation.ADDITION));
        }
        if (speed != null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "SFGame class speed",
                    definition.movementSpeedMultiplier() - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    static List<String> validateInventoryShape(ClassDefinition definition) {
        int guns = 0;
        int ammoBoxes = 0;
        boolean[] occupiedSlots = new boolean[36];
        List<String> errors = new ArrayList<>();
        for (ItemDefinition item : definition.inventory()) {
            if (item == null) continue;
            if (item.isGun()) guns++;
            if (item.isAmmoBox()) ammoBoxes++;
            int slot = item.slot();
            if (slot < -1 || slot >= occupiedSlots.length) {
                errors.add(definition.id() + ": inventory slot must be between 0 and 35");
            } else if (slot >= 0 && occupiedSlots[slot]) {
                errors.add(definition.id() + ": inventory slot " + slot + " is configured more than once");
            } else if (slot >= 0) {
                occupiedSlots[slot] = true;
            }
        }
        if (guns == 0) errors.add(definition.id() + ": inventory must contain a primary gun");
        if (guns > 3) errors.add(definition.id() + ": inventory supports one primary gun and at most two secondary guns");
        if (ammoBoxes != guns) errors.add(definition.id() + ": inventory must contain one ammo box per gun");
        return errors;
    }

    private void validateGun(String classId, ItemDefinition item, List<String> errors) {
        ResourceLocation gunId = ResourceLocation.tryParse(item.gunId());
        if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
            errors.add(classId + ": unknown TACZ gun " + item.gunId());
        }
        try {
            FireMode.valueOf(item.fireMode());
        } catch (IllegalArgumentException exception) {
            errors.add(classId + ": invalid fire mode " + item.fireMode());
        }
        for (Map.Entry<String, String> attachment : item.attachments().entrySet()) {
            try {
                AttachmentType.valueOf(attachment.getKey().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                errors.add(classId + ": invalid attachment type " + attachment.getKey());
                continue;
            }
            ResourceLocation attachmentId = ResourceLocation.tryParse(attachment.getValue());
            if (attachmentId == null || TimelessAPI.getCommonAttachmentIndex(attachmentId).isEmpty()) {
                errors.add(classId + ": unknown attachment " + attachment.getValue());
            }
        }
    }

    private void validateAmmoBox(String classId, ItemDefinition item, List<String> errors) {
        ResourceLocation ammoId = ResourceLocation.tryParse(item.ammoId());
        if (ammoId == null || TimelessAPI.getCommonAmmoIndex(ammoId).isEmpty()) {
            errors.add(classId + ": unknown TACZ ammo " + item.ammoId());
        }
    }

    private boolean giveInventoryItem(ServerPlayer player, ItemDefinition definition) {
        ItemStack stack = buildInventoryItem(definition);
        if ((definition.isGun() || definition.isAmmoBox()) && stack.isEmpty()) return false;
        if (stack.isEmpty()) return true;
        if (definition.slot() >= 0) {
            player.getInventory().setItem(definition.slot(), stack);
            return true;
        }
        return player.getInventory().add(stack);
    }

    private ItemStack buildInventoryItem(ItemDefinition definition) {
        if (definition.isGun()) return buildGun(definition);
        if (definition.isAmmoBox()) return buildAmmoBox(definition);
        return buildItem(definition);
    }

    private ItemStack buildGun(ItemDefinition definition) {
        ResourceLocation gunId = ResourceLocation.tryParse(definition.gunId());
        if (gunId == null) return ItemStack.EMPTY;
        GunItemBuilder builder = GunItemBuilder.create()
                .setId(gunId)
                .setAmmoCount(definition.initialMagazine())
                .setFireMode(parseFireMode(definition.fireMode()));
        for (Map.Entry<String, String> entry : definition.attachments().entrySet()) {
            try {
                AttachmentType type = AttachmentType.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                ResourceLocation attachmentId = ResourceLocation.tryParse(entry.getValue());
                if (attachmentId != null) builder.putAttachment(type, attachmentId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return builder.build();
    }

    private ItemStack buildAmmoBox(ItemDefinition definition) {
        ResourceLocation ammoId = ResourceLocation.tryParse(definition.ammoId());
        if (ammoId == null) return ItemStack.EMPTY;
        ItemStack ammoBox = new ItemStack(ModItems.AMMO_BOX.get());
        if (!(ammoBox.getItem() instanceof AmmoBoxItemDataAccessor accessor)) {
            SFGame.LOGGER.error("TACZ ammo box does not expose AmmoBoxItemDataAccessor");
            return ItemStack.EMPTY;
        }
        accessor.setAmmoId(ammoBox, ammoId);
        accessor.setAmmoCount(ammoBox, definition.ammoCount());
        accessor.setAmmoLevel(ammoBox, AmmoBoxItem.DIAMOND_LEVEL);
        return ammoBox;
    }

    private void equipArmor(ServerPlayer player, Map<String, ItemDefinition> armor) {
        setArmor(player, armor.get("head"), EquipmentSlot.HEAD);
        setArmor(player, armor.get("chest"), EquipmentSlot.CHEST);
        setArmor(player, armor.get("legs"), EquipmentSlot.LEGS);
        setArmor(player, armor.get("feet"), EquipmentSlot.FEET);
    }

    private void setArmor(ServerPlayer player, ItemDefinition definition, EquipmentSlot slot) {
        if (definition != null) player.setItemSlot(slot, buildItem(definition));
    }

    private ItemStack buildItem(ItemDefinition definition) {
        if (definition == null) return ItemStack.EMPTY;
        ItemStrings.Parsed parsed = ItemStrings.parse(definition.item());
        if (parsed.id() == null || !BuiltInRegistries.ITEM.containsKey(parsed.id())) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(parsed.id()), definition.count());
        String nbt = parsed.hasNbt() ? parsed.nbt() : definition.nbt();
        // A tagless TACZ gun or ammo box is worse than no item at all; skip it.
        if (!ItemStrings.applyTag(stack, nbt)) {
            SFGame.LOGGER.warn("Invalid item NBT in class config: {}", nbt);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private FireMode parseFireMode(String value) {
        try {
            return FireMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FireMode.UNKNOWN;
        }
    }

    private void removeModifier(AttributeInstance attribute, UUID id) {
        if (attribute != null) attribute.removeModifier(id);
    }
}

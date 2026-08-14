package com.sfgame.classsystem;

import com.sfgame.SFGame;
import com.sfgame.game.GameModeRegistry;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.nbt.AmmoBoxItemDataAccessor;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.AmmoBoxItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
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
    private static final int AMMO_BOX_INVENTORY_SLOT = 9;
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("8a6a21da-baf8-41ce-b3aa-6827a7be1101");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("8a6a21da-baf8-41ce-b3aa-6827a7be1102");

    public List<String> validate(ClassRegistry registry) {
        return validate(registry, GameModeRegistry.TEAM_DEATHMATCH, false);
    }

    public List<String> validate(ClassRegistry registry, String modeId, boolean includeCaptain) {
        List<String> errors = new ArrayList<>(registry.loadErrors());
        List<ClassDefinition> definitions = new ArrayList<>(registry.all(modeId));
        if (includeCaptain) definitions.addAll(registry.captainClasses(modeId));
        for (ClassDefinition definition : definitions) {
            ResourceLocation gunId = ResourceLocation.tryParse(definition.gunId());
            ResourceLocation ammoId = ResourceLocation.tryParse(definition.ammoId());
            if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
                errors.add(definition.id() + ": unknown TACZ gun " + definition.gunId());
            }
            if (ammoId == null || TimelessAPI.getCommonAmmoIndex(ammoId).isEmpty()) {
                errors.add(definition.id() + ": unknown TACZ ammo " + definition.ammoId());
            }
            try {
                FireMode.valueOf(definition.fireMode());
            } catch (IllegalArgumentException exception) {
                errors.add(definition.id() + ": invalid fire mode " + definition.fireMode());
            }
            for (Map.Entry<String, String> attachment : definition.attachments().entrySet()) {
                try {
                    AttachmentType.valueOf(attachment.getKey().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    errors.add(definition.id() + ": invalid attachment type " + attachment.getKey());
                    continue;
                }
                ResourceLocation attachmentId = ResourceLocation.tryParse(attachment.getValue());
                if (attachmentId == null || TimelessAPI.getCommonAttachmentIndex(attachmentId).isEmpty()) {
                    errors.add(definition.id() + ": unknown attachment " + attachment.getValue());
                }
            }
        }
        return errors;
    }

    public boolean apply(ServerPlayer player, ClassDefinition definition) {
        clear(player);
        applyAttributes(player, definition);

        ResourceLocation gunId = ResourceLocation.tryParse(definition.gunId());
        ResourceLocation ammoId = ResourceLocation.tryParse(definition.ammoId());
        if (gunId == null || ammoId == null) return false;

        GunItemBuilder gunBuilder = GunItemBuilder.create()
                .setId(gunId)
                .setAmmoCount(definition.initialMagazine())
                .setFireMode(parseFireMode(definition.fireMode()));
        for (Map.Entry<String, String> entry : definition.attachments().entrySet()) {
            try {
                AttachmentType type = AttachmentType.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                ResourceLocation attachmentId = ResourceLocation.tryParse(entry.getValue());
                if (attachmentId != null) gunBuilder.putAttachment(type, attachmentId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        ItemStack gun = gunBuilder.build();
        if (gun.isEmpty()) return false;
        player.getInventory().add(gun);
        if (!giveAmmoBox(player, ammoId, definition.reserveAmmo())) return false;

        for (ItemDefinition item : definition.inventory()) {
            ItemStack stack = buildItem(item);
            if (!stack.isEmpty()) player.getInventory().add(stack);
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

    private boolean giveAmmoBox(ServerPlayer player, ResourceLocation ammoId, int amount) {
        if (amount <= 0) return true;

        ItemStack ammoBox = new ItemStack(ModItems.AMMO_BOX.get());
        if (!(ammoBox.getItem() instanceof AmmoBoxItemDataAccessor accessor)) {
            SFGame.LOGGER.error("TACZ ammo box does not expose AmmoBoxItemDataAccessor");
            return false;
        }
        accessor.setAmmoId(ammoBox, ammoId);
        accessor.setAmmoCount(ammoBox, amount);
        accessor.setAmmoLevel(ammoBox, AmmoBoxItem.DIAMOND_LEVEL);
        player.getInventory().setItem(AMMO_BOX_INVENTORY_SLOT, ammoBox);
        return true;
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
        ResourceLocation id = ResourceLocation.tryParse(definition.item());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(item, definition.count());
        if (!definition.nbt().isBlank()) {
            try {
                stack.setTag(TagParser.parseTag(definition.nbt()));
            } catch (Exception exception) {
                SFGame.LOGGER.warn("Invalid item NBT in class config: {}", definition.nbt());
            }
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

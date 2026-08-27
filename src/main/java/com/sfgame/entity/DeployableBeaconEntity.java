package com.sfgame.entity;

import com.sfgame.game.MatchManager;
import com.sfgame.game.TeamSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.UUID;

public final class DeployableBeaconEntity extends Display.BlockDisplay {
    public static final String BEACON_TAG = "SFGameRespawnBeacon";
    public static final String TEAM_TAG = "Faction";
    public static final String OWNER_TAG = "OwnerUUID";
    private static final String HEALTH_TAG = "Health";
    private static final String MAX_HEALTH_TAG = "MaxHealth";
    public static final float DISPLAY_SCALE = 0.5F;
    public static final float HITBOX_WIDTH = DISPLAY_SCALE;
    public static final float HITBOX_HEIGHT = DISPLAY_SCALE;
    private static final float DISPLAY_HORIZONTAL_OFFSET = -DISPLAY_SCALE / 2.0F;
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(
            DeployableBeaconEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_HEALTH = SynchedEntityData.defineId(
            DeployableBeaconEntity.class, EntityDataSerializers.FLOAT);

    public DeployableBeaconEntity(EntityType<? extends DeployableBeaconEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setSilent(true);
        addTag(BEACON_TAG);
        applyDisplayTransform();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HEALTH, 0.0F);
        entityData.define(MAX_HEALTH, 0.0F);
    }

    public float getHealth() { return entityData.get(HEALTH); }
    public float getMaxHealth() { return entityData.get(MAX_HEALTH); }

    public void setHealth(float value) {
        entityData.set(HEALTH, BeaconHealth.clamp(value, getMaxHealth()));
    }

    public void setMaxHealth(float value) {
        float max = Math.max(0.0F, value);
        entityData.set(MAX_HEALTH, max);
        if (getHealth() > max) entityData.set(HEALTH, max);
    }


    public void initializeHealth(float value) {
        setMaxHealth(value);
        setHealth(value);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || amount <= 0.0F || !isAlive() || getMaxHealth() <= 0.0F) return false;
        setHealth(BeaconHealth.afterDamage(getHealth(), amount));
        if (getHealth() <= 0.0F) {
            MatchManager.get().beacons().onDestroyed(this);
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean skipAttackInteraction(Entity attacker) { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(MAX_HEALTH_TAG)) entityData.set(MAX_HEALTH, Math.max(0.0F, tag.getFloat(MAX_HEALTH_TAG)));
        if (tag.contains(HEALTH_TAG)) entityData.set(HEALTH,
                BeaconHealth.clamp(tag.getFloat(HEALTH_TAG), getMaxHealth()));
        if (tag.contains(TEAM_TAG)) getPersistentData().putString(TEAM_TAG, tag.getString(TEAM_TAG));
        if (tag.hasUUID(OWNER_TAG)) getPersistentData().putUUID(OWNER_TAG, tag.getUUID(OWNER_TAG));
        addTag(BEACON_TAG);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(HEALTH_TAG, getHealth());
        tag.putFloat(MAX_HEALTH_TAG, getMaxHealth());
        tag.putString(TEAM_TAG, team().id());
        UUID owner = ownerUuid();
        if (owner != null) tag.putUUID(OWNER_TAG, owner);
        tag.putBoolean(BEACON_TAG, true);
    }

    public void team(TeamSide side) {
        getPersistentData().putString(TEAM_TAG, side.id());
    }

    public TeamSide team() {
        return TeamSide.fromId(getPersistentData().getString(TEAM_TAG));
    }

    public void ownerUuid(@Nullable UUID owner) {
        if (owner == null) getPersistentData().remove(OWNER_TAG);
        else getPersistentData().putUUID(OWNER_TAG, owner);
    }

    @Nullable
    public UUID ownerUuid() {
        return getPersistentData().hasUUID(OWNER_TAG) ? getPersistentData().getUUID(OWNER_TAG) : null;
    }

    private void applyDisplayTransform() {
        CompoundTag tag = new CompoundTag();
        saveWithoutId(tag);
        tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(Blocks.BEACON.defaultBlockState()));
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floatList(DISPLAY_HORIZONTAL_OFFSET, 0.0F, DISPLAY_HORIZONTAL_OFFSET));
        transformation.put("scale", floatList(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE));
        transformation.put("left_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("right_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put(Display.TAG_TRANSFORMATION, transformation);
        load(tag);
        setNoGravity(true);
        setSilent(true);
        addTag(BEACON_TAG);
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }
}

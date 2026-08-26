package com.sfgame.registry;

import com.sfgame.SFGame;
import com.sfgame.entity.DeployableBeaconEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SFGame.MOD_ID);

    public static final RegistryObject<EntityType<DeployableBeaconEntity>> RESPAWN_BEACON =
            ENTITY_TYPES.register("respawn_beacon", () -> EntityType.Builder
                    .of(DeployableBeaconEntity::new, MobCategory.MISC)
                    .sized(0.45F, 0.65F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(SFGame.MOD_ID + ":respawn_beacon"));

    private ModEntities() { }
}

package com.sfgame.registry;

import com.sfgame.SFGame;
import com.sfgame.item.DeployableBeaconItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SFGame.MOD_ID);

    public static final RegistryObject<Item> RESPAWN_BEACON = ITEMS.register("respawn_beacon",
            () -> new DeployableBeaconItem(new Item.Properties().stacksTo(16)));

    private ModItems() { }
}

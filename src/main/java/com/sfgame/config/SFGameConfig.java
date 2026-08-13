package com.sfgame.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SFGameConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue GLOBAL_HUNGER_LOCK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("global");
        GLOBAL_HUNGER_LOCK = builder
                .comment("Keep every online player's hunger and saturation at 20 while SFGame is installed.")
                .define("globalHungerLock", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SFGameConfig() {
    }
}


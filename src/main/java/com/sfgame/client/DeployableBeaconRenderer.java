package com.sfgame.client;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Uses vanilla ItemDisplay rendering for the custom damageable beacon entity. */
public final class DeployableBeaconRenderer extends DisplayRenderer.ItemDisplayRenderer {
    public DeployableBeaconRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}

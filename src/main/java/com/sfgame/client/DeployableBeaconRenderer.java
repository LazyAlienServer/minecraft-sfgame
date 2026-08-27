package com.sfgame.client;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Uses vanilla BlockDisplay rendering for the custom damageable beacon entity. */
public final class DeployableBeaconRenderer extends DisplayRenderer.BlockDisplayRenderer {
    public DeployableBeaconRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}

package com.sfgame.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.sfgame.entity.BeaconHealth;
import com.sfgame.entity.DeployableBeaconEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;

/** Renders the vanilla beacon model and its vanilla health damage overlay. */
public final class DeployableBeaconRenderer extends DisplayRenderer.BlockDisplayRenderer {
    private final BlockRenderDispatcher blockRenderer;
    private final MultiBufferSource.BufferSource crumblingBuffer;

    public DeployableBeaconRenderer(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
        crumblingBuffer = Minecraft.getInstance().renderBuffers().crumblingBufferSource();
    }

    @Override
    public void renderInner(Display.BlockDisplay display, Display.BlockDisplay.BlockRenderState renderState,
                            PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        super.renderInner(display, renderState, poseStack, buffer, packedLight, partialTick);
        if (!(display instanceof DeployableBeaconEntity beacon)) return;
        int damageStage = BeaconHealth.damageStage(beacon.getHealth(), beacon.getMaxHealth());
        if (damageStage < 0) return;
        PoseStack.Pose pose = poseStack.last();
        blockRenderer.renderBreakingTexture(renderState.blockState(), BlockPos.ZERO, beacon.level(), poseStack,
                new SheetedDecalTextureGenerator(crumblingBuffer.getBuffer(ModelBakery.DESTROY_TYPES.get(damageStage)),
                        pose.pose(), pose.normal(), 1.0F));
    }
}

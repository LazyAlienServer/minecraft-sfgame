package com.sfgame.mixin;

import com.sfgame.game.MatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Protects the arena from mods that call Level.destroyBlock directly instead
 * of posting a player break event (notably vehicle collision and projectiles).
 */
@Mixin(Level.class)
public abstract class LevelBlockProtectionMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void sfgame$protectMapBlock(BlockPos pos, boolean drop, Entity breaker, int recursionLeft,
                                        CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerLevel level
                && !MatchManager.get().canExternalDestroyBlock(level, pos, level.getBlockState(pos))) {
            callback.setReturnValue(false);
        }
    }
}

package com.sfgame.item;

import com.sfgame.game.MatchManager;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.TeamSide;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DeployableBeaconItem extends Item {
    public DeployableBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        MatchManager manager = MatchManager.get();
        if (manager.phase() != MatchPhase.RUNNING || !manager.state(player).participating()
                || manager.state(player).respawning() || player.isSpectator() || player.isDeadOrDying()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("sfgame.respawn.beacon_unavailable"), true);
            return InteractionResult.FAIL;
        }
        TeamSide side = manager.teams().sideOf(player, manager.savedData());
        if (side == TeamSide.NONE || !manager.savedData().enabledTeams().contains(side)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("sfgame.respawn.beacon_unavailable"), true);
            return InteractionResult.FAIL;
        }
        if (manager.beacons().hasBeacon(side)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("sfgame.respawn.beacon_already_present"), true);
            return InteractionResult.FAIL;
        }
        BlockPos clickedPos = context.getClickedPos();
        BlockPos adjacentPos = clickedPos.relative(context.getClickedFace());
        BlockState clickedState = context.getLevel().getBlockState(clickedPos);
        BlockState adjacentState = context.getLevel().getBlockState(adjacentPos);
        BlockPos placePos = BeaconPlacement.resolve(clickedPos, context.getClickedFace(),
                clickedState.canBeReplaced(), adjacentState.canBeReplaced());
        if (placePos == null || !context.getLevel().getWorldBorder().isWithinBounds(placePos)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "sfgame.respawn.beacon_invalid_placement"), true);
            return InteractionResult.FAIL;
        }
        Vec3 position = Vec3.atCenterOf(placePos);
        if (!manager.beacons().deploy(player, position)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "sfgame.respawn.beacon_deploy_failed"), true);
            return InteractionResult.FAIL;
        }
        context.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(false);
    }
}

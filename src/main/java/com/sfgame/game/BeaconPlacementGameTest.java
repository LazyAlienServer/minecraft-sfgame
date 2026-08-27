package com.sfgame.game;

import com.mojang.authlib.GameProfile;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.SFGameSavedData;
import com.sfgame.entity.DeployableBeaconEntity;
import com.sfgame.item.DeployableBeaconItem;
import com.sfgame.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.lang.reflect.Field;
import java.util.UUID;

public final class BeaconPlacementGameTest {
    private static final BlockPos SUPPORT = new BlockPos(1, 1, 1);

    private BeaconPlacementGameTest() { }


    @GameTest(template = "empty", templateNamespace = "sfgame", batch = "sfgame_beacon", timeoutTicks = 100)
    public static void deployableBeaconUseOnAddsBlockDisplayAndConsumesOne(GameTestHelper helper)
            throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        MatchManager manager = MatchManager.get();
        manager.serverStarted(server);
        SFGameSavedData data = manager.savedData();
        data.addSpawn(TeamSide.RED, new ArenaPosition(helper.getLevel().dimension().location().toString(),
                SUPPORT.getX() + 0.5, SUPPORT.getY() + 1.0, SUPPORT.getZ() + 0.5, 0.0F, 0.0F));
        manager.teams().ensureDefaultTeams(server, data);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-beacon-player"));
        server.getScoreboard().addPlayerToTeam(player.getScoreboardName(),
                server.getScoreboard().getPlayerTeam(data.teamName(TeamSide.RED)));
        manager.state(player).participating(true);
        setPhase(manager, MatchPhase.RUNNING);
        manager.beacons().beginRunning(server);
        helper.setBlock(SUPPORT, Blocks.STONE);
        BlockPos support = helper.absolutePos(SUPPORT);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0),
                Direction.UP, support, false);
        ItemStack stack = new ItemStack(ModItems.RESPAWN_BEACON.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int beforeCount = stack.getCount();
        player.setGameMode(GameType.ADVENTURE);
        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), stack,
                InteractionHand.MAIN_HAND, hit);

        helper.assertTrue(result.consumesAction(), "beacon right-click must deploy the item");
        helper.assertTrue(stack.getCount() == beforeCount - 1, "successful placement must consume one item");
        DeployableBeaconEntity beacon = manager.beacons().target(TeamSide.RED);
        helper.assertTrue(beacon != null && beacon.isAlive(), "placement must add the beacon entity");
        CompoundTag saved = beacon.saveWithoutId(new CompoundTag());
        helper.assertTrue("minecraft:beacon".equals(saved.getCompound(Display.BlockDisplay.TAG_BLOCK_STATE)
                .getString("Name")), "placed entity must render the vanilla beacon block state");
        manager.beacons().clear(server);
        setPhase(manager, MatchPhase.LOBBY);

        data.selectMode(GameModeRegistry.DOMINATION);
        ArenaMap map = data.activeMap();
        for (TeamSide side : TeamSide.PLAYABLE) map.clearSpawns(side);
        ArenaPosition spawn = new ArenaPosition(helper.getLevel().dimension().location().toString(),
                0.5, 64.0, 0.5, 0.0F, 0.0F);
        map.addSpawn(TeamSide.RED, spawn);
        map.addSpawn(TeamSide.BLUE, spawn);
        manager.teams().ensureDefaultTeams(server, data);
        CapturingFakePlayer claimPlayer = new CapturingFakePlayer(helper.getLevel());
        server.getScoreboard().addPlayerToTeam(claimPlayer.getScoreboardName(),
                server.getScoreboard().getPlayerTeam(data.teamName(TeamSide.RED)));
        manager.state(claimPlayer).participating(true);
        setPhase(manager, MatchPhase.RUNNING);
        manager.supplies().beginRunning(map, GameModeRegistry.DOMINATION, TeamSide.NONE, TeamSide.NONE);
        try {
            helper.assertTrue(manager.supplies().publishItem(TeamSide.RED, "apples", "minecraft:apple",
                    2, "", 1), "apple supply must publish");
            helper.assertTrue(manager.claimSupply(claimPlayer, "apples"), "apple supply must be claimable");
            assertActionbarContains(helper, claimPlayer,
                    new ItemStack(Items.APPLE).getHoverName().getString());

            helper.assertTrue(manager.supplies().publishItem(TeamSide.RED, "beacon", "sfgame:respawn_beacon",
                    1, "", 1), "beacon supply must publish");
            helper.assertTrue(manager.claimSupply(claimPlayer, "beacon"), "beacon supply must be claimable");
            assertActionbarContains(helper, claimPlayer,
                    new ItemStack(ModItems.RESPAWN_BEACON.get()).getHoverName().getString());
        } finally {
            manager.supplies().clear();
            manager.beacons().clear(server);
            setPhase(manager, MatchPhase.LOBBY);
        }
        helper.succeed();
    }



    private static void assertActionbarContains(GameTestHelper helper, CapturingFakePlayer player, String itemName) {
        helper.assertTrue(player.lastActionbar != null, "claim must send an actionbar message");
        helper.assertTrue(player.lastActionbar.getString().contains(itemName),
                "actionbar must name " + itemName + ": " + player.lastActionbar.getString());
    }

    private static final class CapturingFakePlayer extends FakePlayer {
        private Component lastActionbar;

        private CapturingFakePlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "test-supply-player"));
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (overlay) lastActionbar = message;
        }
    }
    private static void setPhase(MatchManager manager, MatchPhase phase) throws ReflectiveOperationException {
        Field field = MatchManager.class.getDeclaredField("phase");
        field.setAccessible(true);
        field.set(manager, phase);
    }
}

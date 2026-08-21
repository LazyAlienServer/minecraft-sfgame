package com.sfgame.event;

import com.sfgame.SFGame;
import com.sfgame.command.SFGameCommands;
import com.sfgame.config.SFGameConfig;
import com.sfgame.game.MatchHudService;
import com.sfgame.game.MatchManager;
import com.sfgame.game.MatchPhase;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SFGame.MOD_ID)
public final class SFGameEvents {
    private static final MatchHudService HUD = new MatchHudService();
    private static final Set<UUID> SAFE_PHASE_PROTECTED_PLAYERS = new HashSet<>();
    private static final int SAFE_PHASE_RESISTANCE_DURATION = 40;
    private static final int RESISTANCE_FIVE_AMPLIFIER = 4;
    private static int hudTicker;

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        SFGameCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        MatchManager.get().serverStarted(event.getServer());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        HUD.clear(event.getServer());
        SAFE_PHASE_PROTECTED_PLAYERS.clear();
        MatchManager.get().serverStopped();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MatchManager.get().tick();
        if (++hudTicker >= 20) {
            hudTicker = 0;
            HUD.update(event.getServer(), MatchManager.get());
        }
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)) return;
        if (SFGameConfig.GLOBAL_HUNGER_LOCK.get()) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
        }
        updateSafePhaseProtection(player);
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fillHunger(player);
            MatchManager.get().playerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MatchManager.get().playerLoggedOut(player);
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) fillHunger(player);
    }

    @SubscribeEvent
    public static void tabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var state = MatchManager.get().state(player);
        Component teamFormattedName = PlayerTeam.formatNameForTeam(
                player.getTeam(), Component.literal(player.getGameProfile().getName()));
        event.setDisplayName(teamFormattedName.copy()
                .append(Component.literal("  " + state.kills() + "/" + state.deaths())));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void livingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        MatchManager manager = MatchManager.get();
        Player sourcePlayer = event.getSource().getEntity() instanceof Player player ? player
                : event.getSource().getDirectEntity() instanceof Player player ? player : null;
        if (sourcePlayer instanceof ServerPlayer attacker && manager.ctfCarrierCannotUseWeapons(attacker)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (isSafePhase(manager.phase()) || manager.modeBlocksCombat()) {
            event.setCanceled(true);
            return;
        }
        if (sourcePlayer instanceof ServerPlayer attacker && manager.areFriendly(attacker, victim)) {
            event.setCanceled(true);
            return;
        }
        if (manager.isProtected(victim)) {
            event.setCanceled(true);
            return;
        }
        if (sourcePlayer instanceof ServerPlayer attacker && manager.isProtected(attacker)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void livingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || MatchManager.get().phase() != MatchPhase.RUNNING) return;
        if (!MatchManager.get().state(victim).participating()) return;
        event.setCanceled(true);
        MatchManager.get().handleDeath(victim, event.getSource());
    }

    @SubscribeEvent
    public static void gunFire(GunFireEvent event) {
        if (event.getLogicalSide() == LogicalSide.SERVER && event.getShooter() instanceof ServerPlayer player) {
            if (MatchManager.get().ctfCarrierCannotUseWeapons(player)) {
                event.setCanceled(true);
                return;
            }
            MatchManager.get().removeProtection(player);
        }
    }

    @SubscribeEvent
    public static void itemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !MatchManager.get().mayDrop(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        MatchManager manager = MatchManager.get();
        if ((manager.phase() == MatchPhase.RESULT || manager.state(player).participating())
                && !manager.canBreakBlock(player, event.getPos(), event.getState())) {
            event.setCanceled(true);
            long now = player.server.getTickCount();
            var persistent = player.getPersistentData();
            String cooldownKey = "sfgameMapEditDenialTick";
            if (!persistent.contains(cooldownKey) || now - persistent.getLong(cooldownKey) >= 20L) {
                persistent.putLong(cooldownKey, now);
                player.displayClientMessage(manager.mapEditDenialReason(player, event.getPos(), event.getState()), true);
            }
        }
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MatchManager manager = MatchManager.get();
        if ((manager.phase() == MatchPhase.RESULT || manager.state(player).participating())
                && !manager.canPlaceBlock(player, event.getPos(), event.getPlacedBlock())) event.setCanceled(true);
    }

    /** Covers vanilla explosions plus TACZ/Superb Warfare explosions that use Forge's explosion pipeline. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void explosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(pos -> !MatchManager.get().canExternalDestroyBlock(
                level, pos, level.getBlockState(pos)));
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && MatchManager.get().state(player).participating()
                && player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
                && !MatchManager.get().usesMapEditingSurvival(player)) {
            player.setGameMode(GameType.ADVENTURE);
        }
    }

    private static void fillHunger(ServerPlayer player) {
        if (!SFGameConfig.GLOBAL_HUNGER_LOCK.get()) return;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
    }

    private static void updateSafePhaseProtection(ServerPlayer player) {
        MatchPhase phase = MatchManager.get().phase();
        if (isSafePhase(phase)) {
            MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            boolean needsProtection = resistance == null || resistance.getAmplifier() < RESISTANCE_FIVE_AMPLIFIER
                    || resistance.getAmplifier() == RESISTANCE_FIVE_AMPLIFIER && resistance.getDuration() <= 20;
            if (needsProtection) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                        SAFE_PHASE_RESISTANCE_DURATION, RESISTANCE_FIVE_AMPLIFIER, false, false, false));
                SAFE_PHASE_PROTECTED_PLAYERS.add(player.getUUID());
            }
        } else if (SAFE_PHASE_PROTECTED_PLAYERS.remove(player.getUUID())) {
            MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && resistance.getAmplifier() == RESISTANCE_FIVE_AMPLIFIER
                    && resistance.getDuration() <= SAFE_PHASE_RESISTANCE_DURATION) {
                player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            }
        }
    }

    private static boolean isSafePhase(MatchPhase phase) {
        return phase == MatchPhase.UNCONFIGURED || phase == MatchPhase.LOBBY || phase == MatchPhase.PREPARING || phase == MatchPhase.RESULT;
    }

    private SFGameEvents() {}
}

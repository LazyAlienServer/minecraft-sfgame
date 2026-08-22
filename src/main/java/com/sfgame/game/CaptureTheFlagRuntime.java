package com.sfgame.game;

import com.sfgame.SFGame;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.BoxCaptureRegion;
import com.sfgame.data.CarrierRestriction;
import com.sfgame.data.CaptureRegion;
import com.sfgame.data.CtfForwardFlagDefinition;
import com.sfgame.data.CtfHomeFlagDefinition;
import com.sfgame.data.CtfVariant;
import com.sfgame.data.MatchRules;
import com.sfgame.data.CaptureTheFlagMapConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative CTF state machine. Flag entities are presentation only. */
public final class CaptureTheFlagRuntime implements MatchModeRuntime {
    private enum Location { STAND, CARRIED, DROPPED, DEPOT }

    private static final String DISPLAY_TAG = "SFGameCtfFlag";
    private static final String CARRIER_FLAG_TAG = "SFGameCtfCarrierFlag";
    private final Map<String, FlagState> flags = new HashMap<>();
    private final Map<TeamSide, CapturePointState> homeCapture = new EnumMap<>(TeamSide.class);
    private final Map<String, ServerBossEvent> bossBars = new HashMap<>();
    private final Map<String, ArmorStand> displays = new HashMap<>();
    private MinecraftServer server;
    private ArenaMap activeMap;
    private int attackerTickets;
    private CtfVariant variant = CtfVariant.CLASSIC;
    private CarrierRestriction restriction = CarrierRestriction.NORMAL;
    private TeamSide attacker = TeamSide.RED;
    private TeamSide defender = TeamSide.BLUE;

    @Override
    public List<String> validate(MinecraftServer server, ArenaMap map, MatchRules rules) {
        List<String> errors = new ArrayList<>(map.captureTheFlag().validate(map.enabledTeams(), rules.ctfVariant(),
                rules.ctfAttacker(), rules.ctfDefender()));
        CaptureTheFlagMapConfig config = map.captureTheFlag();
        for (CtfHomeFlagDefinition home : config.homes()) {
            checkPosition(server, home.flagPosition(), "Home flag " + home.team().id(), errors);
            checkPosition(server, home.depotPosition(), "Home depot " + home.team().id(), errors);
            checkRegion(server, home.captureRegion(), "Home capture " + home.team().id(), errors);
        }
        for (CtfForwardFlagDefinition flag : config.forwardFlags()) {
            checkPosition(server, flag.stand(), "Forward flag " + flag.id(), errors);
            checkRegion(server, flag.region(), "Forward flag " + flag.id(), errors);
        }
        return errors;
    }

    @Override
    public void start(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        stop();
        this.server = server; this.activeMap = map;
        this.variant = rules.ctfVariant();
        this.restriction = rules.ctfCarrierRestriction();
        this.attacker = rules.ctfAttacker();
        this.defender = rules.ctfDefender();
        removeOrphanedDisplays(server);
        CaptureTheFlagMapConfig config = map.captureTheFlag();
        for (TeamSide side : config.teams(map.enabledTeams(), variant, attacker, defender)) {
            CtfHomeFlagDefinition home = config.homeOptional(side).orElse(null);
            if (home == null || home.flagPosition() == null) continue;
            flags.put(homeKey(side), FlagState.home(home));
            homeCapture.put(side, new CapturePointState());
        }
        for (CtfForwardFlagDefinition definition : config.forwardFlags()) flags.put(definition.id(), FlagState.forward(definition));
        attackerTickets = rules.attackerTickets();
        for (FlagState state : flags.values()) {
            state.reset();
            if (state.home && variant != CtfVariant.TERRITORY) state.unlocked = true;
        }
        refreshDisplays(manager, map);
        refreshBossBars(manager, map, rules);
        announce(manager, Component.translatable("sfgame.ctf.started"));
    }

    @Override
    public ModeTickResult tick(MinecraftServer server, MatchManager manager, ArenaMap map, MatchRules rules) {
        this.server = server; this.activeMap = map;
        if (variant == CtfVariant.TERRITORY) tickTerritory(manager, map, rules);
        maintainCarriers(manager);
        tickDropped(manager, rules);
        handleInteractions(manager, map, rules);
        applyCarrierRestrictions(manager, restriction);
        refreshDisplays(manager, map);
        refreshBossBars(manager, map, rules);

        if (variant == CtfVariant.ASSAULT && attackerTickets <= 0) {
            return ModeTickResult.finish(defender);
        }
        for (TeamSide side : manager.savedData().enabledTeams()) {
            if (manager.score(side) >= rules.scoreLimit()) return ModeTickResult.finish(side);
        }
        return ModeTickResult.CONTINUE;
    }

    @Override
    public int remainingSeconds(MatchManager manager, MatchRules rules) {
        return Math.max(0, rules.timeLimitSeconds() - manager.elapsedTicks() / 20);
    }

    @Override
    public void onKill(ServerPlayer killer, TeamSide side, MatchManager manager) {
        manager.addCurrency(killer, 25);
    }

    @Override
    public void onPlayerDeath(ServerPlayer victim, TeamSide side, MatchManager manager) {
        if (activeMap != null && variant == CtfVariant.ASSAULT && side == attacker) {
            attackerTickets = Math.max(0, attackerTickets - 1);
        }
        dropCarriedFlag(victim, manager);
    }

    @Override
    public void onPlayerTeamChanged(ServerPlayer player, TeamSide oldSide, TeamSide newSide, MatchManager manager) {
        dropCarriedFlag(player, manager);
    }

    @Override
    public void onPlayerLoggedOut(ServerPlayer player, MatchManager manager) {
        dropCarriedFlag(player, manager);
    }

    @Override
    public ArenaPosition spawnFor(TeamSide side, ArenaMap map) { return map.randomSpawn(side); }

    public int attackerTickets() { return attackerTickets; }
    @Override
    public void onRuleChanged(String key, MatchRules rules) {
        if ("attackerTickets".equals(key)) attackerTickets = rules.attackerTickets();
    }
    public CarrierRestriction carrierRestriction() {
        return activeMap == null ? CarrierRestriction.NORMAL : restriction;
    }
    public boolean isCarrier(UUID playerId) {
        return flags.values().stream().anyMatch(flag -> flag.location == Location.CARRIED && playerId.equals(flag.carrier));
    }
    public List<FlagView> flagViews(MatchManager manager) {
        return flags.values().stream().map(flag -> new FlagView(flag.key, flag.owner, flag.location.name().toLowerCase(Locale.ROOT),
                flag.carrier == null ? null : flag.carrier.toString(), flag.unlocked, flag.depotTeam)).toList();
    }

    public String hudLine(ServerPlayer viewer) {
        String carried = flags.values().stream()
                .filter(flag -> flag.location == Location.CARRIED && viewer.getUUID().equals(flag.carrier))
                .map(flag -> "FLAG " + displayId(flag.key)).findFirst().orElse(null);
        if (carried != null) return carried;
        long dropped = flags.values().stream().filter(flag -> flag.location == Location.DROPPED).count();
        return "CTF" + (dropped > 0 ? " · DROPPED " + dropped : "");
    }

    @Override
    public void stop() {
        flags.values().forEach(this::clearCarrierAppearance);
        if (server != null) removeOrphanedDisplays(server);
        displays.values().forEach(ArmorStand::discard); displays.clear();
        bossBars.values().forEach(ServerBossEvent::removeAllPlayers); bossBars.clear();
        flags.clear(); homeCapture.clear(); server = null; activeMap = null; attackerTickets = 0;
        variant = CtfVariant.CLASSIC; restriction = CarrierRestriction.NORMAL;
        attacker = TeamSide.RED; defender = TeamSide.BLUE;
    }

    private static void removeOrphanedDisplays(MinecraftServer server) {
        // A server restart does not restore the in-memory CTF state, but old
        // armor stands may have been saved in the world. Remove every tagged
        // presentation entity before creating the new match displays.
        AABB scan = new AABB(-30_000_000, -2048, -30_000_000,
                30_000_000, 2048, 30_000_000);
        for (ServerLevel level : server.getAllLevels()) {
            for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, scan,
                    entity -> entity.getTags().contains(DISPLAY_TAG))) {
                stand.discard();
            }
        }
    }

    private void tickTerritory(MatchManager manager, ArenaMap map, MatchRules rules) {
        for (FlagState flag : flags.values()) {
            if (flag.home || flag.location != Location.STAND || flag.forward == null) continue;
            CapturePointState point = flag.pointState;
            Map<TeamSide, Integer> counts = countsIn(flag.forward.region(), manager);
            point.contested(isTied(counts));
            TeamSide leader = uniqueLeader(counts);
            if (point.contested()) continue;
            if (leader == TeamSide.NONE) {
                point.advance(TeamSide.NONE, 1.0 / (rules.captureTimeSeconds() * 20.0), true);
                continue;
            }
            if (leader == flag.owner) {
                point.advance(flag.owner, 1.0 / (rules.captureTimeSeconds() * 20.0), false);
                continue;
            }
            int first = counts.getOrDefault(leader, 0);
            int second = counts.values().stream().filter(value -> value > 0 && value < first).max(Integer::compareTo).orElse(0);
            double multiplier = DominationRuntime.calculateCaptureMultiplier(rules, first, second);
            CapturePointState.Change change = point.advance(leader, multiplier / (rules.captureTimeSeconds() * 20.0), false);
            if (change == CapturePointState.Change.CAPTURED && point.owner() == leader && !flag.unlocked
                    && leader != flag.owner) {
                flag.unlocked = true;
                announce(manager, Component.translatable("sfgame.ctf.forward_unlocked", displayId(flag.key)));
                awardUnlock(leader, manager);
            }
        }
        for (Map.Entry<TeamSide, CapturePointState> entry : homeCapture.entrySet()) {
            FlagState home = flags.get(homeKey(entry.getKey()));
            CtfHomeFlagDefinition definition = map.captureTheFlag().homeOptional(entry.getKey()).orElse(null);
            if (home == null || definition == null || home.location != Location.STAND || home.unlocked || definition.captureRegion() == null) continue;
            Map<TeamSide, Integer> counts = countsIn(definition.captureRegion(), manager);
            CapturePointState state = entry.getValue();
            state.contested(isTiedExcluding(counts, entry.getKey()));
            TeamSide leader = uniqueLeaderExcluding(counts, entry.getKey());
            double baseDelta = 1.0 / (rules.ctfHomeCaptureTimeSeconds() * 20.0);
            if (state.contested()) continue;
            if (leader == TeamSide.NONE) {
                state.advance(TeamSide.NONE, baseDelta, true);
                continue;
            }
            int first = counts.getOrDefault(leader, 0);
            int second = counts.values().stream().filter(value -> value > 0 && value < first).max(Integer::compareTo).orElse(0);
            double multiplier = DominationRuntime.calculateCaptureMultiplier(rules, first, second);
            CapturePointState.Change change = state.advance(leader, multiplier * baseDelta, false);
            if (change == CapturePointState.Change.CAPTURED && state.owner() == leader) {
                home.unlocked = true;
                announce(manager, Component.translatable("sfgame.ctf.home_unlocked", teamName(entry.getKey())));
            }
        }
    }

    private void maintainCarriers(MatchManager manager) {
        for (FlagState flag : flags.values()) {
            if (flag.location != Location.CARRIED || flag.carrier == null) continue;
            ServerPlayer player = manager.serverPlayer(flag.carrier);
            if (player == null || !manager.state(player).participating() || player.isSpectator()) {
                drop(flag, player == null ? null : player.position(), player == null ? null : player.level().dimension().location().toString());
            }
        }
    }

    private void tickDropped(MatchManager manager, MatchRules rules) {
        for (FlagState flag : flags.values()) {
            if (flag.location != Location.DROPPED) continue;
            if (++flag.droppedTicks >= rules.ctfFlagReturnSeconds() * 20) {
                resetFlag(flag);
                announce(manager, Component.translatable("sfgame.ctf.returned", displayId(flag.key)));
            }
        }
    }

    private void handleInteractions(MatchManager manager, ArenaMap map, MatchRules rules) {
        for (FlagState flag : flags.values()) {
            // Pickups/recovery only apply while the flag is not carried. Delivery
            // must still run for carried flags; the old early continue skipped the
            // delivery path entirely, making classic CTF scores impossible.
            if (flag.location != Location.CARRIED) {
                for (ServerPlayer player : manager.onlineParticipants()) {
                    if (player.isSpectator() || manager.state(player).respawning()) continue;
                    // A player can carry only one flag at a time.  This also
                    // keeps the head-slot marker and its saved helmet unambiguous.
                    if (isCarrier(player.getUUID())) continue;
                    if (!nearFlag(flag, player)) continue;
                    TeamSide side = manager.teams().sideOf(player, manager.savedData());
                    if (side == TeamSide.NONE || !allowedToInteract(flag, side)) continue;
                    if (flag.location == Location.DROPPED && side == flag.owner) {
                        resetFlag(flag); announce(manager, Component.translatable("sfgame.ctf.recovered", displayId(flag.key)));
                        continue;
                    }
                    if (flag.location == Location.DEPOT && side == flag.owner && flag.forward != null) {
                        flag.location = Location.CARRIED; flag.carrier = player.getUUID(); flag.depotTeam = TeamSide.NONE;
                        equipCarrierAppearance(flag, player);
                        announce(manager, Component.translatable("sfgame.ctf.picked_up", displayId(flag.key)));
                        continue;
                    }
                    if ((flag.location == Location.STAND && flag.unlocked) || flag.location == Location.DROPPED) {
                        flag.location = Location.CARRIED; flag.carrier = player.getUUID(); flag.droppedPosition = null; flag.droppedTicks = 0;
                        equipCarrierAppearance(flag, player);
                        announce(manager, Component.translatable("sfgame.ctf.picked_up", displayId(flag.key)));
                    }
                }
            }
            if (flag.location == Location.CARRIED && flag.carrier != null) handleDelivery(flag, manager, map, rules);
        }
    }

    private void handleDelivery(FlagState flag, MatchManager manager, ArenaMap map, MatchRules rules) {
        ServerPlayer player = manager.serverPlayer(flag.carrier);
        if (player == null) return;
        TeamSide carrierSide = manager.teams().sideOf(player, manager.savedData());
        CtfHomeFlagDefinition carrierHome = map.captureTheFlag().homeOptional(carrierSide).orElse(null);
        if (carrierHome == null) return;
        if (flag.forward != null && carrierSide == flag.owner) {
            if (near(player, flag.forward.stand())) {
                manager.addTeamScore(carrierSide, 1); manager.addCurrency(player, 50);
                resetFlag(flag); announce(manager, Component.translatable("sfgame.ctf.forward_replanted", displayId(flag.key)));
            }
            return;
        }
        if (flag.forward != null && carrierSide != flag.owner && carrierHome.depotPosition() != null
                && near(player, carrierHome.depotPosition())) {
            manager.addTeamScore(carrierSide, 1); manager.addCurrency(player, 100);
            clearCarrierAppearance(flag);
            flag.location = Location.DEPOT; flag.depotTeam = carrierSide; flag.carrier = null; flag.droppedPosition = null;
            flag.droppedTicks = 0; flag.unlocked = false;
            announce(manager, Component.translatable("sfgame.ctf.captured", displayId(flag.key)));
            return;
        }
        if (flag.home && carrierSide != flag.owner && carrierHome.captureRegion() != null
                && carrierHome.captureRegion().contains(player)) {
            FlagState ownHome = flags.get(homeKey(carrierSide));
            if (ownHome == null || ownHome.location != Location.STAND) return;
            manager.addTeamScore(carrierSide, 1); manager.addCurrency(player, 100);
            resetFlag(flag); announce(manager, Component.translatable("sfgame.ctf.captured", displayId(flag.key)));
        }
    }

    private boolean allowedToInteract(FlagState flag, TeamSide side) {
        if (variant == CtfVariant.ASSAULT) {
            if (flag.owner != defender && flag.home) return false;
            if (side != attacker && flag.location == Location.STAND) return false;
        }
        if (flag.forward != null && flag.location == Location.STAND && side == flag.owner) return false;
        if (flag.location == Location.DEPOT) return side == flag.owner;
        return side != flag.owner || flag.location == Location.DROPPED;
    }

    private void dropCarriedFlag(ServerPlayer player, MatchManager manager) {
        for (FlagState flag : flags.values()) {
            if (flag.location == Location.CARRIED && player.getUUID().equals(flag.carrier)) {
                drop(flag, player.position(), player.level().dimension().location().toString());
                announce(manager, Component.translatable("sfgame.ctf.dropped", displayId(flag.key)));
            }
        }
    }

    private void drop(FlagState flag, @Nullable Vec3 position, @Nullable String dimension) {
        clearCarrierAppearance(flag);
        flag.location = Location.DROPPED; flag.carrier = null; flag.depotTeam = TeamSide.NONE;
        flag.droppedPosition = position == null ? standPosition(flag) : position;
        flag.droppedDimension = dimension; flag.droppedTicks = 0;
    }

    private void resetFlag(FlagState flag) {
        clearCarrierAppearance(flag);
        flag.location = Location.STAND; flag.carrier = null; flag.depotTeam = TeamSide.NONE; flag.droppedPosition = null;
        flag.droppedDimension = null; flag.droppedTicks = 0;
        flag.unlocked = flag.home && activeMap != null && variant != CtfVariant.TERRITORY;
        if (flag.pointState != null) flag.pointState.reset(flag.owner);
        if (flag.home && homeCapture.containsKey(flag.owner)) homeCapture.get(flag.owner).reset();
    }

    private void equipCarrierAppearance(FlagState flag, ServerPlayer player) {
        // Preserve the configured helmet so returning, dropping or dying with
        // a flag never deletes the player's armor.
        flag.carrierHelmet = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, carrierFlag(flag.owner));
        player.setGlowingTag(true);
    }

    private void clearCarrierAppearance(FlagState flag) {
        if (flag.carrier == null) {
            flag.carrierHelmet = ItemStack.EMPTY;
            return;
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(flag.carrier);
        if (player != null) {
            ItemStack equipped = player.getItemBySlot(EquipmentSlot.HEAD);
            if (isCarrierFlag(equipped)) player.setItemSlot(EquipmentSlot.HEAD,
                    flag.carrierHelmet == null ? ItemStack.EMPTY : flag.carrierHelmet.copy());
            player.setGlowingTag(false);
        }
        flag.carrierHelmet = ItemStack.EMPTY;
    }

    private void refreshDisplays(MatchManager manager, ArenaMap map) {
        Iterator<Map.Entry<String, ArmorStand>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ArmorStand> entry = iterator.next();
            if (!flags.containsKey(entry.getKey()) || !entry.getValue().isAlive()) { entry.getValue().discard(); iterator.remove(); }
        }
        for (FlagState flag : flags.values()) {
            // Carried flags are rendered directly in the carrier's helmet slot;
            // do not leave a second floating display entity above the player.
            if (flag.location == Location.CARRIED) {
                ArmorStand carriedDisplay = displays.remove(flag.key);
                if (carriedDisplay != null) carriedDisplay.discard();
                continue;
            }
            Vec3 position = displayPosition(flag);
            if (position == null) continue;
            ServerLevel level = levelFor(flag, position);
            if (level == null) continue;
            ArmorStand stand = displays.get(flag.key);
            if (stand == null || !stand.isAlive() || stand.level() != level) {
                if (stand != null) stand.discard();
                stand = new ArmorStand(level, position.x, position.y, position.z);
                // The invisible stand renders only its equipment, so the banner
                // is planted at the configured ground position without the white
                // glowing humanoid outline shown by a normal armor stand.
                stand.setInvisible(true); stand.setNoGravity(true);
                stand.setInvulnerable(true); stand.setSilent(true);
                stand.addTag(DISPLAY_TAG); stand.setCustomNameVisible(false);
                stand.setItemSlot(EquipmentSlot.HEAD, banner(flag.owner));
                if (level.addFreshEntity(stand)) displays.put(flag.key, stand);
            }
            stand.setPos(position.x, position.y, position.z);
        }
    }

    private void refreshBossBars(MatchManager manager, ArenaMap map, MatchRules rules) {
        if (variant != CtfVariant.TERRITORY) {
            bossBars.values().forEach(ServerBossEvent::removeAllPlayers); bossBars.clear(); return;
        }
        for (FlagState flag : flags.values()) {
            if (flag.home || flag.forward == null || flag.location != Location.STAND) {
                ServerBossEvent old = bossBars.remove(flag.key); if (old != null) old.removeAllPlayers(); continue;
            }
            ServerBossEvent bar = bossBars.computeIfAbsent(flag.key, ignored -> new ServerBossEvent(
                    Component.literal(displayId(flag.key)), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS));
            TeamSide color = flag.pointState == null || flag.pointState.owner() == TeamSide.NONE ? flag.owner : flag.pointState.owner();
            bar.setColor(color(color)); bar.setProgress((float) Math.max(0, Math.min(1, flag.pointState == null ? 0 : flag.pointState.progress())));
            String status = flag.pointState != null && flag.pointState.contested() ? "CONTESTED" : flag.status();
            bar.setName(Component.translatable("sfgame.ctf.forward_bossbar", displayId(flag.key), status));
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
                if (!manager.onlineMatchViewers().contains(player)) bar.removePlayer(player);
            }
            for (ServerPlayer player : manager.onlineMatchViewers()) bar.addPlayer(player);
        }
    }

    private void awardUnlock(TeamSide side, MatchManager manager) { manager.addCurrencyToTeamPlayers(side, 10); }

    private Map<TeamSide, Integer> countsIn(CaptureRegion region, MatchManager manager) {
        Map<TeamSide, Integer> counts = new EnumMap<>(TeamSide.class);
        for (ServerPlayer player : manager.onlineParticipants()) {
            if (player.isSpectator() || manager.state(player).respawning() || !region.contains(player)) continue;
            TeamSide side = manager.teams().sideOf(player, manager.savedData());
            if (side != TeamSide.NONE) counts.merge(side, 1, Integer::sum);
        }
        return counts;
    }

    private static TeamSide uniqueLeader(Map<TeamSide, Integer> counts) {
        int high = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (high <= 0 || counts.values().stream().filter(value -> value == high).count() != 1) return TeamSide.NONE;
        return counts.entrySet().stream().filter(entry -> entry.getValue() == high).findFirst().map(Map.Entry::getKey).orElse(TeamSide.NONE);
    }

    private static TeamSide uniqueLeaderExcluding(Map<TeamSide, Integer> counts, TeamSide excluded) {
        Map<TeamSide, Integer> filtered = new EnumMap<>(TeamSide.class); counts.forEach((side, count) -> { if (side != excluded) filtered.put(side, count); });
        return uniqueLeader(filtered);
    }

    private static boolean isTied(Map<TeamSide, Integer> counts) {
        int high = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return high > 0 && counts.values().stream().filter(value -> value == high).count() > 1;
    }

    private static boolean isTiedExcluding(Map<TeamSide, Integer> counts, TeamSide excluded) {
        Map<TeamSide, Integer> filtered = new EnumMap<>(TeamSide.class);
        counts.forEach((side, count) -> { if (side != excluded) filtered.put(side, count); });
        return isTied(filtered);
    }

    private void applyCarrierRestrictions(MatchManager manager, CarrierRestriction restriction) {
        if (restriction == CarrierRestriction.NORMAL) return;
        for (FlagState flag : flags.values()) if (flag.location == Location.CARRIED && flag.carrier != null) {
            ServerPlayer player = manager.serverPlayer(flag.carrier); if (player == null) continue;
            if (restriction == CarrierRestriction.MOVEMENT_LIMITED) player.setSprinting(false);
        }
    }

    private boolean nearFlag(FlagState flag, ServerPlayer player) {
        ServerLevel level = levelFor(flag, displayPosition(flag));
        Vec3 position = displayPosition(flag);
        return level != null && level == player.serverLevel() && position != null
                && player.distanceToSqr(position.x, position.y, position.z) <= 3.5 * 3.5;
    }
    private boolean near(ServerPlayer player, ArenaPosition target) {
        if (target == null || !player.level().dimension().location().toString().equals(target.dimension())) return false;
        return Math.pow(player.getX() - target.x(), 2) + Math.pow(player.getY() - target.y(), 2)
                + Math.pow(player.getZ() - target.z(), 2) <= 3.5 * 3.5;
    }
    private Vec3 displayPosition(FlagState flag) {
        if (flag.location == Location.CARRIED && flag.carrier != null && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(flag.carrier);
            return player == null ? null : player.position().add(0, 2.4, 0);
        }
        if (flag.location == Location.DROPPED) return flag.droppedPosition;
        if (flag.location == Location.DEPOT && flag.depotTeam != TeamSide.NONE && activeMap != null) {
            CtfHomeFlagDefinition home = activeMap.captureTheFlag().homeOptional(flag.depotTeam).orElse(null);
            if (home != null && home.depotPosition() != null) return vec(home.depotPosition());
        }
        return standPosition(flag);
    }
    private Vec3 standPosition(FlagState flag) {
        return flag.home && flag.homeDefinition != null ? vec(flag.homeDefinition.flagPosition())
                : flag.forward == null ? null : vec(flag.forward.stand());
    }
    private ServerLevel levelFor(FlagState flag, Vec3 ignored) {
        if (flag.location == Location.CARRIED && flag.carrier != null && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(flag.carrier); return player == null ? null : player.serverLevel();
        }
        ArenaPosition position = flag.location == Location.DEPOT && activeMap != null && flag.depotTeam != TeamSide.NONE
                ? activeMap.captureTheFlag().homeOptional(flag.depotTeam).map(CtfHomeFlagDefinition::depotPosition).orElse(null)
                : flag.location == Location.DROPPED && flag.droppedDimension != null
                ? new ArenaPosition(flag.droppedDimension, 0, 0, 0, 0, 0)
                : flag.home && flag.homeDefinition != null ? flag.homeDefinition.flagPosition() : flag.forward == null ? null : flag.forward.stand();
        if (position == null || server == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(position.dimension());
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static Vec3 vec(ArenaPosition position) { return position == null ? null : new Vec3(position.x(), position.y(), position.z()); }
    private static String homeKey(TeamSide side) { return "home_" + side.id(); }
    private static String displayId(String id) { return id.toUpperCase(Locale.ROOT); }
    private static Component teamName(TeamSide side) { return Component.translatable("sfgame.team." + side.id()); }
    private static BossEvent.BossBarColor color(TeamSide side) { return switch (side) {
        case RED -> BossEvent.BossBarColor.RED; case BLUE -> BossEvent.BossBarColor.BLUE;
        case YELLOW -> BossEvent.BossBarColor.YELLOW; case GREEN -> BossEvent.BossBarColor.GREEN; case NONE -> BossEvent.BossBarColor.WHITE;
    }; }
    private static ItemStack banner(TeamSide side) { return new ItemStack(switch (side) {
        case RED -> Blocks.RED_BANNER; case BLUE -> Blocks.BLUE_BANNER; case YELLOW -> Blocks.YELLOW_BANNER; case GREEN -> Blocks.GREEN_BANNER; default -> Blocks.WHITE_BANNER;
    }); }
    private static ItemStack carrierFlag(TeamSide side) {
        ItemStack stack = banner(side);
        stack.getOrCreateTag().putBoolean(CARRIER_FLAG_TAG, true);
        return stack;
    }
    private static boolean isCarrierFlag(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(CARRIER_FLAG_TAG);
    }
    private static void checkPosition(MinecraftServer server, @Nullable ArenaPosition position, String label, List<String> errors) {
        if (position == null) { errors.add(label + " is not set"); return; }
        ResourceLocation id = ResourceLocation.tryParse(position.dimension());
        if (id == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) == null) errors.add(label + " uses an unavailable dimension");
    }
    private static void checkRegion(MinecraftServer server, @Nullable CaptureRegion region, String label, List<String> errors) {
        if (region == null) { errors.add(label + " is not set"); return; }
        ResourceLocation id = ResourceLocation.tryParse(region.dimension());
        if (id == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) == null) errors.add(label + " uses an unavailable dimension");
    }
    private static void announce(MatchManager manager, Component message) {
        for (ServerPlayer player : manager.onlineParticipants()) {
            player.sendSystemMessage(message, true);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.MASTER, 0.8F, 1.15F);
        }
    }

    public record FlagView(String id, TeamSide owner, String state, String carrier, boolean unlocked, TeamSide depotTeam) { }

    private static final class FlagState {
        private final String key;
        private final TeamSide owner;
        private final boolean home;
        @Nullable private final CtfHomeFlagDefinition homeDefinition;
        @Nullable private final CtfForwardFlagDefinition forward;
        private final CapturePointState pointState;
        private Location location = Location.STAND;
        @Nullable private UUID carrier;
        @Nullable private Vec3 droppedPosition;
        @Nullable private String droppedDimension;
        private ItemStack carrierHelmet = ItemStack.EMPTY;
        private TeamSide depotTeam = TeamSide.NONE;
        private int droppedTicks;
        private boolean unlocked;

        private FlagState(String key, TeamSide owner, boolean home, @Nullable CtfHomeFlagDefinition homeDefinition,
                          @Nullable CtfForwardFlagDefinition forward) {
            this.key = key; this.owner = owner; this.home = home; this.homeDefinition = homeDefinition; this.forward = forward;
            this.pointState = forward == null ? null : new CapturePointState();
        }
        static FlagState home(CtfHomeFlagDefinition definition) { return new FlagState(homeKey(definition.team()), definition.team(), true, definition, null); }
        static FlagState forward(CtfForwardFlagDefinition definition) { return new FlagState(definition.id(), definition.owner(), false, null, definition); }
        void reset() { location = Location.STAND; carrier = null; droppedPosition = null; droppedDimension = null; carrierHelmet = ItemStack.EMPTY; depotTeam = TeamSide.NONE; droppedTicks = 0; unlocked = false; if (pointState != null) pointState.reset(owner); }
        String status() { return location == Location.STAND && !unlocked ? "LOCKED" : location.name(); }
    }
}

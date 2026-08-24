package com.sfgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.MatchRules;
import com.sfgame.data.SFGameSavedData;
import com.sfgame.data.BoxCaptureRegion;
import com.sfgame.data.BlockAllowlist;
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.CaptureRegion;
import com.sfgame.data.SquareCaptureRegion;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.BreakthroughSectorDefinition;
import com.sfgame.data.BreakthroughVehicleDefinition;
import com.sfgame.data.CtfForwardFlagDefinition;
import com.sfgame.data.CtfHomeFlagDefinition;
import com.sfgame.data.CaptureTheFlagMapConfig;
import com.sfgame.game.BreakthroughRuntime;
import com.sfgame.game.GameModeDefinition;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.MatchManager;
import com.sfgame.game.MatchPhase;
import com.sfgame.game.AdminRuleCatalog;
import com.sfgame.game.TeamSide;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SFGameCommands {
    private static final Map<UUID, ArenaPosition> POINT_POS_1 = new HashMap<>();
    private static final Map<UUID, ArenaPosition> POINT_POS_2 = new HashMap<>();
    private static final Map<UUID, ArenaPosition> CTF_POS_1 = new HashMap<>();
    private static final Map<UUID, ArenaPosition> CTF_POS_2 = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);
    private static final SuggestionProvider<CommandSourceStack> ENTITY_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString), builder);
    private static final SuggestionProvider<CommandSourceStack> ITEM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(Object::toString), builder);
    private static final SuggestionProvider<CommandSourceStack> VEHICLE_ROLE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("attacker", "defender"), builder);
    private static final SuggestionProvider<CommandSourceStack> CLASS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(MatchManager.get().classes().allForMode(
                    SFGameSavedData.get(context.getSource().getServer()).selectedMode(),
                    SFGameSavedData.get(context.getSource().getServer()).selectedMap()).stream().map(ClassDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> CAPTAIN_CLASS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(MatchManager.get().classes().captainClassesForMode(
                    SFGameSavedData.get(context.getSource().getServer()).selectedMode(),
                    SFGameSavedData.get(context.getSource().getServer()).selectedMap()).stream().map(ClassDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SUPPLY_TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(TeamSide.PLAYABLE.stream().map(TeamSide::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SUPPLY_PRESET_SUGGESTIONS = (context, builder) -> {
        ArenaMap map = SFGameSavedData.get(context.getSource().getServer()).activeMap();
        return SharedSuggestionProvider.suggest(map == null ? java.util.stream.Stream.empty()
                : map.supply().offers().stream().map(com.sfgame.data.SupplyOfferDefinition::id), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> ELITE_CLASS_SUGGESTIONS = (context, builder) -> {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        TeamSide side = TeamSide.fromId(StringArgumentType.getString(context, "team"));
        return SharedSuggestionProvider.suggest(MatchManager.get().classes().eliteClassesForTeam(
                data.selectedMode(), data.selectedMap(), side).stream().map(ClassDefinition::id), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> RULE_SUGGESTIONS = (context, builder) -> {
        String mode = SFGameSavedData.get(context.getSource().getServer()).selectedMode();
        return SharedSuggestionProvider.suggest(AdminRuleCatalog.forMode(mode).stream()
                .map(AdminRuleCatalog.Definition::key), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> RULE_VALUE_SUGGESTIONS = (context, builder) -> {
        String key = StringArgumentType.getString(context, "key");
        String mode = SFGameSavedData.get(context.getSource().getServer()).selectedMode();
        return AdminRuleCatalog.find(mode, key).map(definition -> switch (definition.type()) {
            case BOOLEAN -> SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
            case ENUM -> SharedSuggestionProvider.suggest(AdminRuleCatalog.enumValues(key), builder);
            default -> builder.buildFuture();
        }).orElseGet(builder::buildFuture);
    };
    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(GameModeRegistry.all().stream().map(GameModeDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).maps().stream()
                    .map(ArenaMap::id), builder);
    private static final SuggestionProvider<CommandSourceStack> RULE_PARENT_SUGGESTIONS = (context, builder) -> {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        java.util.stream.Stream<String> canonical = GameModeRegistry.all().stream().flatMap(mode ->
                java.util.stream.Stream.concat(java.util.stream.Stream.of(mode.id() + "/base"),
                        data.maps(mode.id()).stream().map(map -> mode.id() + "/" + map.id())));
        java.util.stream.Stream<String> local = java.util.stream.Stream.concat(java.util.stream.Stream.of("base"),
                data.maps().stream().map(ArenaMap::id));
        return SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(local, canonical), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> BREAKTHROUGH_VEHICLE_SUGGESTIONS = (context, builder) -> {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        return SharedSuggestionProvider.suggest(data.activeMap() == null ? java.util.stream.Stream.empty()
                : data.activeMap().breakthrough().vehicles().stream().map(BreakthroughVehicleDefinition::id), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).activeMap() == null
                    ? java.util.stream.Stream.empty() : SFGameSavedData.get(context.getSource().getServer()).activeMap()
                    .domination().points().stream().map(CapturePointDefinition::id), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("sfgame")
                .then(Commands.literal("menu").executes(SFGameCommands::menu))
                .then(Commands.literal("leave").executes(SFGameCommands::leave))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(SFGameCommands::status))
                .then(scoreCommands().requires(s -> s.hasPermission(2)))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(SFGameCommands::start))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(SFGameCommands::stop))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(SFGameCommands::reset))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(SFGameCommands::reload))
                .then(Commands.literal("dev").requires(s -> s.hasPermission(2)).executes(SFGameCommands::toggleDev))
                .then(Commands.literal("joinnow").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(SFGameCommands::joinNow)))
                // All map region tools share these two selection commands.  The
                // selected mode decides which in-memory selection is updated.
                .then(Commands.literal("pos1").requires(s -> s.hasPermission(2))
                        .executes(c -> universalPosition(c, true)))
                .then(Commands.literal("pos2").requires(s -> s.hasPermission(2))
                        .executes(c -> universalPosition(c, false)))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("setdefault")
                                .then(Commands.literal("lobby").executes(SFGameCommands::setDefaultLobby)))
                        .then(Commands.literal("set")
                                .then(Commands.literal("lobby").executes(c -> setPosition(c, "lobby")))
                                .then(Commands.literal("red").executes(c -> setPosition(c, "red")))
                                .then(Commands.literal("blue").executes(c -> setPosition(c, "blue")))
                                .then(Commands.literal("yellow").executes(c -> setPosition(c, "yellow")))
                                .then(Commands.literal("green").executes(c -> setPosition(c, "green"))))
                        .then(Commands.literal("list")
                                .then(Commands.literal("red").executes(c -> spawnList(c, TeamSide.RED)))
                                .then(Commands.literal("blue").executes(c -> spawnList(c, TeamSide.BLUE)))
                                .then(Commands.literal("yellow").executes(c -> spawnList(c, TeamSide.YELLOW)))
                                .then(Commands.literal("green").executes(c -> spawnList(c, TeamSide.GREEN))))
                        .then(Commands.literal("remove")
                                .then(Commands.literal("red").then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(c -> spawnRemove(c, TeamSide.RED))))
                                .then(Commands.literal("blue").then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(c -> spawnRemove(c, TeamSide.BLUE))))
                                .then(Commands.literal("yellow").then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(c -> spawnRemove(c, TeamSide.YELLOW))))
                                .then(Commands.literal("green").then(Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(c -> spawnRemove(c, TeamSide.GREEN)))))
                        .then(Commands.literal("clear")
                                .then(Commands.literal("lobby").executes(SFGameCommands::clearLobby))
                                .then(Commands.literal("red").executes(c -> spawnClear(c, TeamSide.RED)))
                                .then(Commands.literal("blue").executes(c -> spawnClear(c, TeamSide.BLUE)))
                                .then(Commands.literal("yellow").executes(c -> spawnClear(c, TeamSide.YELLOW)))
                                .then(Commands.literal("green").executes(c -> spawnClear(c, TeamSide.GREEN)))))
                .then(Commands.literal("point").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.literal("box").then(Commands.argument("point", StringArgumentType.word())
                                        .executes(SFGameCommands::pointAddBox)))
                                .then(Commands.literal("square").then(Commands.argument("point", StringArgumentType.word())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                                .executes(SFGameCommands::pointAddSquare)))))
                        .then(Commands.literal("set")
                                .then(Commands.literal("box").then(Commands.argument("point", StringArgumentType.word())
                                        .suggests(POINT_SUGGESTIONS).executes(SFGameCommands::pointSetBox)))
                                .then(Commands.literal("center").then(Commands.argument("point", StringArgumentType.word())
                                        .suggests(POINT_SUGGESTIONS).executes(SFGameCommands::pointSetCenter)))
                                .then(Commands.literal("radius").then(Commands.argument("point", StringArgumentType.word())
                                        .suggests(POINT_SUGGESTIONS).then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                                .executes(SFGameCommands::pointSetRadius))))
                                .then(Commands.literal("height").then(Commands.argument("point", StringArgumentType.word())
                                        .suggests(POINT_SUGGESTIONS)
                                        .then(Commands.literal("full").executes(c -> pointSetHeight(c, true)))
                                        .then(Commands.argument("minY", IntegerArgumentType.integer(-2048, 2048))
                                                .then(Commands.argument("maxY", IntegerArgumentType.integer(-2048, 2048))
                                                        .executes(c -> pointSetHeight(c, false))))))
                                .then(Commands.literal("order").then(Commands.argument("point", StringArgumentType.word())
                                        .suggests(POINT_SUGGESTIONS).then(Commands.argument("order", IntegerArgumentType.integer(1, 16))
                                                .executes(SFGameCommands::pointSetOrder)))))
                        .then(Commands.literal("list").executes(SFGameCommands::pointList))
                        .then(Commands.literal("status").then(Commands.argument("point", StringArgumentType.word())
                                .suggests(POINT_SUGGESTIONS).executes(SFGameCommands::pointStatus)))
                        .then(Commands.literal("remove").then(Commands.argument("point", StringArgumentType.word())
                                .suggests(POINT_SUGGESTIONS).executes(SFGameCommands::pointRemove)))
                        .then(Commands.literal("clear").executes(SFGameCommands::pointClear)))
                .then(Commands.literal("mode").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(SFGameCommands::modeList))
                        .then(Commands.literal("status").executes(SFGameCommands::modeStatus))
                        .then(Commands.literal("select").then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(MODE_SUGGESTIONS).executes(SFGameCommands::modeSelect))))
                .then(Commands.literal("map").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(SFGameCommands::mapList))
                        .then(Commands.literal("status").executes(SFGameCommands::mapStatus))
                        .then(Commands.literal("create").then(Commands.argument("map", StringArgumentType.word())
                                .executes(SFGameCommands::mapCreate)))
                        .then(Commands.literal("select").then(Commands.argument("map", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS).executes(SFGameCommands::mapSelect)))
                        .then(Commands.literal("remove").then(Commands.argument("map", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS).executes(SFGameCommands::mapRemove))))
                .then(Commands.literal("team").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("status").executes(SFGameCommands::teamStatus))
                        .then(Commands.literal("bind")
                                .then(Commands.literal("red").then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_SUGGESTIONS).executes(c -> bindTeam(c, TeamSide.RED))))
                                .then(Commands.literal("blue").then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_SUGGESTIONS).executes(c -> bindTeam(c, TeamSide.BLUE))))
                                .then(Commands.literal("yellow").then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_SUGGESTIONS).executes(c -> bindTeam(c, TeamSide.YELLOW))))
                                .then(Commands.literal("green").then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_SUGGESTIONS).executes(c -> bindTeam(c, TeamSide.GREEN)))))
                        .then(Commands.literal("set").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("red").executes(c -> setTeam(c, TeamSide.RED)))
                                .then(Commands.literal("blue").executes(c -> setTeam(c, TeamSide.BLUE)))
                                .then(Commands.literal("yellow").executes(c -> setTeam(c, TeamSide.YELLOW)))
                                .then(Commands.literal("green").executes(c -> setTeam(c, TeamSide.GREEN)))
                                .then(Commands.literal("random").executes(c -> setTeam(c, TeamSide.NONE)))))
                        // Also accept the natural `/team set random @a` order;
                        // the documented players-first form remains supported.
                        .then(Commands.literal("random").then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> setTeam(c, TeamSide.NONE))))
                        .then(Commands.literal("remove").then(Commands.argument("players", EntityArgument.players())
                                .executes(SFGameCommands::removeTeam))))
                .then(ruleCommands("rule", buildContext))
                .then(Commands.literal("class").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reload").executes(SFGameCommands::classReload))
                        .then(Commands.literal("validate").executes(SFGameCommands::classValidate))
                        .then(Commands.literal("list").executes(SFGameCommands::classList)
                                .then(Commands.literal("normal").executes(SFGameCommands::classList))
                                .then(Commands.literal("captain").executes(SFGameCommands::classListCaptain)))
                        .then(Commands.literal("listcaptain").executes(SFGameCommands::classListCaptain))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASS_SUGGESTIONS)
                                        .executes(SFGameCommands::classSet))))
                        .then(Commands.literal("setcaptain").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CAPTAIN_CLASS_SUGGESTIONS)
                                        .executes(SFGameCommands::classSetCaptain)))))
                .then(shopCommands())
                .then(supplyCommands().requires(source -> source.hasPermission(2)))
                .then(sectorCommands())
                .then(captainCommands()));
    }

    static LiteralArgumentBuilder<CommandSourceStack> scoreCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("score")
                .executes(SFGameCommands::scoreStatus);
        root.then(Commands.literal("time")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(
                                0, MatchManager.MAX_LIVE_TIME_SECONDS))
                        .executes(SFGameCommands::scoreTime)));
        root.then(Commands.literal("currency").requires(source -> scoreFieldVisible(source, "currency"))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                        0, MatchManager.MAX_LIVE_SCORE))
                                .executes(SFGameCommands::scoreCurrency))));
        root.then(Commands.literal("tickets").requires(source -> scoreFieldVisible(source, "tickets"))
                .then(Commands.argument("value", IntegerArgumentType.integer(
                                0, MatchManager.MAX_LIVE_SCORE))
                        .executes(SFGameCommands::scoreTickets)));
        root.then(Commands.literal("leg").requires(source -> scoreFieldVisible(source, "leg"))
                .then(Commands.argument("value", IntegerArgumentType.integer(1, MatchManager.MAX_LIVE_LEG))
                        .executes(SFGameCommands::scoreLeg)));
        root.then(Commands.literal("sector").requires(source -> scoreFieldVisible(source, "sector"))
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                        .executes(SFGameCommands::scoreSector)));
        root.then(scoreTeamCommand(TeamSide.RED));
        root.then(scoreTeamCommand(TeamSide.BLUE));
        root.then(scoreTeamCommand(TeamSide.YELLOW));
        root.then(scoreTeamCommand(TeamSide.GREEN));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scoreTeamCommand(TeamSide side) {
        return Commands.literal(side.id()).requires(source -> scoreFieldVisible(source, side.id()))
                .then(Commands.argument("value", IntegerArgumentType.integer(
                                0, MatchManager.MAX_LIVE_SCORE))
                        .executes(context -> scoreTeam(context, side)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> breakthroughCommands() {
        return Commands.literal("breakthrough")
                .requires(source -> source.hasPermission(2)
                        && modeIs(source, GameModeRegistry.BREAKTHROUGH))
                .then(breakthroughVehicleCommands())
                .then(Commands.literal("status").executes(SFGameCommands::breakthroughStatus));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> breakthroughVehicleCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vehicle");
        root.then(Commands.literal("add")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("entity", ResourceLocationArgument.id()).suggests(ENTITY_TYPE_SUGGESTIONS)
                                .then(Commands.argument("role", StringArgumentType.word()).suggests(VEHICLE_ROLE_SUGGESTIONS)
                                        .then(Commands.argument("respawnSeconds", IntegerArgumentType.integer(1, 3600))
                                                .executes(SFGameCommands::breakthroughVehicleAdd))))));
        root.then(Commands.literal("set")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                        .executes(SFGameCommands::breakthroughVehicleSetPosition))
                .then(Commands.literal("entity")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("entity", ResourceLocationArgument.id()).suggests(ENTITY_TYPE_SUGGESTIONS)
                                        .executes(SFGameCommands::breakthroughVehicleSetEntity))))
                .then(Commands.literal("role")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("role", StringArgumentType.word()).suggests(VEHICLE_ROLE_SUGGESTIONS)
                                        .executes(SFGameCommands::breakthroughVehicleSetRole))))
                .then(Commands.literal("interval")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("respawnSeconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(SFGameCommands::breakthroughVehicleSetInterval))))
                .then(Commands.literal("offset")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("yOffset", DoubleArgumentType.doubleArg(-64.0D, 64.0D))
                                        .executes(SFGameCommands::breakthroughVehicleSetOffset))))
                .then(Commands.literal("energy")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(SFGameCommands::breakthroughVehicleSetEnergy))))
                .then(Commands.literal("ammo")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.literal("none").executes(SFGameCommands::breakthroughVehicleClearAmmo))
                                .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEM_SUGGESTIONS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                        BreakthroughVehicleDefinition.MAX_AMMO_COUNT))
                                                .executes(SFGameCommands::breakthroughVehicleReplaceAmmo))))));
        root.then(Commands.literal("ammo")
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEM_SUGGESTIONS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1,
                                                        BreakthroughVehicleDefinition.MAX_AMMO_COUNT))
                                                .executes(SFGameCommands::breakthroughVehicleAddAmmo)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEM_SUGGESTIONS)
                                        .executes(SFGameCommands::breakthroughVehicleRemoveAmmo))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .executes(SFGameCommands::breakthroughVehicleClearAmmo)))
                .then(Commands.literal("list")
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS)
                                .executes(SFGameCommands::breakthroughVehicleListAmmo))));
        root.then(Commands.literal("list").executes(SFGameCommands::breakthroughVehicleList));
        root.then(Commands.literal("status").then(Commands.argument("id", StringArgumentType.word())
                .suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS).executes(SFGameCommands::breakthroughVehicleStatus)));
        root.then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word())
                .suggests(BREAKTHROUGH_VEHICLE_SUGGESTIONS).executes(SFGameCommands::breakthroughVehicleRemove)));
        root.then(Commands.literal("clear").executes(SFGameCommands::breakthroughVehicleClear));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ctfCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ctf")
                .requires(source -> source.hasPermission(2)
                        && modeIs(source, GameModeRegistry.CAPTURE_THE_FLAG));
        root.then(Commands.literal("status").executes(SFGameCommands::ctfStatus));
        LiteralArgumentBuilder<CommandSourceStack> home = Commands.literal("home");
        home.then(Commands.literal("set").then(Commands.argument("team", StringArgumentType.word())
                .then(Commands.literal("flag").executes(c -> ctfHomeSetPosition(c, "flag")))
                .then(Commands.literal("depot").executes(c -> ctfHomeSetPosition(c, "depot")))
                .then(Commands.literal("capture").then(Commands.literal("box").executes(c -> ctfHomeSetCapture(c, false)))
                        .then(Commands.literal("square").then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                .executes(c -> ctfHomeSetCapture(c, true)))))));
        home.then(Commands.literal("clear").then(Commands.argument("team", StringArgumentType.word())
                .then(Commands.literal("flag").executes(c -> ctfHomeClear(c, "flag")))
                .then(Commands.literal("capture").executes(c -> ctfHomeClear(c, "capture")))
                .then(Commands.literal("depot").executes(c -> ctfHomeClear(c, "depot")))));
        home.then(Commands.literal("list").executes(SFGameCommands::ctfHomeList));
        root.then(home);

        LiteralArgumentBuilder<CommandSourceStack> forward = Commands.literal("forward");
        forward.then(Commands.literal("add").then(Commands.literal("box").then(Commands.argument("owner", StringArgumentType.word())
                .then(Commands.argument("id", StringArgumentType.word()).executes(SFGameCommands::ctfForwardAddBox)))));
        forward.then(Commands.literal("add").then(Commands.literal("square").then(Commands.argument("owner", StringArgumentType.word())
                .then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(SFGameCommands::ctfForwardAddSquare))))));
        forward.then(Commands.literal("set").then(Commands.literal("box").then(Commands.argument("id", StringArgumentType.word())
                .executes(SFGameCommands::ctfForwardSetBox))));
        forward.then(Commands.literal("set").then(Commands.literal("center").then(Commands.argument("id", StringArgumentType.word())
                .executes(SFGameCommands::ctfForwardSetCenter))));
        forward.then(Commands.literal("set").then(Commands.literal("radius").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(SFGameCommands::ctfForwardSetRadius)))));
        forward.then(Commands.literal("set").then(Commands.literal("height").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.literal("full").executes(c -> ctfForwardSetHeight(c, true)))
                .then(Commands.argument("minY", IntegerArgumentType.integer(-2048, 2048)).then(Commands.argument("maxY", IntegerArgumentType.integer(-2048, 2048))
                        .executes(c -> ctfForwardSetHeight(c, false)))))));
        forward.then(Commands.literal("set").then(Commands.literal("stand").then(Commands.argument("id", StringArgumentType.word())
                .executes(SFGameCommands::ctfForwardSetStand))));
        forward.then(Commands.literal("list").executes(SFGameCommands::ctfForwardList));
        forward.then(Commands.literal("status").then(Commands.argument("id", StringArgumentType.word()).executes(SFGameCommands::ctfForwardStatus)));
        forward.then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).executes(SFGameCommands::ctfForwardRemove)));
        forward.then(Commands.literal("clear").executes(SFGameCommands::ctfForwardClear));
        root.then(forward);

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mapBuildCommands(CommandBuildContext buildContext) {
        return Commands.literal("build")
                .then(Commands.literal("setbox")
                        .executes(context -> ctfBuildSetBox(context, false))
                        .then(Commands.literal("full").executes(context -> ctfBuildSetBox(context, true))))
                .then(Commands.literal("clear")
                        .then(Commands.literal("snapshot").executes(SFGameCommands::ctfSnapshotClear))
                        .then(Commands.literal("setbox").executes(SFGameCommands::ctfBuildClear))
                        .then(Commands.literal("all").executes(SFGameCommands::ctfBuildClearAll)))
                .then(Commands.literal("status").executes(SFGameCommands::ctfBuildStatus))
                .then(Commands.literal("allow").then(Commands.argument("block",
                        ResourceOrTagArgument.resourceOrTag(buildContext, net.minecraft.core.registries.Registries.BLOCK))
                        .executes(SFGameCommands::ctfBuildAllow)))
                .then(Commands.literal("disallow").then(Commands.argument("block",
                        ResourceOrTagArgument.resourceOrTag(buildContext, net.minecraft.core.registries.Registries.BLOCK))
                        .executes(SFGameCommands::ctfBuildDisallow)))
                .then(Commands.literal("allowlist").executes(SFGameCommands::ctfBuildAllowList))
                .then(Commands.literal("snapshot").then(Commands.literal("save").executes(SFGameCommands::ctfSnapshotSave))
                        .then(Commands.literal("restore").executes(SFGameCommands::ctfSnapshotRestore)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shopCommands() {
        return Commands.literal("shop")
                .then(Commands.literal("list").executes(SFGameCommands::shopList))
                .then(Commands.literal("buy").then(Commands.argument("item", StringArgumentType.word())
                        .executes(SFGameCommands::shopBuy)))
                .then(Commands.literal("reload").requires(source -> source.hasPermission(2))
                        .executes(SFGameCommands::shopReload));
    }
    static LiteralArgumentBuilder<CommandSourceStack> supplyCommands() {
        var team = StringArgumentType.word();
        return Commands.literal("supply")
                .then(Commands.literal("list").executes(context -> supplyList(context, TeamSide.NONE))
                        .then(Commands.argument("team", team).suggests(SUPPLY_TEAM_SUGGESTIONS)
                                .executes(context -> supplyList(context,
                                        TeamSide.fromId(StringArgumentType.getString(context, "team"))))))
                .then(Commands.literal("push")
                        .then(Commands.literal("preset")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUPPLY_TEAM_SUGGESTIONS)
                                        .then(Commands.argument("offerId", StringArgumentType.word())
                                                .suggests(SUPPLY_PRESET_SUGGESTIONS)
                                                .executes(context -> supplyPushPreset(context, 1))
                                                .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 100_000))
                                                        .executes(context -> supplyPushPreset(context,
                                                                IntegerArgumentType.getInteger(context, "quantity")))))))
                        .then(Commands.literal("item")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUPPLY_TEAM_SUGGESTIONS)
                                        .then(Commands.argument("offerId", StringArgumentType.word())
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 100_000))
                                                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                                                        .suggests(ITEM_SUGGESTIONS)
                                                                        .executes(SFGameCommands::supplyPushItem)))))))
                        .then(Commands.literal("elite")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SUPPLY_TEAM_SUGGESTIONS)
                                        .then(Commands.argument("offerId", StringArgumentType.word())
                                                .then(Commands.argument("classId", StringArgumentType.word())
                                                        .suggests(ELITE_CLASS_SUGGESTIONS)
                                                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 100_000))
                                                                .executes(SFGameCommands::supplyPushElite)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(SUPPLY_TEAM_SUGGESTIONS)
                                .then(Commands.argument("offerId", StringArgumentType.word())
                                        .executes(SFGameCommands::supplyRemove))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(SUPPLY_TEAM_SUGGESTIONS)
                                .executes(SFGameCommands::supplyClear)));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> ruleCommands(String literal,
                                                                            CommandBuildContext buildContext) {
        return Commands.literal(literal).requires(s -> s.hasPermission(2))
                .then(breakthroughCommands())
                .then(ctfCommands())
                .then(mapBuildCommands(buildContext))
                .then(Commands.literal("list").executes(SFGameCommands::rulesList))
                .then(Commands.literal("reset").executes(SFGameCommands::rulesReset))
                .then(Commands.literal("inherit").then(Commands.argument("parent", StringArgumentType.word())
                        .suggests(RULE_PARENT_SUGGESTIONS).executes(SFGameCommands::rulesInherit)))
                .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.word())
                        .suggests(RULE_SUGGESTIONS).executes(SFGameCommands::rulesGet)))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word()).suggests(RULE_SUGGESTIONS)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(RULE_VALUE_SUGGESTIONS).executes(SFGameCommands::rulesSetGeneric))));
    }

    private static boolean modeIs(CommandSourceStack source, String... modes) {
        String selected = SFGameSavedData.get(source.getServer()).selectedMode();
        return java.util.Arrays.stream(modes).anyMatch(selected::equals);
    }

    private static int ctfStatus(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode()) || data.activeMap() == null) return failure(context, "Select ctf mode first");
        var config = data.activeMap().captureTheFlag();
        MatchRules rules = MatchManager.get().rules();
        send(context, "variant=" + rules.ctfVariant().id() + ", carrierRestriction=" + rules.ctfCarrierRestriction().id()
                + ", attacker=" + rules.ctfAttacker().id() + ", defender=" + rules.ctfDefender().id()
                + ", homes=" + config.homes().size() + ", forwardFlags=" + config.forwardFlags().size());
        return config.validate(data.activeMap().enabledTeams(), rules.ctfVariant(), rules.ctfAttacker(),
                rules.ctfDefender()).isEmpty() ? 1 : 0;
    }

    private static int ctfPosition(CommandContext<CommandSourceStack> context, boolean first)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        (first ? CTF_POS_1 : CTF_POS_2).put(player.getUUID(), ArenaPosition.from(player));
        return success(context, "Set CTF pos" + (first ? "1" : "2"));
    }

    private static int ctfHomeSetPosition(CommandContext<CommandSourceStack> context, String type)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        TeamSide side = TeamSide.fromId(StringArgumentType.getString(context, "team"));
        if (side == TeamSide.NONE) return failure(context, "Unknown team");
        CtfHomeFlagDefinition home = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().home(side);
        ArenaPosition position = ArenaPosition.from(context.getSource().getPlayerOrException());
        if ("flag".equals(type)) home.flagPosition(position); else home.depotPosition(position);
        dirty(context); return success(context, "Set " + side.id() + " home " + type + " position");
    }

    private static int ctfHomeSetCapture(CommandContext<CommandSourceStack> context, boolean square)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        TeamSide side = TeamSide.fromId(StringArgumentType.getString(context, "team"));
        if (side == TeamSide.NONE) return failure(context, "Unknown team");
        CaptureRegion region;
        try {
            if (square) region = SquareCaptureRegion.centeredAt(ArenaPosition.from(context.getSource().getPlayerOrException()),
                    IntegerArgumentType.getInteger(context, "radius"));
            else {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ArenaPosition first = CTF_POS_1.get(player.getUUID()), second = CTF_POS_2.get(player.getUUID());
                if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
                if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
                region = selectedBlockBox(first, second, null, null);
            }
            var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
            config.validateHomeCaptureRegion(side, region);
            config.home(side).captureRegion(region);
            dirty(context); return success(context, "Set " + side.id() + " capture region");
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfHomeClear(CommandContext<CommandSourceStack> context, String type) {
        if (!checkCtfEdit(context)) return 0;
        TeamSide side = TeamSide.fromId(StringArgumentType.getString(context, "team"));
        if (side == TeamSide.NONE) return failure(context, "Unknown team");
        CtfHomeFlagDefinition home = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().home(side);
        if ("flag".equals(type)) home.flagPosition(null); else if ("capture".equals(type)) home.captureRegion(null); else home.depotPosition(null);
        dirty(context); return success(context, "Cleared " + side.id() + " home " + type);
    }

    private static int ctfHomeList(CommandContext<CommandSourceStack> context) {
        if (!checkCtf(context)) return 0;
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
        config.homes().forEach(home -> send(context, home.team().id() + ": flag=" + (home.flagPosition() != null)
                + ", capture=" + (home.captureRegion() != null) + ", depot=" + (home.depotPosition() != null)));
        return config.homes().size();
    }

    private static int ctfForwardAddBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = CTF_POS_1.get(player.getUUID()), second = CTF_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        CaptureRegion region = selectedBlockBox(first, second, null, null);
        return ctfForwardAdd(context, region, player);
    }

    private static int ctfForwardAddSquare(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        CaptureRegion region = SquareCaptureRegion.centeredAt(ArenaPosition.from(player), IntegerArgumentType.getInteger(context, "radius"));
        return ctfForwardAdd(context, region, player);
    }

    private static int ctfForwardAdd(CommandContext<CommandSourceStack> context, CaptureRegion region, ServerPlayer player) {
        TeamSide owner = TeamSide.fromId(StringArgumentType.getString(context, "owner"));
        if (owner == TeamSide.NONE) return failure(context, "Unknown owner team");
        String id = StringArgumentType.getString(context, "id");
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
        int order = config.forwardFlags().stream().mapToInt(CtfForwardFlagDefinition::order).max().orElse(0) + 1;
        try {
            config.addForward(new CtfForwardFlagDefinition(id, owner, region, ArenaPosition.from(player), order));
            dirty(context); return success(context, "Added CTF forward flag " + id);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfForwardSetBox(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException(); ArenaPosition first = CTF_POS_1.get(player.getUUID()), second = CTF_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        return ctfReplaceForward(context, old -> old.withRegion(selectedBlockBox(
                first, second, old.region().minY(), old.region().maxY())));
    }

    private static int ctfForwardSetCenter(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ArenaPosition center = ArenaPosition.from(context.getSource().getPlayerOrException());
        return ctfReplaceForward(context, old -> old.region() instanceof SquareCaptureRegion square ? old.withRegion(square.withCenter(center)) : throwIllegal("Forward flag is not square"));
    }

    private static int ctfForwardSetRadius(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return ctfReplaceForward(context, old -> old.region() instanceof SquareCaptureRegion square ? old.withRegion(square.withRadius(radius)) : throwIllegal("Forward flag is not square"));
    }

    private static int ctfForwardSetHeight(CommandContext<CommandSourceStack> context, boolean full) {
        if (!checkCtfEdit(context)) return 0;
        Integer min = full ? null : IntegerArgumentType.getInteger(context, "minY"), max = full ? null : IntegerArgumentType.getInteger(context, "maxY");
        return ctfReplaceForward(context, old -> old.withRegion(old.region().withHeight(min, max)));
    }

    private static int ctfForwardSetStand(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ArenaPosition position = ArenaPosition.from(context.getSource().getPlayerOrException());
        return ctfReplaceForward(context, old -> old.withStand(position));
    }

    private static CtfForwardFlagDefinition throwIllegal(String message) { throw new IllegalArgumentException(message); }

    private static int ctfReplaceForward(CommandContext<CommandSourceStack> context, java.util.function.Function<CtfForwardFlagDefinition, CtfForwardFlagDefinition> function) {
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
        String id = StringArgumentType.getString(context, "id");
        try { CtfForwardFlagDefinition old = config.forward(id).orElseThrow(() -> new IllegalArgumentException("Unknown forward flag: " + id));
            config.replaceForward(id, function.apply(old)); dirty(context); return success(context, "Updated forward flag " + id); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfForwardList(CommandContext<CommandSourceStack> context) {
        if (!checkCtf(context)) return 0;
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
        config.forwardFlags().forEach(flag -> send(context, flag.order() + ": " + flag.id() + " owner=" + flag.owner().id() + " " + regionText(flag.region())));
        return config.forwardFlags().size();
    }

    private static int ctfForwardStatus(CommandContext<CommandSourceStack> context) {
        if (!checkCtf(context)) return 0; String id = StringArgumentType.getString(context, "id");
        CtfForwardFlagDefinition flag = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().forward(id).orElse(null);
        if (flag == null) return failure(context, "Unknown forward flag: " + id);
        send(context, flag.id() + " owner=" + flag.owner().id() + " order=" + flag.order() + " " + regionText(flag.region())); return 1;
    }

    private static int ctfForwardRemove(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0; String id = StringArgumentType.getString(context, "id");
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
        if (!config.removeForward(id)) return failure(context, "Unknown forward flag: " + id); dirty(context); return success(context, "Removed forward flag " + id);
    }

    private static int ctfForwardClear(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0; var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag(); int count = config.forwardFlags().size();
        config.clearForward(); dirty(context); return success(context, "Cleared " + count + " forward flag(s)");
    }

    private static int ctfBuildPosition(CommandContext<CommandSourceStack> context, boolean first)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        (first ? CTF_POS_1 : CTF_POS_2).put(player.getUUID(), ArenaPosition.from(player));
        return success(context, "Set CTF build pos" + (first ? "1" : "2"));
    }

    private static int ctfBuildSetBox(CommandContext<CommandSourceStack> context, boolean fullHeight)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkMapBuildEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException(); ArenaPosition first = CTF_POS_1.get(player.getUUID()), second = CTF_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Build corners must be in the same dimension");
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap();
        Integer minY = fullHeight ? null : (int) Math.floor(Math.min(first.y(), second.y()));
        Integer maxY = fullHeight ? null : (int) Math.floor(Math.max(first.y(), second.y()));
        config.build().region(selectedBlockBox(first, second, minY, maxY));
        dirty(context);
        BoxCaptureRegion region = config.build().region();
        return success(context, (fullHeight ? "Set full-height map build box" : "Set bounded-height map build box")
                + ": X " + net.minecraft.util.Mth.floor(region.minX()) + ".." + net.minecraft.util.Mth.floor(region.maxX())
                + ", Z " + net.minecraft.util.Mth.floor(region.minZ()) + ".." + net.minecraft.util.Mth.floor(region.maxZ()));
    }

    private static int ctfBuildClear(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuildEdit(context)) return 0;
        SFGameSavedData.get(context.getSource().getServer()).activeMap().build().clearRegion(); dirty(context);
        return success(context, "Cleared map build box");
    }

    private static int ctfBuildClearAll(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuildEdit(context)) return 0;
        try {
            SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            com.sfgame.game.MapBuildSnapshotService.clear(context.getSource().getServer(),
                    data.selectedMode(), data.activeMap());
            data.activeMap().build().clearRegion();
            dirty(context);
            return success(context, "Cleared map build box and snapshot");
        } catch (Exception exception) {
            return failure(context, exception.getMessage() == null
                    ? "Could not clear map build box and snapshot" : exception.getMessage());
        }
    }

    private static int ctfBuildAllow(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkMapBuildEdit(context)) return 0;
        try {
            String selector = BlockAllowlist.normalize(ResourceOrTagArgument.getResourceOrTag(
                    context, "block", net.minecraft.core.registries.Registries.BLOCK).asPrintable());
            SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            java.util.Set<String> allowlist = new java.util.LinkedHashSet<>(MatchManager.get().rules().mapBlockAllowlist());
            if (!allowlist.add(selector)) return failure(context, "Selector is already in the allowlist: " + selector);
            MatchManager.get().ruleConfigs().setStringSet(data.selectedMode(), data.selectedMap(),
                    "mapBlockAllowlist", allowlist);
            data.activeMap().build().snapshotSaved(false);
            dirty(context);
            return success(context, "Allowed block selector " + selector);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfBuildDisallow(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkMapBuildEdit(context)) return 0;
        try {
            String selector = BlockAllowlist.normalize(ResourceOrTagArgument.getResourceOrTag(
                    context, "block", net.minecraft.core.registries.Registries.BLOCK).asPrintable());
            SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            java.util.Set<String> allowlist = new java.util.LinkedHashSet<>(MatchManager.get().rules().mapBlockAllowlist());
            if (!allowlist.remove(selector)) return failure(context, "Selector was not in the allowlist: " + selector);
            MatchManager.get().ruleConfigs().setStringSet(data.selectedMode(), data.selectedMap(),
                    "mapBlockAllowlist", allowlist);
            data.activeMap().build().snapshotSaved(false);
            dirty(context);
            return success(context, "Disallowed block selector " + selector);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfBuildAllowList(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuild(context)) return 0;
        var allowlist = MatchManager.get().rules().mapBlockAllowlist();
        allowlist.forEach(id -> send(context, id));
        return allowlist.size();
    }

    private static int ctfSnapshotSave(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuildEdit(context)) return 0;
        try { SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            int parts = com.sfgame.game.MapBuildSnapshotService.save(context.getSource().getServer(),
                    data.selectedMode(), data.activeMap(), MatchManager.get().rules().mapSnapshotMode(),
                    MatchManager.get().rules().mapBlockAllowlist()); dirty(context);
            return success(context, "Saved map snapshot in " + parts + " partition(s)");
        } catch (Exception exception) { return failure(context, exception.getMessage() == null ? "Could not save map snapshot" : exception.getMessage()); }
    }

    private static int ctfSnapshotRestore(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuildEdit(context)) return 0;
        try { SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            int parts = com.sfgame.game.MapBuildSnapshotService.restore(context.getSource().getServer(),
                    data.selectedMode(), data.activeMap(), MatchManager.get().rules().mapSnapshotMode(),
                    MatchManager.get().rules().mapBlockAllowlist());
            return success(context, "Restored map snapshot from " + parts + " partition(s)");
        } catch (Exception exception) { return failure(context, exception.getMessage() == null ? "Could not restore map snapshot" : exception.getMessage()); }
    }

    private static int ctfBuildStatus(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuild(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        BoxCaptureRegion region = data.activeMap().build().region();
        var snapshot = com.sfgame.game.MapBuildSnapshotService.status(
                context.getSource().getServer(), data.selectedMode(), data.activeMap(),
                MatchManager.get().rules().mapSnapshotMode(), MatchManager.get().rules().mapBlockAllowlist());
        send(context, "setbox=" + (region != null));
        if (region != null) {
            send(context, "setbox pos1=" + buildCornerText(region, true));
            send(context, "setbox pos2=" + buildCornerText(region, false));
        }
        send(context, "snapshot=" + snapshot.exists()
                + ", partitions=" + snapshot.partitions()
                + ", detail=" + snapshot.detail());
        return 1;
    }

    static String buildCornerText(BoxCaptureRegion region, boolean first) {
        int x = net.minecraft.util.Mth.floor(first ? region.minX() : region.maxX());
        int z = net.minecraft.util.Mth.floor(first ? region.minZ() : region.maxZ());
        Integer configuredY = first ? region.minY() : region.maxY();
        String y = configuredY == null ? "full-height" : Integer.toString(configuredY);
        return region.dimension() + " " + x + " " + y + " " + z;
    }

    private static int ctfSnapshotClear(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuildEdit(context)) return 0;
        try { SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer()); com.sfgame.game.MapBuildSnapshotService.clear(context.getSource().getServer(), data.selectedMode(), data.activeMap()); dirty(context); return success(context, "Cleared map snapshot"); }
        catch (Exception exception) { return failure(context, exception.getMessage() == null ? "Could not clear map snapshot" : exception.getMessage()); }
    }

    private static int shopList(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        var registry = MatchManager.get().shop();
        registry.items(data.selectedMode()).forEach(item ->
                send(context, item.id() + " - " + item.name() + " (" + item.price() + ")"));
        registry.errors().forEach(error -> context.getSource().sendFailure(Component.literal(error)));
        return registry.items(data.selectedMode()).size();
    }

    private static int shopBuy(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String item = StringArgumentType.getString(context, "item");
        return MatchManager.get().purchase(player, item) ? success(context, "Purchased " + item) : failure(context, "Could not purchase " + item);
    }

    private static int shopReload(CommandContext<CommandSourceStack> context) {
        List<String> errors = MatchManager.get().shop().reload();
        if (!errors.isEmpty()) {
            errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
            return 0;
        }
        return success(context, "Reloaded shops");
    }
    private static int supplyList(CommandContext<CommandSourceStack> context, TeamSide selected) {
        MatchManager manager = MatchManager.get();
        if (!supplyRunning(context)) return failure(context, "Supply commands require a running economy match");
        List<TeamSide> sides = selected == TeamSide.NONE ? TeamSide.PLAYABLE : List.of(selected);
        int count = 0;
        for (TeamSide side : sides) {
            for (var item : manager.supplies().items(side)) {
                send(context, side.id() + ": " + item.id() + " " + item.type() + " x" + item.quantity());
                count++;
            }
        }
        return count;
    }

    private static int supplyPushPreset(CommandContext<CommandSourceStack> context, int quantity) {
        TeamSide side = supplyTeam(context);
        String offerId = StringArgumentType.getString(context, "offerId");
        return side != TeamSide.NONE && MatchManager.get().pushSupplyPreset(side, offerId, quantity)
                ? success(context, "Published " + quantity + " " + offerId + " for " + side.id())
                : failure(context, "Could not publish supply preset");
    }

    private static int supplyPushItem(CommandContext<CommandSourceStack> context) {
        TeamSide side = supplyTeam(context);
        String offerId = StringArgumentType.getString(context, "offerId");
        int count = IntegerArgumentType.getInteger(context, "count");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        String item = StringArgumentType.getString(context, "item");
        return side != TeamSide.NONE && MatchManager.get().pushSupplyItem(side, offerId, count, quantity, item)
                ? success(context, "Published " + quantity + " " + offerId + " for " + side.id())
                : failure(context, "Could not publish item supply");
    }

    private static int supplyPushElite(CommandContext<CommandSourceStack> context) {
        TeamSide side = supplyTeam(context);
        String offerId = StringArgumentType.getString(context, "offerId");
        String classId = StringArgumentType.getString(context, "classId");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        return side != TeamSide.NONE && MatchManager.get().pushSupplyElite(side, offerId, classId, quantity)
                ? success(context, "Published " + quantity + " " + offerId + " for " + side.id())
                : failure(context, "Could not publish elite supply");
    }

    private static int supplyRemove(CommandContext<CommandSourceStack> context) {
        TeamSide side = supplyTeam(context);
        String offerId = StringArgumentType.getString(context, "offerId");
        return side != TeamSide.NONE && MatchManager.get().removeSupply(side, offerId)
                ? success(context, "Removed " + offerId + " for " + side.id())
                : failure(context, "Could not remove supply");
    }

    private static int supplyClear(CommandContext<CommandSourceStack> context) {
        TeamSide side = supplyTeam(context);
        int removed = side == TeamSide.NONE ? -1 : MatchManager.get().clearSupplies(side);
        return removed >= 0 ? success(context, "Cleared " + removed + " supplies for " + side.id())
                : failure(context, "Could not clear supplies");
    }

    private static TeamSide supplyTeam(CommandContext<CommandSourceStack> context) {
        return TeamSide.fromId(StringArgumentType.getString(context, "team"));
    }

    private static boolean supplyRunning(CommandContext<CommandSourceStack> context) {
        return MatchManager.get().phase() == MatchPhase.RUNNING
                && MatchManager.supportsEconomy(
                        SFGameSavedData.get(context.getSource().getServer()).selectedMode());
    }


    private static boolean checkCtf(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        return GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode()) && data.activeMap() != null;
    }
    private static boolean checkCtfEdit(CommandContext<CommandSourceStack> context) {
        if (!checkCtf(context)) { failure(context, "Select ctf mode first"); return false; }
        if (!MatchManager.get().canChangeArena()) { failure(context, "Cannot edit CTF map during a match"); return false; }
        return true;
    }
    private static boolean checkMapBuild(CommandContext<CommandSourceStack> context) {
        if (SFGameSavedData.get(context.getSource().getServer()).activeMap() == null) {
            failure(context, "Select a map first");
            return false;
        }
        return true;
    }
    private static boolean checkMapBuildEdit(CommandContext<CommandSourceStack> context) {
        if (!checkMapBuild(context)) return false;
        if (!MatchManager.get().canChangeArena()) {
            failure(context, "Cannot edit the map build configuration during a match");
            return false;
        }
        return true;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sectorCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sector");
        root.requires(source -> source.hasPermission(2));
        root.then(Commands.literal("add").then(Commands.argument("sector", StringArgumentType.word()).executes(SFGameCommands::sectorAdd)));
        root.then(Commands.literal("set").then(Commands.literal("order").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("order", IntegerArgumentType.integer(1, 16)).executes(SFGameCommands::sectorSetOrder)))));
        root.then(Commands.literal("list").executes(SFGameCommands::sectorList));
        root.then(Commands.literal("status").then(Commands.argument("sector", StringArgumentType.word()).executes(SFGameCommands::sectorStatus)));
        root.then(Commands.literal("remove").then(Commands.argument("sector", StringArgumentType.word()).executes(SFGameCommands::sectorRemove)));
        root.then(Commands.literal("clear").executes(SFGameCommands::sectorClear));

        LiteralArgumentBuilder<CommandSourceStack> point = Commands.literal("point");
        LiteralArgumentBuilder<CommandSourceStack> pointAdd = Commands.literal("add");
        pointAdd.then(Commands.literal("box").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word()).executes(SFGameCommands::sectorPointAddBox))));
        pointAdd.then(Commands.literal("square").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(SFGameCommands::sectorPointAddSquare)))));
        point.then(pointAdd);
        LiteralArgumentBuilder<CommandSourceStack> pointSet = Commands.literal("set");
        pointSet.then(Commands.literal("box").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word()).executes(SFGameCommands::sectorPointSetBox))));
        pointSet.then(Commands.literal("radius").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256)).executes(SFGameCommands::sectorPointSetRadius)))));
        pointSet.then(Commands.literal("height").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word())
                        .then(Commands.literal("full").executes(context -> sectorPointSetHeight(context, true)))
                        .then(Commands.argument("minY", IntegerArgumentType.integer(-2048, 2048))
                                .then(Commands.argument("maxY", IntegerArgumentType.integer(-2048, 2048))
                                        .executes(context -> sectorPointSetHeight(context, false)))))));
        pointSet.then(Commands.literal("respawn").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word())
                        .then(Commands.literal("inside").executes(context -> sectorPointSetRespawn(context, false)))
                        .then(Commands.literal("nearby").executes(context -> sectorPointSetRespawn(context, true))))));
        point.then(pointSet);
        point.then(Commands.literal("remove").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.argument("point", StringArgumentType.word()).executes(SFGameCommands::sectorPointRemove))));
        root.then(point);

        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
        spawn.then(Commands.literal("add").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.literal("attacker").executes(context -> sectorSpawnAdd(context, true)))
                .then(Commands.literal("defender").executes(context -> sectorSpawnAdd(context, false)))));
        spawn.then(Commands.literal("list").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.literal("attacker").executes(context -> sectorSpawnList(context, true)))
                .then(Commands.literal("defender").executes(context -> sectorSpawnList(context, false)))));
        spawn.then(Commands.literal("remove").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.literal("attacker").then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(context -> sectorSpawnRemove(context, true))))
                .then(Commands.literal("defender").then(Commands.argument("index", IntegerArgumentType.integer(1))
                        .executes(context -> sectorSpawnRemove(context, false))))));
        spawn.then(Commands.literal("clear").then(Commands.argument("sector", StringArgumentType.word())
                .then(Commands.literal("attacker").executes(context -> sectorSpawnClear(context, true)))
                .then(Commands.literal("defender").executes(context -> sectorSpawnClear(context, false)))));
        root.then(spawn);
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> captainCommands() {
        return Commands.literal("captain")
                .then(Commands.literal("vote")
                        .then(Commands.literal("abstain").executes(context -> captainVote(context, true)))
                        .then(Commands.argument("candidate", EntityArgument.player()).executes(context -> captainVote(context, false))))
                .then(Commands.literal("status").executes(SFGameCommands::captainStatus))
                .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("side", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player()).executes(SFGameCommands::captainSet))))
                .then(Commands.literal("reelect").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("side", StringArgumentType.word()).executes(SFGameCommands::captainReelect)));
    }

    private static int menu(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SFGameNetwork.openMenu(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MatchManager.get().leave(player);
        return success(context, "Left the SFGame match");
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MatchManager manager = MatchManager.get();
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, "Phase=" + manager.phase() + ", scores=" + TeamSide.PLAYABLE.stream()
                .map(side -> side.id() + ":" + manager.score(side)).collect(java.util.stream.Collectors.joining(","))
                + ", mode=" + data.selectedMode() + ", map=" + data.selectedMap()
                + ", devMode=" + data.devMode()
                + ", arenaConfigured=" + data.isArenaConfigured());
        List<String> errors = manager.validateStart();
        errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
        return errors.isEmpty() ? 1 : 0;
    }

    private static int scoreStatus(CommandContext<CommandSourceStack> context) {
        MatchManager manager = MatchManager.get();
        if (manager.phase() != MatchPhase.RUNNING) {
            return failure(context, "Live score editing is only available while a match is running");
        }
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!supportsTeamScores(data.selectedMode())) {
            BreakthroughRuntime runtime = manager.breakthrough();
            int sectors = data.activeMap() == null ? 0 : runtime.sectorCount(data.activeMap());
            send(context, breakthroughScoreStatus(manager.remainingSeconds(), runtime.attacker(), runtime.defender(),
                    runtime.tickets(), runtime.leg(), runtime.sectorNumber(), sectors));
        } else {
            String scores = data.enabledTeams().stream()
                    .map(side -> side.id() + "=" + manager.score(side))
                    .collect(java.util.stream.Collectors.joining(", "));
            send(context, "Remaining time=" + manager.remainingSeconds() + "s, scores: " + scores);
        }
        return 1;
    }

    private static int scoreTime(CommandContext<CommandSourceStack> context) {
        MatchManager manager = MatchManager.get();
        if (manager.phase() != MatchPhase.RUNNING) {
            return failure(context, "Live score editing is only available while a match is running");
        }
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        return manager.setRemainingSeconds(seconds)
                ? success(context, "Remaining match time set to " + seconds + " seconds")
                : failure(context, "Could not update the remaining match time");
    }

    private static int scoreCurrency(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MatchManager manager = MatchManager.get();
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (manager.phase() != MatchPhase.RUNNING || !MatchManager.supportsEconomy(data.selectedMode())) {
            return failure(context, "Currency editing is only available during a running economy match");
        }
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int value = IntegerArgumentType.getInteger(context, "value");
        return manager.setCurrency(player, value)
                ? success(context, player.getGameProfile().getName() + " currency set to " + value)
                : failure(context, "Could not update currency");
    }

    private static int scoreTickets(CommandContext<CommandSourceStack> context) {
        if (!isRunningBreakthrough(context)) {
            return failure(context, "This score field is only available during a running breakthrough match");
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        return MatchManager.get().setBreakthroughTickets(value)
                ? success(context, "Breakthrough attacker tickets set to " + value)
                : failure(context, "Could not update breakthrough tickets");
    }


    private static int scoreLeg(CommandContext<CommandSourceStack> context) {
        if (!isRunningBreakthrough(context)) {
            return failure(context, "This score field is only available during a running breakthrough match");
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        return MatchManager.get().setBreakthroughLeg(value)
                ? success(context, "Breakthrough switched to live leg " + value
                        + "; sector, time and tickets were reset")
                : failure(context, "Leg must be between 1 and " + MatchManager.MAX_LIVE_LEG);
    }

    private static int scoreSector(CommandContext<CommandSourceStack> context) {
        if (!isRunningBreakthrough(context)) {
            return failure(context, "This score field is only available during a running breakthrough match");
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        return MatchManager.get().setBreakthroughSector(value)
                ? success(context, "Breakthrough switched to sector " + value
                        + "; sector time, points and tickets were reset")
                : failure(context, "Sector must exist in the active breakthrough map");
    }

    private static boolean isRunningBreakthrough(CommandContext<CommandSourceStack> context) {
        return MatchManager.get().phase() == MatchPhase.RUNNING
                && GameModeRegistry.BREAKTHROUGH.equals(
                SFGameSavedData.get(context.getSource().getServer()).selectedMode());
    }

    private static int scoreTeam(CommandContext<CommandSourceStack> context, TeamSide side) {
        MatchManager manager = MatchManager.get();
        if (manager.phase() != MatchPhase.RUNNING) {
            return failure(context, "Live score editing is only available while a match is running");
        }
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!supportsTeamScores(data.selectedMode())) {
            return failure(context, "Breakthrough uses attacker tickets instead of team scores");
        }
        if (!data.enabledTeams().contains(side)) {
            return failure(context, side.id() + " is not enabled for the active map");
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        return manager.setTeamScore(side, value)
                ? success(context, side.id() + " score set to " + value)
                : failure(context, "Could not update " + side.id() + " score");
    }

    static boolean supportsTeamScores(String modeId) {
        return !GameModeRegistry.BREAKTHROUGH.equals(modeId);
    }

    private static boolean scoreFieldVisible(CommandSourceStack source, String field) {
        return source == null || scoreFieldVisible(
                SFGameSavedData.get(source.getServer()).selectedMode(), field);
    }

    static boolean scoreFieldVisible(String modeId, String field) {
        return switch (field) {
            case "tickets", "leg", "sector" -> GameModeRegistry.BREAKTHROUGH.equals(modeId);
            case "currency" -> MatchManager.supportsEconomy(modeId);
            case "red", "blue", "yellow", "green" -> supportsTeamScores(modeId);
            default -> true;
        };
    }

    static String breakthroughScoreStatus(int remainingSeconds, TeamSide attacker, TeamSide defender,
                                          int tickets, int leg, int sector, int sectors) {
        return "Remaining time=" + remainingSeconds + "s, attacker=" + attacker.id()
                + ", defender=" + defender.id() + ", tickets=" + tickets
                + ", leg=" + leg + ", sector=" + sector + "/" + sectors;
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        List<String> errors = MatchManager.get().validateStart();
        if (!errors.isEmpty()) {
            errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
            return 0;
        }
        return MatchManager.get().start() ? success(context, "Match countdown started") : failure(context, "Match cannot start in the current phase");
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        MatchManager.get().stop(false, Component.literal("Match stopped by an administrator"));
        return success(context, "Match stopped");
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        MatchManager.get().stop(false, Component.literal("SFGame runtime reset"));
        return success(context, "Runtime state reset; arena coordinates were kept");
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        MatchManager manager = MatchManager.get();
        if (!manager.canChangeArena()) {
            return failure(context, "Map and supply files can only be reloaded in the lobby");
        }
        List<String> mapErrors = manager.reloadMapConfigurations();
        int classResult = classReload(context);
        List<String> ruleErrors = manager.reloadRuleConfigurations();
        List<String> shopErrors = manager.shop().reload();
        mapErrors.forEach(error -> context.getSource().sendFailure(Component.literal("Maps: " + error)));
        ruleErrors.forEach(error -> context.getSource().sendFailure(Component.literal("Rules: " + error)));
        shopErrors.forEach(error -> context.getSource().sendFailure(Component.literal("Shop: " + error)));
        manager.refreshCommandTree();
        return mapErrors.isEmpty() && classResult > 0 && ruleErrors.isEmpty() && shopErrors.isEmpty() ? 1 : 0;
    }

    private static int toggleDev(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        boolean enabled = !data.devMode();
        data.devMode(enabled);
        MatchManager.get().arenaSelectionChanged();
        return success(context, enabled
                ? "Global dev mode enabled; solo matches may now start"
                : "Global dev mode disabled; normal team player requirements restored");
    }

    private static int joinNow(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        return MatchManager.get().joinNow(player) ? success(context, player.getGameProfile().getName() + " may join now")
                : failure(context, "Could not immediately join that player");
    }

    private static int setPosition(CommandContext<CommandSourceStack> context, String type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit map positions during a match");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        ArenaPosition position = ArenaPosition.from(context.getSource().getPlayerOrException());
        switch (type) {
            case "lobby" -> data.lobby(position);
            case "red" -> data.addSpawn(TeamSide.RED, position);
            case "blue" -> data.addSpawn(TeamSide.BLUE, position);
            case "yellow" -> data.addSpawn(TeamSide.YELLOW, position);
            case "green" -> data.addSpawn(TeamSide.GREEN, position);
        }
        MatchManager.get().arenaSelectionChanged();
        String detail = switch (type) {
            case "red" -> "Added red spawn #" + data.spawns(TeamSide.RED).size();
            case "blue" -> "Added blue spawn #" + data.spawns(TeamSide.BLUE).size();
            case "yellow" -> "Added yellow spawn #" + data.spawns(TeamSide.YELLOW).size();
            case "green" -> "Added green spawn #" + data.spawns(TeamSide.GREEN).size();
            default -> "Set lobby";
        };
        return success(context, detail + " for " + data.selectedMode() + "/" + data.selectedMap());
    }

    private static int setDefaultLobby(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit the default lobby during a match");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        ArenaPosition position = ArenaPosition.from(context.getSource().getPlayerOrException());
        data.defaultLobby(position);
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Set default lobby to " + positionText(position));
    }

    private static int clearLobby(CommandContext<CommandSourceStack> context) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit the lobby during a match");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.clearLobby();
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Cleared map lobby; using default lobby at " + positionText(data.defaultLobby()));
    }

    private static int spawnList(CommandContext<CommandSourceStack> context, TeamSide side) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        List<ArenaPosition> positions = data.spawns(side);
        send(context, side + " spawns for " + data.selectedMode() + "/" + data.selectedMap() + ": " + positions.size());
        for (int i = 0; i < positions.size(); i++) send(context, (i + 1) + ": " + positionText(positions.get(i)));
        return positions.size();
    }

    private static int spawnRemove(CommandContext<CommandSourceStack> context, TeamSide side) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit spawns during a match");
        int displayIndex = IntegerArgumentType.getInteger(context, "index");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        boolean removed = data.removeSpawn(side, displayIndex - 1);
        if (!removed) return failure(context, "Spawn index does not exist: " + displayIndex);
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Removed " + side + " spawn #" + displayIndex);
    }

    private static int spawnClear(CommandContext<CommandSourceStack> context, TeamSide side) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit spawns during a match");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int removed = data.spawns(side).size();
        data.clearSpawns(side);
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Cleared " + removed + " " + side + " spawn(s)");
    }

    private static String positionText(ArenaPosition position) {
        return position.dimension() + " " + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", position.x(), position.y(), position.z());
    }

    private static int pointPosition(CommandContext<CommandSourceStack> context, boolean first)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!GameModeRegistry.DOMINATION.equals(data.selectedMode()) && !GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())) {
            return failure(context, "Select domination or breakthrough mode before setting capture positions");
        }
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit capture points during a match");
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition position = ArenaPosition.from(player);
        (first ? POINT_POS_1 : POINT_POS_2).put(player.getUUID(), position);
        return success(context, "Set capture point pos" + (first ? "1" : "2") + " to " + positionText(position));
    }

    private static int universalPosition(CommandContext<CommandSourceStack> context, boolean first)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        String mode = data.selectedMode();
        if (data.activeMap() == null) return failure(context, "Select a map first");
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit regions during a match");
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition position = targetedBlockPosition(player);
        if (position == null) return failure(context, "No block is targeted within 128 blocks");
        (first ? CTF_POS_1 : CTF_POS_2).put(player.getUUID(), position);
        if (GameModeRegistry.DOMINATION.equals(mode) || GameModeRegistry.BREAKTHROUGH.equals(mode)) {
            (first ? POINT_POS_1 : POINT_POS_2).put(player.getUUID(), position);
        }
        return success(context, "Set " + mode + " pos" + (first ? "1" : "2") + " to " + positionText(position));
    }

    private static ArenaPosition targetedBlockPosition(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(128.0D));
        BlockHitResult hit = player.level().clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos block = hit.getBlockPos();
        return new ArenaPosition(player.level().dimension().location().toString(),
                block.getX(), block.getY(), block.getZ(), 0.0F, 0.0F);
    }

    /** Creates a region that includes the complete volume of both selected blocks. */
    static BoxCaptureRegion selectedBlockBox(ArenaPosition first, ArenaPosition second,
                                             Integer minY, Integer maxY) {
        if (!first.dimension().equals(second.dimension())) {
            throw new IllegalArgumentException("Corners must be in the same dimension");
        }
        int firstX = net.minecraft.util.Mth.floor(first.x());
        int secondX = net.minecraft.util.Mth.floor(second.x());
        int firstZ = net.minecraft.util.Mth.floor(first.z());
        int secondZ = net.minecraft.util.Mth.floor(second.z());
        int minBlockX = Math.min(firstX, secondX);
        int maxBlockX = Math.max(firstX, secondX);
        int minBlockZ = Math.min(firstZ, secondZ);
        int maxBlockZ = Math.max(firstZ, secondZ);
        return new BoxCaptureRegion(first.dimension(), minBlockX, Math.nextDown(maxBlockX + 1.0D),
                minBlockZ, Math.nextDown(maxBlockZ + 1.0D), minY, maxY);
    }

    private static int pointAddBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        try {
            addPoint(context, selectedBlockBox(first, second, null, null));
            return 1;
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int pointAddSquare(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        int radius = IntegerArgumentType.getInteger(context, "radius");
        try {
            addPoint(context, SquareCaptureRegion.centeredAt(ArenaPosition.from(context.getSource().getPlayerOrException()), radius));
            return 1;
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static void addPoint(CommandContext<CommandSourceStack> context, CaptureRegion region) {
        String id = StringArgumentType.getString(context, "point");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int order = data.activeMap().domination().points().stream().mapToInt(CapturePointDefinition::order).max().orElse(0) + 1;
        data.activeMap().domination().add(new CapturePointDefinition(id, region, order));
        dirty(context); MatchManager.get().arenaSelectionChanged();
        context.getSource().sendSuccess(() -> Component.literal("Added capture point " + id).withStyle(ChatFormatting.GREEN), true);
    }

    private static int pointSetBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        return replacePointRegion(context, existing -> selectedBlockBox(
                first, second, existing.region().minY(), existing.region().maxY()));
    }

    private static int pointSetCenter(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        ArenaPosition center = ArenaPosition.from(context.getSource().getPlayerOrException());
        return replacePointRegion(context, existing -> {
            if (!(existing.region() instanceof SquareCaptureRegion square)) throw new IllegalArgumentException("Point is not square");
            return square.withCenter(center);
        });
    }

    private static int pointSetRadius(CommandContext<CommandSourceStack> context) {
        if (!checkPointEdit(context)) return 0;
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return replacePointRegion(context, existing -> {
            if (!(existing.region() instanceof SquareCaptureRegion square)) throw new IllegalArgumentException("Point is not square");
            return square.withRadius(radius);
        });
    }

    private static int pointSetHeight(CommandContext<CommandSourceStack> context, boolean full) {
        if (!checkPointEdit(context)) return 0;
        Integer minY = full ? null : IntegerArgumentType.getInteger(context, "minY");
        Integer maxY = full ? null : IntegerArgumentType.getInteger(context, "maxY");
        return replacePointRegion(context, existing -> existing.region().withHeight(minY, maxY));
    }

    private static int pointSetOrder(CommandContext<CommandSourceStack> context) {
        if (!checkPointEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        String id = StringArgumentType.getString(context, "point");
        int order = IntegerArgumentType.getInteger(context, "order");
        try {
            CapturePointDefinition point = data.activeMap().domination().point(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown capture point: " + id));
            data.activeMap().domination().replace(id, point.withOrder(order)); dirty(context);
            MatchManager.get().arenaSelectionChanged();
            return success(context, "Set " + id + " order to " + order);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int pointList(CommandContext<CommandSourceStack> context) {
        if (!checkDomination(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, "Strategy=" + MatchManager.get().rules().dominationStrategy().name().toLowerCase()
                + ", points=" + data.activeMap().domination().points().size());
        data.activeMap().domination().points().forEach(point -> send(context,
                point.order() + ": " + point.id() + " " + regionText(point.region())));
        return data.activeMap().domination().points().size();
    }

    private static int pointStatus(CommandContext<CommandSourceStack> context) {
        if (!checkDomination(context)) return 0;
        String id = StringArgumentType.getString(context, "point");
        CapturePointDefinition point = SFGameSavedData.get(context.getSource().getServer()).activeMap().domination().point(id).orElse(null);
        if (point == null) return failure(context, "Unknown capture point: " + id);
        send(context, point.id() + " order=" + point.order() + " " + regionText(point.region()));
        return 1;
    }

    private static int pointRemove(CommandContext<CommandSourceStack> context) {
        if (!checkPointEdit(context)) return 0;
        String id = StringArgumentType.getString(context, "point");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.activeMap().domination().remove(id)) return failure(context, "Unknown capture point: " + id);
        dirty(context); MatchManager.get().arenaSelectionChanged();
        return success(context, "Removed capture point " + id);
    }

    private static int pointClear(CommandContext<CommandSourceStack> context) {
        if (!checkPointEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int count = data.activeMap().domination().points().size();
        data.activeMap().domination().clear(); dirty(context); MatchManager.get().arenaSelectionChanged();
        return success(context, "Cleared " + count + " capture point(s)");
    }

    private static int replacePointRegion(CommandContext<CommandSourceStack> context,
                                          java.util.function.Function<CapturePointDefinition, CaptureRegion> replacement) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        String id = StringArgumentType.getString(context, "point");
        try {
            CapturePointDefinition point = data.activeMap().domination().point(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown capture point: " + id));
            data.activeMap().domination().replace(id, point.withRegion(replacement.apply(point)));
            dirty(context); MatchManager.get().arenaSelectionChanged();
            return success(context, "Updated capture point " + id);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static boolean checkDomination(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!GameModeRegistry.DOMINATION.equals(data.selectedMode())) {
            failure(context, "Select domination mode before editing capture points"); return false;
        }
        if (data.activeMap() == null) { failure(context, "No active map"); return false; }
        return true;
    }

    private static boolean checkPointEdit(CommandContext<CommandSourceStack> context) {
        if (!checkDomination(context)) return false;
        if (!MatchManager.get().canChangeArena()) {
            failure(context, "Cannot edit capture points during a match"); return false;
        }
        return true;
    }

    private static String regionText(CaptureRegion region) {
        String height = region.minY() == null ? "all heights" : "Y=" + region.minY() + ".." + region.maxY();
        if (region instanceof BoxCaptureRegion box) {
            return "box " + box.dimension() + " X=" + box.minX() + ".." + box.maxX()
                    + " Z=" + box.minZ() + ".." + box.maxZ() + " " + height;
        }
        SquareCaptureRegion square = (SquareCaptureRegion) region;
        return "square " + square.dimension() + " center=" + square.centerX() + "," + square.centerZ()
                + " radius=" + square.radius() + " " + height;
    }

    private static int breakthroughStatus(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().breakthrough();
        MatchRules rules = MatchManager.get().rules();
        send(context, "variant=" + rules.breakthroughVariant().name().toLowerCase() + ", legs=" + rules.breakthroughLegs()
                + ", attacker=" + rules.breakthroughAttacker().id() + ", defender=" + rules.breakthroughDefender().id()
                + ", sectors=" + config.sectors().size() + ", vehicles=" + config.vehicles().size());
        return 1;
    }

    private static int breakthroughVehicleAdd(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "id");
        String entity = ResourceLocationArgument.getId(context, "entity").toString();
        BreakthroughVehicleDefinition.Role role = BreakthroughVehicleDefinition.Role.fromId(
                StringArgumentType.getString(context, "role"));
        if (role == null) return failure(context, "Vehicle role must be attacker or defender");
        int respawnSeconds = IntegerArgumentType.getInteger(context, "respawnSeconds");
        try {
            SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            data.activeMap().breakthrough().addVehicle(new BreakthroughVehicleDefinition(id, entity, role,
                    ArenaPosition.from(player), respawnSeconds));
            dirty(context);
            return success(context, "Added breakthrough vehicle " + id + " (" + entity + ", " + role.id()
                    + ", respawn " + respawnSeconds + "s)");
        } catch (IllegalArgumentException exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int breakthroughVehicleSetPosition(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        vehicle.spawn(ArenaPosition.from(context.getSource().getPlayerOrException()));
        dirty(context);
        return success(context, "Updated vehicle spawn position for " + vehicle.id());
    }

    private static int breakthroughVehicleSetEntity(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        try {
            vehicle.entityId(ResourceLocationArgument.getId(context, "entity").toString());
            dirty(context);
            return success(context, "Updated vehicle entity for " + vehicle.id());
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int breakthroughVehicleSetRole(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        BreakthroughVehicleDefinition.Role role = BreakthroughVehicleDefinition.Role.fromId(
                StringArgumentType.getString(context, "role"));
        if (role == null) return failure(context, "Vehicle role must be attacker or defender");
        vehicle.role(role); dirty(context);
        return success(context, "Updated vehicle role for " + vehicle.id() + " to " + role.id());
    }

    private static int breakthroughVehicleSetInterval(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        vehicle.respawnSeconds(IntegerArgumentType.getInteger(context, "respawnSeconds"));
        dirty(context);
        return success(context, "Updated vehicle respawn interval for " + vehicle.id());
    }

    private static int breakthroughVehicleSetOffset(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        vehicle.spawnYOffset(DoubleArgumentType.getDouble(context, "yOffset"));
        dirty(context);
        return success(context, "Updated vehicle spawn Y offset for " + vehicle.id() + " to " + vehicle.spawnYOffset());
    }

    private static int breakthroughVehicleSetEnergy(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        vehicle.energyPercent(IntegerArgumentType.getInteger(context, "percent"));
        dirty(context);
        return success(context, "Updated vehicle spawn energy for " + vehicle.id() + " to "
                + vehicle.energyPercent() + "%");
    }

    private static int breakthroughVehicleReplaceAmmo(CommandContext<CommandSourceStack> context) {
        BreakthroughVehicleDefinition vehicle = checkedVehicleForAmmo(context);
        if (vehicle == null) return 0;
        String item = ResourceLocationArgument.getId(context, "item").toString();
        if (!BuiltInRegistries.ITEM.containsKey(ResourceLocationArgument.getId(context, "item"))) {
            return failure(context, "Unavailable ammo item: " + item);
        }
        vehicle.clearAmmo();
        vehicle.setAmmo(item, IntegerArgumentType.getInteger(context, "count"));
        dirty(context);
        return success(context, "Replaced vehicle ammo for " + vehicle.id() + " with " + vehicle.ammo());
    }

    private static int breakthroughVehicleAddAmmo(CommandContext<CommandSourceStack> context) {
        BreakthroughVehicleDefinition vehicle = checkedVehicleForAmmo(context);
        if (vehicle == null) return 0;
        var itemId = ResourceLocationArgument.getId(context, "item");
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) return failure(context, "Unavailable ammo item: " + itemId);
        try {
            vehicle.setAmmo(itemId.toString(), IntegerArgumentType.getInteger(context, "count"));
            dirty(context);
            return success(context, "Updated vehicle ammo for " + vehicle.id() + ": " + vehicle.ammo());
        } catch (IllegalArgumentException exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int breakthroughVehicleRemoveAmmo(CommandContext<CommandSourceStack> context) {
        BreakthroughVehicleDefinition vehicle = checkedVehicleForAmmo(context);
        if (vehicle == null) return 0;
        String item = ResourceLocationArgument.getId(context, "item").toString();
        if (!vehicle.removeAmmo(item)) return failure(context, "Vehicle ammo entry not found: " + item);
        dirty(context);
        return success(context, "Removed " + item + " from vehicle " + vehicle.id());
    }

    private static int breakthroughVehicleClearAmmo(CommandContext<CommandSourceStack> context) {
        BreakthroughVehicleDefinition vehicle = checkedVehicleForAmmo(context);
        if (vehicle == null) return 0;
        vehicle.clearAmmo();
        dirty(context);
        return success(context, "Disabled spawn ammo for vehicle " + vehicle.id());
    }

    private static int breakthroughVehicleListAmmo(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        send(context, "Vehicle " + vehicle.id() + " spawn ammo: " + vehicle.ammo());
        return vehicle.ammo().size();
    }

    private static BreakthroughVehicleDefinition checkedVehicleForAmmo(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return null;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        return vehicle;
    }

    private static int breakthroughVehicleList(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        List<BreakthroughVehicleDefinition> vehicles = SFGameSavedData.get(context.getSource().getServer())
                .activeMap().breakthrough().vehicles();
        vehicles.forEach(vehicle -> send(context, vehicle.toString() + " @ " + positionText(vehicle.spawn())));
        return vehicles.size();
    }

    private static int breakthroughVehicleStatus(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        BreakthroughVehicleDefinition vehicle = breakthroughVehicle(context);
        if (vehicle == null) return failure(context, "Unknown vehicle: " + StringArgumentType.getString(context, "id"));
        send(context, vehicle.toString() + " @ " + positionText(vehicle.spawn()));
        return 1;
    }

    private static int breakthroughVehicleRemove(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        String id = StringArgumentType.getString(context, "id");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.activeMap().breakthrough().removeVehicle(id)) return failure(context, "Unknown vehicle: " + id);
        dirty(context);
        return success(context, "Removed breakthrough vehicle " + id);
    }

    private static int breakthroughVehicleClear(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int count = data.activeMap().breakthrough().vehicles().size();
        data.activeMap().breakthrough().clearVehicles(); dirty(context);
        return success(context, "Cleared " + count + " breakthrough vehicle(s)");
    }

    private static BreakthroughVehicleDefinition breakthroughVehicle(CommandContext<CommandSourceStack> context) {
        return SFGameSavedData.get(context.getSource().getServer()).activeMap().breakthrough()
                .vehicle(StringArgumentType.getString(context, "id")).orElse(null);
    }

    private static int sectorAdd(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        String id = StringArgumentType.getString(context, "sector");
        int order = data.activeMap().breakthrough().sectors().stream().mapToInt(BreakthroughSectorDefinition::order).max().orElse(0) + 1;
        try {
            data.activeMap().breakthrough().addSector(new BreakthroughSectorDefinition(id, order)); dirty(context);
            MatchManager.get().arenaSelectionChanged(); return success(context, "Added sector " + id);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int sectorSetOrder(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        String id = StringArgumentType.getString(context, "sector");
        int order = IntegerArgumentType.getInteger(context, "order");
        BreakthroughSectorDefinition sector = sector(context, id);
        if (sector == null) return failure(context, "Unknown sector: " + id);
        sector.order(order); dirty(context); return success(context, "Set sector " + id + " order to " + order);
    }

    private static int sectorList(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        var sectors = SFGameSavedData.get(context.getSource().getServer()).activeMap().breakthrough().sectors();
        sectors.forEach(sector -> send(context, sector.order() + ": " + sector.id() + " points=" + sector.points().size()
                + " attackerSpawns=" + sector.spawns(true).size() + " defenderSpawns=" + sector.spawns(false).size()));
        return sectors.size();
    }

    private static int sectorStatus(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        String id = StringArgumentType.getString(context, "sector");
        BreakthroughSectorDefinition sector = sector(context, id);
        if (sector == null) return failure(context, "Unknown sector: " + id);
        send(context, sector.id() + " order=" + sector.order() + " attackerSpawns=" + sector.spawns(true).size()
                + " defenderSpawns=" + sector.spawns(false).size());
        sector.points().forEach(point -> send(context, "  " + displayPoint(point)));
        return 1;
    }

    private static int sectorRemove(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        String id = StringArgumentType.getString(context, "sector");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.activeMap().breakthrough().removeSector(id)) return failure(context, "Unknown sector: " + id);
        dirty(context); return success(context, "Removed sector " + id);
    }

    private static int sectorClear(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int count = data.activeMap().breakthrough().sectors().size(); data.activeMap().breakthrough().clear();
        dirty(context); return success(context, "Cleared " + count + " sector(s)");
    }

    private static int sectorPointAddBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        CaptureRegion region = selectedBlockBox(first, second, null, null);
        return sectorPointAdd(context, region);
    }

    private static int sectorPointAddSquare(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        return sectorPointAdd(context, SquareCaptureRegion.centeredAt(ArenaPosition.from(context.getSource().getPlayerOrException()),
                IntegerArgumentType.getInteger(context, "radius")));
    }

    private static int sectorPointAdd(CommandContext<CommandSourceStack> context, CaptureRegion region) {
        String sectorId = StringArgumentType.getString(context, "sector");
        String pointId = StringArgumentType.getString(context, "point");
        BreakthroughSectorDefinition sector = sector(context, sectorId);
        if (sector == null) return failure(context, "Unknown sector: " + sectorId);
        int order = sector.points().stream().mapToInt(CapturePointDefinition::order).max().orElse(0) + 1;
        try { sector.addPoint(new CapturePointDefinition(pointId, region, order)); dirty(context); return success(context, "Added point " + pointId + " to sector " + sectorId); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int sectorPointSetBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        return replaceSectorPoint(context, existing -> selectedBlockBox(
                first, second, existing.region().minY(), existing.region().maxY()));
    }

    private static int sectorPointSetRadius(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return replaceSectorPoint(context, existing -> {
            if (!(existing.region() instanceof SquareCaptureRegion square)) throw new IllegalArgumentException("Point is not square");
            return square.withRadius(radius);
        });
    }

    private static int sectorPointSetHeight(CommandContext<CommandSourceStack> context, boolean full) {
        if (!checkBreakthroughEdit(context)) return 0;
        Integer minY = full ? null : IntegerArgumentType.getInteger(context, "minY");
        Integer maxY = full ? null : IntegerArgumentType.getInteger(context, "maxY");
        return replaceSectorPoint(context, existing -> existing.region().withHeight(minY, maxY));
    }

    private static int sectorPointSetRespawn(CommandContext<CommandSourceStack> context, boolean nearby)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        String sectorId = StringArgumentType.getString(context, "sector");
        String pointId = StringArgumentType.getString(context, "point");
        BreakthroughSectorDefinition sector = sector(context, sectorId);
        if (sector == null) return failure(context, "Unknown sector: " + sectorId);
        try {
            CapturePointDefinition point = sector.point(pointId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown point: " + pointId));
            ArenaPosition position = ArenaPosition.from(context.getSource().getPlayerOrException());
            if (!point.region().dimension().equals(position.dimension())) {
                return failure(context, "Point respawn position must be in " + point.region().dimension());
            }
            if (!nearby && !point.region().contains(position)) {
                return failure(context, "Inside respawn position must be inside point " + pointId);
            }
            sector.replacePoint(pointId, nearby ? point.withNearbyRespawnPosition(position) : point.withRespawnPosition(position));
            dirty(context);
            return success(context, "Set " + (nearby ? "nearby contested" : "inside")
                    + " respawn position for point " + pointId + " in sector " + sectorId);
        } catch (IllegalArgumentException exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int replaceSectorPoint(CommandContext<CommandSourceStack> context,
                                          java.util.function.Function<CapturePointDefinition, CaptureRegion> replacement) {
        String sectorId = StringArgumentType.getString(context, "sector"), pointId = StringArgumentType.getString(context, "point");
        BreakthroughSectorDefinition sector = sector(context, sectorId);
        if (sector == null) return failure(context, "Unknown sector: " + sectorId);
        try {
            CapturePointDefinition point = sector.point(pointId).orElseThrow(() -> new IllegalArgumentException("Unknown point: " + pointId));
            sector.replacePoint(pointId, point.withRegion(replacement.apply(point))); dirty(context);
            return success(context, "Updated point " + pointId + " in sector " + sectorId);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int sectorPointRemove(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        String sectorId = StringArgumentType.getString(context, "sector"), pointId = StringArgumentType.getString(context, "point");
        BreakthroughSectorDefinition sector = sector(context, sectorId);
        if (sector == null || !sector.removePoint(pointId)) return failure(context, "Unknown sector or point");
        dirty(context); return success(context, "Removed point " + pointId + " from sector " + sectorId);
    }

    private static int sectorSpawnAdd(CommandContext<CommandSourceStack> context, boolean attacker)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkBreakthroughEdit(context)) return 0;
        String sectorId = StringArgumentType.getString(context, "sector");
        BreakthroughSectorDefinition sector = sector(context, sectorId);
        if (sector == null) return failure(context, "Unknown sector: " + sectorId);
        sector.addSpawn(attacker, ArenaPosition.from(context.getSource().getPlayerOrException())); dirty(context);
        return success(context, "Added " + (attacker ? "attacker" : "defender") + " spawn to " + sectorId);
    }

    private static int sectorSpawnList(CommandContext<CommandSourceStack> context, boolean attacker) {
        if (!checkBreakthrough(context)) return 0;
        BreakthroughSectorDefinition sector = sector(context, StringArgumentType.getString(context, "sector"));
        if (sector == null) return failure(context, "Unknown sector");
        List<ArenaPosition> spawns = sector.spawns(attacker);
        for (int i = 0; i < spawns.size(); i++) send(context, (i + 1) + ": " + positionText(spawns.get(i)));
        return spawns.size();
    }

    private static int sectorSpawnRemove(CommandContext<CommandSourceStack> context, boolean attacker) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughSectorDefinition sector = sector(context, StringArgumentType.getString(context, "sector"));
        if (sector == null || !sector.removeSpawn(attacker, IntegerArgumentType.getInteger(context, "index") - 1)) return failure(context, "Unknown sector or spawn index");
        dirty(context); return success(context, "Removed sector spawn");
    }

    private static int sectorSpawnClear(CommandContext<CommandSourceStack> context, boolean attacker) {
        if (!checkBreakthroughEdit(context)) return 0;
        BreakthroughSectorDefinition sector = sector(context, StringArgumentType.getString(context, "sector"));
        if (sector == null) return failure(context, "Unknown sector");
        int count = sector.spawns(attacker).size(); sector.clearSpawns(attacker); dirty(context);
        return success(context, "Cleared " + count + " sector spawn(s)");
    }

    private static boolean checkBreakthrough(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())) { failure(context, "Select breakthrough mode first"); return false; }
        if (data.activeMap() == null) { failure(context, "No active map"); return false; }
        return true;
    }
    private static boolean checkBreakthroughEdit(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return false;
        if (!MatchManager.get().canChangeArena()) { failure(context, "Cannot edit breakthrough map during a match"); return false; }
        return true;
    }
    private static BreakthroughSectorDefinition sector(CommandContext<CommandSourceStack> context, String id) {
        return SFGameSavedData.get(context.getSource().getServer()).activeMap().breakthrough().sector(id).orElse(null);
    }
    private static void dirty(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.setDirty();
        MatchManager.get().saveActiveMapConfiguration();
        MatchManager.get().arenaSelectionChanged();
    }
    private static String displayPoint(CapturePointDefinition point) {
        return point.id() + " " + regionText(point.region()) + " respawn="
                + (point.respawnPosition() == null ? "unset" : positionText(point.respawnPosition()))
                + " nearby=" + (point.nearbyRespawnPosition() == null ? "unset" : positionText(point.nearbyRespawnPosition()));
    }

    private static int modeList(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        GameModeRegistry.all().forEach(mode -> send(context,
                (mode.id().equals(data.selectedMode()) ? "* " : "  ") + mode.id() + " - " + mode.displayName()));
        return GameModeRegistry.all().size();
    }

    private static int modeStatus(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, "Mode=" + data.selectedMode() + ", map=" + data.selectedMap());
        return 1;
    }

    private static int modeSelect(CommandContext<CommandSourceStack> context) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot change mode during a match");
        String modeId = StringArgumentType.getString(context, "mode");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.selectMode(modeId)) return failure(context, "Unknown game mode: " + modeId);
        MatchManager.get().arenaSelectionChanged();
        MatchManager.get().refreshCommandTree();
        return success(context, "Selected mode " + modeId + " with map " + data.selectedMap());
    }

    private static int mapList(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.maps().forEach(map -> send(context, (map.id().equals(data.selectedMap()) ? "* " : "  ")
                + map.id() + (data.mapConfigured(map) ? " [configured]" : " [incomplete]")));
        return data.maps().size();
    }

    private static int mapStatus(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        ArenaMap map = data.activeMap();
        if (map == null) return failure(context, "No active map");
        send(context, "Mode=" + data.selectedMode() + ", map=" + map.id() + ", configured=" + data.mapConfigured(map)
                + ", mapLobby=" + (map.lobby() != null) + ", defaultLobby=" + (data.defaultLobby() != null)
                + ", enabledTeams=" + map.enabledTeams()
                + ", spawns=" + TeamSide.PLAYABLE.stream().map(side -> side.id() + ":" + map.spawns(side).size()).toList());
        if (GameModeRegistry.DOMINATION.equals(data.selectedMode())) {
            send(context, "pointStrategy=" + MatchManager.get().rules().dominationStrategy().name().toLowerCase()
                    + ", capturePoints=" + map.domination().points().size());
        }
        return data.mapConfigured(map) ? 1 : 0;
    }

    private static int mapCreate(CommandContext<CommandSourceStack> context) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot create a map during a match");
        String mapId = StringArgumentType.getString(context, "map");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.createMap(mapId)) return failure(context, "Invalid or duplicate map id: " + mapId);
        try {
            MatchManager.get().createMapConfiguration(data.selectedMode(), data.selectedMap());
        } catch (IllegalStateException exception) {
            return failure(context, exception.getMessage());
        }
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Created and selected map " + data.selectedMode() + "/" + mapId);
    }

    private static int mapSelect(CommandContext<CommandSourceStack> context) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot change map during a match");
        String mapId = StringArgumentType.getString(context, "map");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.selectMap(mapId)) return failure(context, "Unknown map for current mode: " + mapId);
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Selected map " + data.selectedMode() + "/" + mapId);
    }

    private static int mapRemove(CommandContext<CommandSourceStack> context) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot remove a map during a match");
        String mapId = StringArgumentType.getString(context, "map");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        try {
            MatchManager.get().ensureMapConfigurationRemovable(data.selectedMode(), mapId);
            if (!data.removeMap(mapId)) return failure(context, "Map does not exist or is the last map for this mode");
            MatchManager.get().removeMapConfiguration(data.selectedMode(), mapId);
        } catch (IllegalStateException exception) {
            return failure(context, exception.getMessage());
        }
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Removed map " + mapId + "; selected " + data.selectedMap());
    }

    private static int teamStatus(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, TeamSide.PLAYABLE.stream().map(side -> side.id().toUpperCase() + " -> " + data.teamName(side))
                .collect(java.util.stream.Collectors.joining(", ")));
        return 1;
    }

    private static int bindTeam(CommandContext<CommandSourceStack> context, TeamSide side) {
        String name = StringArgumentType.getString(context, "team");
        PlayerTeam team = context.getSource().getServer().getScoreboard().getPlayerTeam(name);
        if (team == null) return failure(context, "Vanilla team does not exist: " + name);
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (TeamSide.PLAYABLE.stream().filter(other -> other != side).anyMatch(other -> name.equals(data.teamName(other)))) {
            return failure(context, "Each SFGame side must bind a different vanilla team");
        }
        data.teamName(side, name);
        return success(context, side + " bound to " + name);
    }

    private static int setTeam(CommandContext<CommandSourceStack> context, TeamSide side) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        MatchManager manager = MatchManager.get();
        manager.teams().ensureDefaultTeams(context.getSource().getServer(), data);
        long assigned = context.getSource().getServer().getPlayerList().getPlayers().stream()
                .filter(p -> manager.teams().sideOf(p, data) != TeamSide.NONE).count();
        long newPlayers = players.stream().filter(p -> manager.teams().sideOf(p, data) == TeamSide.NONE).count();
        if (!manager.rules().permitsPlayerCount(assigned + newPlayers)) {
            return failure(context, "Assigning " + players.size() + " players would exceed maxPlayers");
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            TeamSide target = side == TeamSide.NONE
                    ? manager.teams().balancedSide(context.getSource().getServer(), data) : side;
            if (target == TeamSide.NONE) return failure(context, "No bound vanilla team is available for random assignment");
            if (manager.teams().assign(player, target, data)) {
                manager.redeploy(player);
                changed++;
            }
        }
        return changed > 0 ? success(context, "Assigned " + changed + " player(s)")
                : failure(context, "Could not assign any players");
    }

    private static int removeTeam(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        players.forEach(MatchManager.get().teams()::remove);
        return success(context, "Removed " + players.size() + " player(s) from their vanilla team");
    }

    private static int rulesList(CommandContext<CommandSourceStack> context) {
        MatchManager manager = MatchManager.get();
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        MatchRules rules = manager.rules();
        send(context, "mode=" + data.selectedMode() + ", map=" + data.selectedMap()
                + ", parent=" + manager.ruleConfigs().parent(data.selectedMode(), data.selectedMap()));
        send(context, rulesText(rules));
        return 1;
    }

    private static int rulesGet(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        MatchRules rules = MatchManager.get().rules();
        String value;
        try { value = ruleValue(rules, key); }
        catch (IllegalArgumentException | IllegalStateException exception) { return failure(context, exception.getMessage()); }
        send(context, key + "=" + value);
        return 1;
    }

    private static int rulesSetGeneric(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        String input = StringArgumentType.getString(context, "value");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        try {
            AdminRuleCatalog.Definition definition = AdminRuleCatalog.find(data.selectedMode(), key)
                    .orElseThrow(() -> new IllegalArgumentException("Rule is unavailable in this mode: " + key));
            Object value = AdminRuleCatalog.parse(definition, input);
            switch (definition.type()) {
                case INTEGER -> MatchManager.get().setRule(key, (Integer) value);
                case DECIMAL -> MatchManager.get().setRule(key, (Double) value);
                case BOOLEAN -> MatchManager.get().setRule(key, (Boolean) value);
                case ENUM -> MatchManager.get().setRule(key, (String) value);
            }
            return success(context, key + "=" + ruleValue(MatchManager.get().rules(), key));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return failure(context, exception.getMessage());
        }
    }

    private static int rulesReset(CommandContext<CommandSourceStack> context) {
        MatchManager.get().resetRules();
        return success(context, "Current map rule overrides cleared; inherited rules now apply");
    }

    private static int rulesInherit(CommandContext<CommandSourceStack> context) {
        String parent = StringArgumentType.getString(context, "parent");
        try { MatchManager.get().setRuleParent(parent); }
        catch (IllegalArgumentException | IllegalStateException exception) { return failure(context, exception.getMessage()); }
        return success(context, "Current map rules now inherit " + parent);
    }

    private static int classReload(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        List<String> errors = new ArrayList<>(MatchManager.get().classes().reload(data));
        boolean captains = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())
                && MatchManager.get().rules().breakthroughVariant() == BreakthroughVariant.CAPTAIN;
        errors.addAll(MatchManager.get().loadouts().validate(MatchManager.get().classes(), data.selectedMode(), data.selectedMap(), data.enabledTeams(), captains)
                .stream().filter(e -> !errors.contains(e)).toList());
        if (!errors.isEmpty()) {
            errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
            return 0;
        }
        return success(context, "Loaded " + MatchManager.get().classes().allForMode(data.selectedMode(), data.selectedMap()).size() + " classes for " + data.selectedMode() + " map " + data.selectedMap());
    }

    private static int classValidate(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        boolean captains = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode())
                && MatchManager.get().rules().breakthroughVariant() == BreakthroughVariant.CAPTAIN;
        List<String> errors = MatchManager.get().loadouts().validate(MatchManager.get().classes(), data.selectedMode(), data.selectedMap(), data.enabledTeams(), captains);
        if (!errors.isEmpty()) {
            errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
            return 0;
        }
        return success(context, "All class and TACZ resources are valid");
    }

    private static int classList(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int count = 0;
        for (TeamSide side : data.enabledTeams().isEmpty() ? TeamSide.PLAYABLE : data.enabledTeams()) {
            send(context, side.id() + ":");
            for (ClassDefinition c : MatchManager.get().classes().allForTeam(data.selectedMode(), data.selectedMap(), side)) {
                send(context, "  " + c.id() + " - " + c.displayName() + " (" + c.gunId() + ")"); count++;
            }
        }
        return count;
    }

    private static int classListCaptain(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int count = 0;
        for (TeamSide side : data.enabledTeams().isEmpty() ? TeamSide.PLAYABLE : data.enabledTeams()) {
            send(context, side.id() + " captain:");
            for (ClassDefinition c : MatchManager.get().classes().captainClassesForTeam(data.selectedMode(), data.selectedMap(), side)) {
                send(context, "  " + c.id() + " - " + c.displayName() + " (" + c.gunId() + ")"); count++;
            }
        }
        return count;
    }

    private static int classSet(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String classId = StringArgumentType.getString(context, "class");
        return MatchManager.get().selectClass(player, classId) ? success(context, "Selected " + classId + " for " + player.getGameProfile().getName())
                : failure(context, "Unknown class " + classId);
    }

    private static int classSetCaptain(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String classId = StringArgumentType.getString(context, "class");
        return MatchManager.get().selectCaptainClass(player, classId)
                ? success(context, "Selected captain class " + classId + " for " + player.getGameProfile().getName())
                : failure(context, "Unknown captain class " + classId);
    }

    private static int captainVote(CommandContext<CommandSourceStack> context, boolean abstain)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer voter = context.getSource().getPlayerOrException();
        ServerPlayer candidate = abstain ? null : EntityArgument.getPlayer(context, "candidate");
        return MatchManager.get().breakthrough().vote(voter, candidate, abstain, MatchManager.get())
                ? success(context, abstain ? "Vote recorded as abstain" : "Captain vote recorded")
                : failure(context, "Captain voting is not available for you now");
    }

    private static int captainStatus(CommandContext<CommandSourceStack> context) {
        var runtime = MatchManager.get().breakthrough();
        String name = runtime.captain() == null ? "none" : context.getSource().getServer().getProfileCache()
                .get(runtime.captain()).map(profile -> profile.getName()).orElse(runtime.captain().toString());
        send(context, "attacker=" + runtime.attacker().id() + ", captain=" + name + ", electionSeconds=" + runtime.electionSeconds());
        return 1;
    }

    private static int captainSet(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TeamSide side = TeamSide.fromId(StringArgumentType.getString(context, "side"));
        if (side != MatchManager.get().breakthrough().attacker()) return failure(context, "Only the current attacker has a captain");
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        return MatchManager.get().breakthrough().setCaptain(player, MatchManager.get())
                ? success(context, "Captain set to " + player.getGameProfile().getName()) : failure(context, "Player is not an active attacker");
    }

    private static int captainReelect(CommandContext<CommandSourceStack> context) {
        TeamSide side = TeamSide.fromId(StringArgumentType.getString(context, "side"));
        MatchRules rules = MatchManager.get().rules();
        return MatchManager.get().breakthrough().reelect(side, rules, MatchManager.get())
                ? success(context, "Captain reelection started") : failure(context, "Only the current attacker elects a captain");
    }

    private static String ruleValue(MatchRules rules, String key) {
        if (!GameModeRegistry.DOMINATION.equals(rules.modeId())
                && !GameModeRegistry.BREAKTHROUGH.equals(rules.modeId())
                && !GameModeRegistry.CAPTURE_THE_FLAG.equals(rules.modeId())
                && isCaptureOnlyRule(key)) {
            throw new IllegalArgumentException(key + " is only available in a capture mode");
        }
        if (!GameModeRegistry.DOMINATION.equals(rules.modeId()) && isDominationOnlyRule(key)) {
            throw new IllegalArgumentException(key + " is only available in domination mode");
        }
        if (!GameModeRegistry.BREAKTHROUGH.equals(rules.modeId()) && isBreakthroughOnlyRule(key)) {
            throw new IllegalArgumentException(key + " is only available in breakthrough mode");
        }
        if (!GameModeRegistry.BREAKTHROUGH.equals(rules.modeId())
                && !GameModeRegistry.CAPTURE_THE_FLAG.equals(rules.modeId())
                && key.equals("attackerTickets")) {
            throw new IllegalArgumentException(key + " is only available in breakthrough or ctf mode");
        }
        if (!GameModeRegistry.CAPTURE_THE_FLAG.equals(rules.modeId()) && isCtfOnlyRule(key)) {
            throw new IllegalArgumentException(key + " is only available in ctf mode");
        }
        return switch (key) {
            case "maxPlayers" -> Integer.toString(rules.maxPlayers());
            case "scoreLimit" -> Integer.toString(rules.scoreLimit());
            case "timeLimitSeconds" -> Integer.toString(rules.timeLimitSeconds());
            case "startCountdownSeconds" -> Integer.toString(rules.startCountdownSeconds());
            case "respawnSeconds" -> Integer.toString(rules.respawnSeconds());
            case "respawnProtectionSeconds" -> Integer.toString(rules.respawnProtectionSeconds());
            case "resultSeconds" -> Integer.toString(rules.resultSeconds());
            case "captureTimeSeconds" -> Integer.toString(rules.captureTimeSeconds());
            case "captureUsePlayerDifference" -> Boolean.toString(rules.captureUsePlayerDifference());
            case "captureDifferenceCoefficient" -> Double.toString(rules.captureDifferenceCoefficient());
            case "captureMaxMultiplier" -> Integer.toString(rules.captureMaxMultiplier());
            case "scoreIntervalSeconds" -> Integer.toString(rules.scoreIntervalSeconds());
            case "scorePerPoint" -> Integer.toString(rules.scorePerPoint());
            case "syncHoldSeconds" -> Integer.toString(rules.syncHoldSeconds());
            case "dominationStrategy" -> rules.dominationStrategy().name().toLowerCase(java.util.Locale.ROOT);
            case "breakthroughVariant" -> rules.breakthroughVariant().name().toLowerCase(java.util.Locale.ROOT);
            case "breakthroughLegs" -> Integer.toString(rules.breakthroughLegs());
            case "breakthroughAttacker" -> rules.breakthroughAttacker().id();
            case "breakthroughDefender" -> rules.breakthroughDefender().id();
            case "attackerTickets" -> Integer.toString(rules.attackerTickets());
            case "sectorTransitionSeconds" -> Integer.toString(rules.sectorTransitionSeconds());
            case "captainVoteSeconds" -> Integer.toString(rules.captainVoteSeconds());
            case "captainReplacementVoteSeconds" -> Integer.toString(rules.captainReplacementVoteSeconds());
            case "attackerCaptainGlowing" -> Boolean.toString(rules.attackerCaptainGlowing());
            case "mapBlockBreaking" -> Boolean.toString(rules.mapBlockBreaking());
            case "mapSnapshotMode" -> rules.mapSnapshotMode().id();
            case "mapRestorePartitionDelayTicks" -> Integer.toString(rules.mapRestorePartitionDelayTicks());
            case "mapRestoreAdaptiveThrottling" -> Boolean.toString(rules.mapRestoreAdaptiveThrottling());
            case "mapRestoreTargetTickMillis" -> Integer.toString(rules.mapRestoreTargetTickMillis());
            case "mapRestoreMaxPartitionsPerTick" -> Integer.toString(rules.mapRestoreMaxPartitionsPerTick());
            case "attackerCaptainCaptureWeight" -> Double.toString(rules.attackerCaptainCaptureWeight());
            case "defenderCaptureWeight" -> Double.toString(rules.defenderCaptureWeight());
            case "ctfFlagReturnSeconds" -> Integer.toString(rules.ctfFlagReturnSeconds());
            case "ctfHomeCaptureTimeSeconds" -> Integer.toString(rules.ctfHomeCaptureTimeSeconds());
            case "killCurrency" -> Integer.toString(rules.killCurrency());
            case "ctfTerritoryUnlockCurrency" -> Integer.toString(rules.ctfTerritoryUnlockCurrency());
            case "ctfForwardFlagReplantCurrency" -> Integer.toString(rules.ctfForwardFlagReplantCurrency());
            case "ctfForwardFlagCaptureCurrency" -> Integer.toString(rules.ctfForwardFlagCaptureCurrency());
            case "ctfHomeFlagCaptureCurrency" -> Integer.toString(rules.ctfHomeFlagCaptureCurrency());
            case "ctfVariant" -> rules.ctfVariant().id();
            case "ctfAttacker" -> rules.ctfAttacker().id();
            case "ctfDefender" -> rules.ctfDefender().id();
            case "ctfCarrierRestriction" -> rules.ctfCarrierRestriction().id();
            default -> throw new IllegalArgumentException("Unknown rule " + key);
        };
    }

    private static boolean isDominationOnlyRule(String key) {
        return key.equals("dominationStrategy") || key.equals("scoreIntervalSeconds")
                || key.equals("scorePerPoint") || key.equals("syncHoldSeconds");
    }

    private static boolean isCaptureOnlyRule(String key) {
        return key.equals("captureTimeSeconds") || key.equals("captureUsePlayerDifference")
                || key.equals("captureDifferenceCoefficient") || key.equals("captureMaxMultiplier");
    }

    private static boolean isBreakthroughOnlyRule(String key) {
        return key.equals("breakthroughVariant") || key.equals("breakthroughLegs")
                || key.equals("breakthroughAttacker") || key.equals("breakthroughDefender")
                || key.equals("sectorTransitionSeconds") || key.equals("captainVoteSeconds")
                || key.equals("captainReplacementVoteSeconds") || key.equals("attackerCaptainGlowing")
                || key.equals("attackerCaptainCaptureWeight")
                || key.equals("defenderCaptureWeight");
    }

    private static boolean isCtfOnlyRule(String key) {
        return key.equals("ctfVariant") || key.equals("ctfAttacker") || key.equals("ctfDefender")
                || key.equals("ctfCarrierRestriction") || key.equals("ctfFlagReturnSeconds")
                || key.equals("ctfHomeCaptureTimeSeconds") || key.equals("ctfTerritoryUnlockCurrency")
                || key.equals("ctfForwardFlagReplantCurrency") || key.equals("ctfForwardFlagCaptureCurrency")
                || key.equals("ctfHomeFlagCaptureCurrency");
    }


    private static String rulesText(MatchRules r) {
        String common = "maxPlayers=" + r.maxPlayers() + ", scoreLimit=" + r.scoreLimit() + ", timeLimitSeconds=" + r.timeLimitSeconds()
                + ", startCountdownSeconds=" + r.startCountdownSeconds() + ", respawnSeconds=" + r.respawnSeconds()
                + ", respawnProtectionSeconds=" + r.respawnProtectionSeconds() + ", resultSeconds=" + r.resultSeconds()
                + ", mapBlockBreaking=" + r.mapBlockBreaking()
                + ", mapSnapshotMode=" + r.mapSnapshotMode().id()
                + ", mapRestorePartitionDelayTicks=" + r.mapRestorePartitionDelayTicks()
                + ", mapRestoreAdaptiveThrottling=" + r.mapRestoreAdaptiveThrottling()
                + ", mapRestoreTargetTickMillis=" + r.mapRestoreTargetTickMillis()
                + ", mapRestoreMaxPartitionsPerTick=" + r.mapRestoreMaxPartitionsPerTick();
        if (GameModeRegistry.DOMINATION.equals(r.modeId())) return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
                + ", dominationStrategy=" + r.dominationStrategy().name().toLowerCase(java.util.Locale.ROOT)
                + ", captureUsePlayerDifference=" + r.captureUsePlayerDifference()
                + ", captureDifferenceCoefficient=" + r.captureDifferenceCoefficient()
                + ", captureMaxMultiplier=" + r.captureMaxMultiplier()
                + ", scoreIntervalSeconds=" + r.scoreIntervalSeconds() + ", scorePerPoint=" + r.scorePerPoint()
                + ", syncHoldSeconds=" + r.syncHoldSeconds();
        if (GameModeRegistry.BREAKTHROUGH.equals(r.modeId())) return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
                + ", breakthroughVariant=" + r.breakthroughVariant().name().toLowerCase(java.util.Locale.ROOT)
                + ", breakthroughLegs=" + r.breakthroughLegs()
                + ", breakthroughAttacker=" + r.breakthroughAttacker().id()
                + ", breakthroughDefender=" + r.breakthroughDefender().id()
                + ", captureUsePlayerDifference=" + r.captureUsePlayerDifference()
                + ", captureDifferenceCoefficient=" + r.captureDifferenceCoefficient()
                + ", captureMaxMultiplier=" + r.captureMaxMultiplier() + ", attackerTickets=" + r.attackerTickets()
                + ", sectorTransitionSeconds=" + r.sectorTransitionSeconds() + ", captainVoteSeconds=" + r.captainVoteSeconds()
                + ", captainReplacementVoteSeconds=" + r.captainReplacementVoteSeconds()
                + ", attackerCaptainGlowing=" + r.attackerCaptainGlowing()
                + ", attackerCaptainCaptureWeight=" + r.attackerCaptainCaptureWeight()
                + ", defenderCaptureWeight=" + r.defenderCaptureWeight();
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(r.modeId())) return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
                + ", ctfVariant=" + r.ctfVariant().id()
                + ", ctfAttacker=" + r.ctfAttacker().id() + ", ctfDefender=" + r.ctfDefender().id()
                + ", ctfCarrierRestriction=" + r.ctfCarrierRestriction().id()
                + ", captureUsePlayerDifference=" + r.captureUsePlayerDifference()
                + ", captureDifferenceCoefficient=" + r.captureDifferenceCoefficient()
                + ", captureMaxMultiplier=" + r.captureMaxMultiplier() + ", attackerTickets=" + r.attackerTickets()
                + ", ctfFlagReturnSeconds=" + r.ctfFlagReturnSeconds()
                + ", ctfHomeCaptureTimeSeconds=" + r.ctfHomeCaptureTimeSeconds();
        return common;
    }

    private static int success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private SFGameCommands() {}
}

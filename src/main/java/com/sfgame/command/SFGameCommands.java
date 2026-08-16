package com.sfgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
import com.sfgame.data.CapturePointDefinition;
import com.sfgame.data.CaptureRegion;
import com.sfgame.data.PointActivationStrategy;
import com.sfgame.data.SquareCaptureRegion;
import com.sfgame.data.BreakthroughVariant;
import com.sfgame.data.BreakthroughSectorDefinition;
import com.sfgame.data.BreakthroughVehicleDefinition;
import com.sfgame.data.CtfVariant;
import com.sfgame.data.CarrierRestriction;
import com.sfgame.data.CtfForwardFlagDefinition;
import com.sfgame.data.CtfHomeFlagDefinition;
import com.sfgame.data.CaptureTheFlagMapConfig;
import com.sfgame.game.GameModeDefinition;
import com.sfgame.game.GameModeRegistry;
import com.sfgame.game.MatchManager;
import com.sfgame.game.TeamSide;
import com.sfgame.network.SFGameNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SFGameCommands {
    private static final String[] COMMON_RULE_KEYS = {"maxPlayers", "scoreLimit", "timeLimitSeconds",
            "startCountdownSeconds", "respawnSeconds", "respawnProtectionSeconds", "resultSeconds"};
    private static final String[] DOMINATION_RULE_KEYS = {"captureTimeSeconds", "captureUsePlayerDifference",
            "captureDifferenceCoefficient", "captureMaxMultiplier", "scoreIntervalSeconds", "scorePerPoint", "syncHoldSeconds"};
    private static final String[] BREAKTHROUGH_RULE_KEYS = {"captureTimeSeconds", "captureUsePlayerDifference",
            "captureDifferenceCoefficient", "captureMaxMultiplier", "attackerTickets", "sectorTransitionSeconds",
            "captainVoteSeconds", "captainReplacementVoteSeconds", "attackerCaptainGlowing",
            "attackerCaptainCaptureWeight", "defenderCaptureWeight", "breakthroughBlockBreaking"};
    private static final String[] CTF_RULE_KEYS = {"captureTimeSeconds", "captureUsePlayerDifference",
            "captureDifferenceCoefficient", "captureMaxMultiplier", "attackerTickets", "ctfFlagReturnSeconds",
            "ctfHomeCaptureTimeSeconds"};
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
    private static final SuggestionProvider<CommandSourceStack> RULE_SUGGESTIONS = (context, builder) -> {
        String mode = SFGameSavedData.get(context.getSource().getServer()).selectedMode();
        java.util.stream.Stream<String> keys = java.util.Arrays.stream(COMMON_RULE_KEYS);
        if (GameModeRegistry.DOMINATION.equals(mode)) keys = java.util.stream.Stream.concat(keys, java.util.Arrays.stream(DOMINATION_RULE_KEYS));
        if (GameModeRegistry.BREAKTHROUGH.equals(mode)) keys = java.util.stream.Stream.concat(keys, java.util.Arrays.stream(BREAKTHROUGH_RULE_KEYS));
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(mode)) keys = java.util.stream.Stream.concat(keys, java.util.Arrays.stream(CTF_RULE_KEYS));
        return SharedSuggestionProvider.suggest(keys, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(GameModeRegistry.all().stream().map(GameModeDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).maps().stream()
                    .map(ArenaMap::id), builder);
    private static final SuggestionProvider<CommandSourceStack> RULE_PARENT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("base"),
                    SFGameSavedData.get(context.getSource().getServer()).maps().stream().map(ArenaMap::id)), builder);
    private static final SuggestionProvider<CommandSourceStack> BREAKTHROUGH_VEHICLE_SUGGESTIONS = (context, builder) -> {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        return SharedSuggestionProvider.suggest(data.activeMap() == null ? java.util.stream.Stream.empty()
                : data.activeMap().breakthrough().vehicles().stream().map(BreakthroughVehicleDefinition::id), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).activeMap() == null
                    ? java.util.stream.Stream.empty() : SFGameSavedData.get(context.getSource().getServer()).activeMap()
                    .domination().points().stream().map(CapturePointDefinition::id), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sfgame")
                .then(Commands.literal("menu").executes(SFGameCommands::menu))
                .then(Commands.literal("leave").executes(SFGameCommands::leave))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(SFGameCommands::status))
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
                        .then(Commands.literal("strategy")
                                .then(Commands.literal("async").executes(c -> pointStrategy(c, PointActivationStrategy.ASYNC)))
                                .then(Commands.literal("sync").executes(c -> pointStrategy(c, PointActivationStrategy.SYNC))))
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
                .then(ruleCommands("rule"))
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
                .then(sectorCommands())
                .then(captainCommands()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> breakthroughCommands() {
        return Commands.literal("breakthrough")
                .requires(source -> source.hasPermission(2)
                        && modeIs(source, GameModeRegistry.BREAKTHROUGH))
                .then(Commands.literal("variant")
                        .then(Commands.literal("normal").executes(context -> breakthroughVariant(context, BreakthroughVariant.NORMAL)))
                        .then(Commands.literal("captain").executes(context -> breakthroughVariant(context, BreakthroughVariant.CAPTAIN))))
                .then(Commands.literal("legs").then(Commands.argument("legs", IntegerArgumentType.integer(1, 2))
                        .executes(SFGameCommands::breakthroughLegs)))
                .then(Commands.literal("roles").then(Commands.argument("attacker", StringArgumentType.word())
                        .then(Commands.argument("defender", StringArgumentType.word()).executes(SFGameCommands::breakthroughRoles))))
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
        root.then(Commands.literal("variant")
                .then(Commands.literal("classic").executes(c -> ctfVariant(c, CtfVariant.CLASSIC)))
                .then(Commands.literal("assault").executes(c -> ctfVariant(c, CtfVariant.ASSAULT)))
                .then(Commands.literal("territory").executes(c -> ctfVariant(c, CtfVariant.TERRITORY))));
        root.then(Commands.literal("roles").then(Commands.argument("attacker", StringArgumentType.word())
                .then(Commands.argument("defender", StringArgumentType.word()).executes(SFGameCommands::ctfRoles))));
        root.then(Commands.literal("carrier")
                .then(Commands.literal("normal").executes(c -> ctfCarrier(c, CarrierRestriction.NORMAL)))
                .then(Commands.literal("movement_limited").executes(c -> ctfCarrier(c, CarrierRestriction.MOVEMENT_LIMITED)))
                .then(Commands.literal("no_weapons").executes(c -> ctfCarrier(c, CarrierRestriction.NO_WEAPONS))));
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

        LiteralArgumentBuilder<CommandSourceStack> build = Commands.literal("build");
        build.then(Commands.literal("setbox").executes(SFGameCommands::ctfBuildSetBox))
                .then(Commands.literal("clear").executes(SFGameCommands::ctfBuildClear))
                .then(Commands.literal("allow").then(Commands.argument("block", StringArgumentType.word()).executes(SFGameCommands::ctfBuildAllow)))
                .then(Commands.literal("disallow").then(Commands.argument("block", StringArgumentType.word()).executes(SFGameCommands::ctfBuildDisallow)))
                .then(Commands.literal("allowlist").executes(SFGameCommands::ctfBuildAllowList))
                .then(Commands.literal("snapshot").then(Commands.literal("save").executes(SFGameCommands::ctfSnapshotSave))
                        .then(Commands.literal("restore").executes(SFGameCommands::ctfSnapshotRestore))
                        .then(Commands.literal("status").executes(SFGameCommands::ctfSnapshotStatus))
                        .then(Commands.literal("clear").executes(SFGameCommands::ctfSnapshotClear)));
        root.then(build);
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shopCommands() {
        return Commands.literal("shop")
                .then(Commands.literal("list").executes(SFGameCommands::shopList))
                .then(Commands.literal("buy").then(Commands.argument("item", StringArgumentType.word())
                        .executes(SFGameCommands::shopBuy)))
                .then(Commands.literal("reload").requires(source -> source.hasPermission(2))
                        .executes(SFGameCommands::shopReload));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ruleCommands(String literal) {
        return Commands.literal(literal).requires(s -> s.hasPermission(2))
                .then(breakthroughCommands())
                .then(ctfCommands())
                .then(Commands.literal("list").executes(SFGameCommands::rulesList))
                .then(Commands.literal("reset").executes(SFGameCommands::rulesReset))
                .then(Commands.literal("inherit").then(Commands.argument("parent", StringArgumentType.word())
                        .suggests(RULE_PARENT_SUGGESTIONS).executes(SFGameCommands::rulesInherit)))
                .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.word())
                        .suggests(RULE_SUGGESTIONS).executes(SFGameCommands::rulesGet)))
                .then(Commands.literal("set")
                        .then(Commands.literal("captureUsePlayerDifference")
                                .requires(s -> modeIs(s, GameModeRegistry.DOMINATION, GameModeRegistry.BREAKTHROUGH,
                                        GameModeRegistry.CAPTURE_THE_FLAG))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> rulesSetBoolean(context, "captureUsePlayerDifference"))))
                        .then(Commands.literal("attackerCaptainGlowing")
                                .requires(s -> modeIs(s, GameModeRegistry.BREAKTHROUGH))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> rulesSetBoolean(context, "attackerCaptainGlowing"))))
                        .then(Commands.literal("breakthroughBlockBreaking")
                                .requires(s -> modeIs(s, GameModeRegistry.BREAKTHROUGH))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> rulesSetBoolean(context, "breakthroughBlockBreaking"))))
                        .then(Commands.literal("captureDifferenceCoefficient")
                                .requires(s -> modeIs(s, GameModeRegistry.DOMINATION, GameModeRegistry.BREAKTHROUGH,
                                        GameModeRegistry.CAPTURE_THE_FLAG))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0))
                                        .executes(c -> rulesSetDouble(c, "captureDifferenceCoefficient"))))
                        .then(Commands.literal("attackerCaptainCaptureWeight")
                                .requires(s -> modeIs(s, GameModeRegistry.BREAKTHROUGH))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 10.0))
                                        .executes(c -> rulesSetDouble(c, "attackerCaptainCaptureWeight"))))
                        .then(Commands.literal("defenderCaptureWeight")
                                .requires(s -> modeIs(s, GameModeRegistry.BREAKTHROUGH))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0))
                                        .executes(c -> rulesSetDouble(c, "defenderCaptureWeight"))))
                        // Integer-only mode rules are intentionally exposed as
                        // literals too, so mode-inapplicable rules never appear
                        // in command suggestions or execute accidentally.
                        .then(Commands.literal("scoreIntervalSeconds")
                                .requires(s -> modeIs(s, GameModeRegistry.DOMINATION))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(SFGameCommands::rulesSet)))
                        .then(Commands.literal("scorePerPoint")
                                .requires(s -> modeIs(s, GameModeRegistry.DOMINATION))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(SFGameCommands::rulesSet)))
                        .then(Commands.literal("syncHoldSeconds")
                                .requires(s -> modeIs(s, GameModeRegistry.DOMINATION))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(SFGameCommands::rulesSet)))
                        .then(Commands.literal("ctfFlagReturnSeconds")
                                .requires(s -> modeIs(s, GameModeRegistry.CAPTURE_THE_FLAG))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(SFGameCommands::rulesSet)))
                        .then(Commands.literal("ctfHomeCaptureTimeSeconds")
                                .requires(s -> modeIs(s, GameModeRegistry.CAPTURE_THE_FLAG))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(SFGameCommands::rulesSet)))
                        .then(Commands.argument("key", StringArgumentType.word()).suggests(RULE_SUGGESTIONS)
                                .then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(SFGameCommands::rulesSet))));
    }

    private static boolean modeIs(CommandSourceStack source, String... modes) {
        String selected = SFGameSavedData.get(source.getServer()).selectedMode();
        return java.util.Arrays.stream(modes).anyMatch(selected::equals);
    }

    private static int ctfVariant(CommandContext<CommandSourceStack> context, CtfVariant variant) {
        if (!checkCtfEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.activeMap().captureTheFlag().variant(variant); data.setDirty(); MatchManager.get().arenaSelectionChanged();
        return success(context, "CTF variant set to " + variant.id());
    }

    private static int ctfRoles(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        TeamSide attacker = TeamSide.fromId(StringArgumentType.getString(context, "attacker"));
        TeamSide defender = TeamSide.fromId(StringArgumentType.getString(context, "defender"));
        try {
            SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            data.activeMap().captureTheFlag().roles(attacker, defender); data.setDirty(); MatchManager.get().arenaSelectionChanged();
            return success(context, "CTF roles set to " + attacker.id() + " attack / " + defender.id() + " defend");
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfCarrier(CommandContext<CommandSourceStack> context, CarrierRestriction restriction) {
        if (!checkCtfEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.activeMap().captureTheFlag().carrierRestriction(restriction); data.setDirty();
        return success(context, "CTF carrier restriction set to " + restriction.id());
    }

    private static int ctfStatus(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!GameModeRegistry.CAPTURE_THE_FLAG.equals(data.selectedMode()) || data.activeMap() == null) return failure(context, "Select ctf mode first");
        var config = data.activeMap().captureTheFlag();
        send(context, "variant=" + config.variant().id() + ", carrierRestriction=" + config.carrierRestriction().id()
                + ", attacker=" + config.attacker().id() + ", defender=" + config.defender().id()
                + ", homes=" + config.homes().size() + ", forwardFlags=" + config.forwardFlags().size());
        return config.validate(data.activeMap().enabledTeams()).isEmpty() ? 1 : 0;
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
                region = new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                        Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), null, null);
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
        CaptureRegion region = new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), null, null);
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
        return ctfReplaceForward(context, old -> old.withRegion(new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), old.region().minY(), old.region().maxY())));
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

    private static int ctfBuildSetBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkCtfEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException(); ArenaPosition first = CTF_POS_1.get(player.getUUID()), second = CTF_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Build corners must be in the same dimension");
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag();
        config.build().region(new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), null, null));
        dirty(context); return success(context, "Set CTF build box");
    }

    private static int ctfBuildClear(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().build().clearRegion(); dirty(context);
        return success(context, "Cleared CTF build box");
    }

    private static int ctfBuildAllow(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        try {
            String id = StringArgumentType.getString(context, "block");
            SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().build().allow(id); dirty(context);
            return success(context, "Allowed block " + id);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfBuildDisallow(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        try {
            String id = StringArgumentType.getString(context, "block");
            var build = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().build();
            if (!build.disallow(id)) return failure(context, "Block was not in the allowlist: " + id);
            dirty(context); return success(context, "Disallowed block " + id);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int ctfBuildAllowList(CommandContext<CommandSourceStack> context) {
        if (!checkCtf(context)) return 0;
        var build = SFGameSavedData.get(context.getSource().getServer()).activeMap().captureTheFlag().build();
        build.allowedBlocks().forEach(id -> send(context, id)); return build.allowedBlocks().size();
    }

    private static int ctfSnapshotSave(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        try { SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            com.sfgame.game.CtfBuildSnapshotService.save(context.getSource().getServer(), data.activeMap()); data.setDirty();
            return success(context, "Saved CTF map snapshot");
        } catch (Exception exception) { return failure(context, exception.getMessage() == null ? "Could not save CTF snapshot" : exception.getMessage()); }
    }

    private static int ctfSnapshotRestore(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        try { SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            com.sfgame.game.CtfBuildSnapshotService.restore(context.getSource().getServer(), data.activeMap());
            return success(context, "Restored CTF map snapshot");
        } catch (Exception exception) { return failure(context, exception.getMessage() == null ? "Could not restore CTF snapshot" : exception.getMessage()); }
    }

    private static int ctfSnapshotStatus(CommandContext<CommandSourceStack> context) {
        if (!checkCtf(context)) return 0; SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, "buildBox=" + (data.activeMap().captureTheFlag().build().region() != null)
                + ", snapshot=" + com.sfgame.game.CtfBuildSnapshotService.exists(context.getSource().getServer(), data.activeMap())); return 1;
    }

    private static int ctfSnapshotClear(CommandContext<CommandSourceStack> context) {
        if (!checkCtfEdit(context)) return 0;
        try { SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer()); com.sfgame.game.CtfBuildSnapshotService.clear(context.getSource().getServer(), data.activeMap()); data.setDirty(); return success(context, "Cleared CTF map snapshot"); }
        catch (Exception exception) { return failure(context, exception.getMessage() == null ? "Could not clear CTF snapshot" : exception.getMessage()); }
    }

    private static int shopList(CommandContext<CommandSourceStack> context) {
        var registry = MatchManager.get().ctfShop();
        registry.items().forEach(item -> send(context, item.id() + " - " + item.name() + " (" + item.price() + ")"));
        registry.errors().forEach(error -> context.getSource().sendFailure(Component.literal(error)));
        return registry.items().size();
    }

    private static int shopBuy(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String item = StringArgumentType.getString(context, "item");
        return MatchManager.get().ctfPurchase(player, item) ? success(context, "Purchased " + item) : failure(context, "Could not purchase " + item);
    }

    private static int shopReload(CommandContext<CommandSourceStack> context) {
        List<String> errors = MatchManager.get().ctfShop().reload();
        if (!errors.isEmpty()) { errors.forEach(error -> context.getSource().sendFailure(Component.literal(error))); return 0; }
        return success(context, "Reloaded CTF shop");
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
        int classResult = classReload(context);
        List<String> ruleErrors = manager.reloadRuleConfigurations();
        List<String> shopErrors = manager.ctfShop().reload();
        ruleErrors.forEach(error -> context.getSource().sendFailure(Component.literal("Rules: " + error)));
        shopErrors.forEach(error -> context.getSource().sendFailure(Component.literal("CTF shop: " + error)));
        manager.refreshCommandTree();
        return classResult > 0 && ruleErrors.isEmpty() && shopErrors.isEmpty() ? 1 : 0;
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
        if (!GameModeRegistry.DOMINATION.equals(mode) && !GameModeRegistry.BREAKTHROUGH.equals(mode)
                && !GameModeRegistry.CAPTURE_THE_FLAG.equals(mode)) {
            return failure(context, "The selected mode has no region position tool");
        }
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot edit regions during a match");
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition position = ArenaPosition.from(player);
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(mode)) {
            (first ? CTF_POS_1 : CTF_POS_2).put(player.getUUID(), position);
        } else {
            (first ? POINT_POS_1 : POINT_POS_2).put(player.getUUID(), position);
        }
        return success(context, "Set " + mode + " pos" + (first ? "1" : "2") + " to " + positionText(position));
    }

    private static int pointAddBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        try {
            addPoint(context, new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                    Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), null, null));
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
        data.setDirty(); MatchManager.get().arenaSelectionChanged();
        context.getSource().sendSuccess(() -> Component.literal("Added capture point " + id).withStyle(ChatFormatting.GREEN), true);
    }

    private static int pointSetBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set /sfgame pos1 and /sfgame pos2 first");
        if (!first.dimension().equals(second.dimension())) return failure(context, "Corners must be in the same dimension");
        return replacePointRegion(context, existing -> new BoxCaptureRegion(first.dimension(),
                Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                Math.min(first.z(), second.z()), Math.max(first.z(), second.z()),
                existing.region().minY(), existing.region().maxY()));
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
            data.activeMap().domination().replace(id, point.withOrder(order)); data.setDirty();
            MatchManager.get().arenaSelectionChanged();
            return success(context, "Set " + id + " order to " + order);
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int pointStrategy(CommandContext<CommandSourceStack> context, PointActivationStrategy strategy) {
        if (!checkPointEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.activeMap().domination().strategy(strategy); data.setDirty(); MatchManager.get().arenaSelectionChanged();
        return success(context, "Domination point strategy set to " + strategy.name().toLowerCase());
    }

    private static int pointList(CommandContext<CommandSourceStack> context) {
        if (!checkDomination(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, "Strategy=" + data.activeMap().domination().strategy().name().toLowerCase()
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
        data.setDirty(); MatchManager.get().arenaSelectionChanged();
        return success(context, "Removed capture point " + id);
    }

    private static int pointClear(CommandContext<CommandSourceStack> context) {
        if (!checkPointEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        int count = data.activeMap().domination().points().size();
        data.activeMap().domination().clear(); data.setDirty(); MatchManager.get().arenaSelectionChanged();
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
            data.setDirty(); MatchManager.get().arenaSelectionChanged();
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

    private static int breakthroughVariant(CommandContext<CommandSourceStack> context, BreakthroughVariant variant) {
        if (!checkBreakthroughEdit(context)) return 0;
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.activeMap().breakthrough().variant(variant); data.setDirty(); MatchManager.get().arenaSelectionChanged();
        return success(context, "Breakthrough mode variant set to " + variant.name().toLowerCase());
    }

    private static int breakthroughLegs(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        int legs = IntegerArgumentType.getInteger(context, "legs");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        data.activeMap().breakthrough().legs(legs); data.setDirty(); MatchManager.get().arenaSelectionChanged();
        return success(context, "Breakthrough mode legs set to " + legs);
    }

    private static int breakthroughRoles(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthroughEdit(context)) return 0;
        TeamSide attacker = TeamSide.fromId(StringArgumentType.getString(context, "attacker"));
        TeamSide defender = TeamSide.fromId(StringArgumentType.getString(context, "defender"));
        try {
            SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
            data.activeMap().breakthrough().roles(attacker, defender); data.setDirty(); MatchManager.get().arenaSelectionChanged();
            return success(context, "Breakthrough mode roles: attacker=" + attacker.id() + ", defender=" + defender.id());
        } catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
    }

    private static int breakthroughStatus(CommandContext<CommandSourceStack> context) {
        if (!checkBreakthrough(context)) return 0;
        var config = SFGameSavedData.get(context.getSource().getServer()).activeMap().breakthrough();
        send(context, "variant=" + config.variant().name().toLowerCase() + ", legs=" + config.legs()
                + ", attacker=" + config.attacker().id() + ", defender=" + config.defender().id()
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
            data.activeMap().breakthrough().addSector(new BreakthroughSectorDefinition(id, order)); data.setDirty();
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
        CaptureRegion region = new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                Math.min(first.z(), second.z()), Math.max(first.z(), second.z()), null, null);
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
        return replaceSectorPoint(context, existing -> new BoxCaptureRegion(first.dimension(), Math.min(first.x(), second.x()),
                Math.max(first.x(), second.x()), Math.min(first.z(), second.z()), Math.max(first.z(), second.z()),
                existing.region().minY(), existing.region().maxY()));
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
        SFGameSavedData.get(context.getSource().getServer()).setDirty(); MatchManager.get().arenaSelectionChanged();
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
            send(context, "pointStrategy=" + map.domination().strategy().name().toLowerCase()
                    + ", capturePoints=" + map.domination().points().size());
        }
        return data.mapConfigured(map) ? 1 : 0;
    }

    private static int mapCreate(CommandContext<CommandSourceStack> context) {
        if (!MatchManager.get().canChangeArena()) return failure(context, "Cannot create a map during a match");
        String mapId = StringArgumentType.getString(context, "map");
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (!data.createMap(mapId)) return failure(context, "Invalid or duplicate map id: " + mapId);
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
        if (!data.removeMap(mapId)) return failure(context, "Map does not exist or is the last map for this mode");
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
        if (assigned + newPlayers > manager.rules().maxPlayers()) {
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

    private static int rulesSet(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        int value = IntegerArgumentType.getInteger(context, "value");
        try { MatchManager.get().setRule(key, value); }
        catch (IllegalArgumentException | IllegalStateException exception) { return failure(context, exception.getMessage()); }
        return success(context, key + "=" + ruleValue(MatchManager.get().rules(), key));
    }

    private static int rulesSetBoolean(CommandContext<CommandSourceStack> context, String key) {
        boolean value = BoolArgumentType.getBool(context, "value");
        try { MatchManager.get().setRule(key, value); }
        catch (IllegalArgumentException | IllegalStateException exception) { return failure(context, exception.getMessage()); }
        return success(context, key + "=" + value);
    }

    private static int rulesSetDouble(CommandContext<CommandSourceStack> context, String key) {
        double value = DoubleArgumentType.getDouble(context, "value");
        try { MatchManager.get().setRule(key, value); }
        catch (IllegalArgumentException | IllegalStateException exception) { return failure(context, exception.getMessage()); }
        return success(context, key + "=" + ruleValue(MatchManager.get().rules(), key));
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
        List<String> errors = new ArrayList<>(MatchManager.get().classes().reload());
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        boolean captains = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode()) && data.activeMap() != null
                && data.activeMap().breakthrough().variant() == BreakthroughVariant.CAPTAIN;
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
        boolean captains = GameModeRegistry.BREAKTHROUGH.equals(data.selectedMode()) && data.activeMap() != null
                && data.activeMap().breakthrough().variant() == BreakthroughVariant.CAPTAIN;
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
            case "attackerTickets" -> Integer.toString(rules.attackerTickets());
            case "sectorTransitionSeconds" -> Integer.toString(rules.sectorTransitionSeconds());
            case "captainVoteSeconds" -> Integer.toString(rules.captainVoteSeconds());
            case "captainReplacementVoteSeconds" -> Integer.toString(rules.captainReplacementVoteSeconds());
            case "attackerCaptainGlowing" -> Boolean.toString(rules.attackerCaptainGlowing());
            case "breakthroughBlockBreaking" -> Boolean.toString(rules.breakthroughBlockBreaking());
            case "attackerCaptainCaptureWeight" -> Double.toString(rules.attackerCaptainCaptureWeight());
            case "defenderCaptureWeight" -> Double.toString(rules.defenderCaptureWeight());
            case "ctfFlagReturnSeconds" -> Integer.toString(rules.ctfFlagReturnSeconds());
            case "ctfHomeCaptureTimeSeconds" -> Integer.toString(rules.ctfHomeCaptureTimeSeconds());
            default -> throw new IllegalArgumentException("Unknown rule " + key);
        };
    }

    private static boolean isDominationOnlyRule(String key) {
        return key.equals("scoreIntervalSeconds") || key.equals("scorePerPoint") || key.equals("syncHoldSeconds");
    }

    private static boolean isCaptureOnlyRule(String key) {
        return key.equals("captureTimeSeconds") || key.equals("captureUsePlayerDifference")
                || key.equals("captureDifferenceCoefficient") || key.equals("captureMaxMultiplier");
    }

    private static boolean isBreakthroughOnlyRule(String key) {
        return key.equals("sectorTransitionSeconds") || key.equals("captainVoteSeconds")
                || key.equals("captainReplacementVoteSeconds") || key.equals("attackerCaptainGlowing")
                || key.equals("breakthroughBlockBreaking")
                || key.equals("attackerCaptainCaptureWeight")
                || key.equals("defenderCaptureWeight");
    }

    private static boolean isCtfOnlyRule(String key) {
        return key.equals("ctfFlagReturnSeconds") || key.equals("ctfHomeCaptureTimeSeconds");
    }


    private static String rulesText(MatchRules r) {
        String common = "maxPlayers=" + r.maxPlayers() + ", scoreLimit=" + r.scoreLimit() + ", timeLimitSeconds=" + r.timeLimitSeconds()
                + ", startCountdownSeconds=" + r.startCountdownSeconds() + ", respawnSeconds=" + r.respawnSeconds()
                + ", respawnProtectionSeconds=" + r.respawnProtectionSeconds() + ", resultSeconds=" + r.resultSeconds();
        if (GameModeRegistry.DOMINATION.equals(r.modeId())) return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
                + ", captureUsePlayerDifference=" + r.captureUsePlayerDifference()
                + ", captureDifferenceCoefficient=" + r.captureDifferenceCoefficient()
                + ", captureMaxMultiplier=" + r.captureMaxMultiplier()
                + ", scoreIntervalSeconds=" + r.scoreIntervalSeconds() + ", scorePerPoint=" + r.scorePerPoint()
                + ", syncHoldSeconds=" + r.syncHoldSeconds();
        if (GameModeRegistry.BREAKTHROUGH.equals(r.modeId())) return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
                + ", captureUsePlayerDifference=" + r.captureUsePlayerDifference()
                + ", captureDifferenceCoefficient=" + r.captureDifferenceCoefficient()
                + ", captureMaxMultiplier=" + r.captureMaxMultiplier() + ", attackerTickets=" + r.attackerTickets()
                + ", sectorTransitionSeconds=" + r.sectorTransitionSeconds() + ", captainVoteSeconds=" + r.captainVoteSeconds()
                + ", captainReplacementVoteSeconds=" + r.captainReplacementVoteSeconds()
                + ", attackerCaptainGlowing=" + r.attackerCaptainGlowing()
                + ", breakthroughBlockBreaking=" + r.breakthroughBlockBreaking()
                + ", attackerCaptainCaptureWeight=" + r.attackerCaptainCaptureWeight()
                + ", defenderCaptureWeight=" + r.defenderCaptureWeight();
        if (GameModeRegistry.CAPTURE_THE_FLAG.equals(r.modeId())) return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
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

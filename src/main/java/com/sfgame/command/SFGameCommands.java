package com.sfgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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
    private static final Map<UUID, ArenaPosition> POINT_POS_1 = new HashMap<>();
    private static final Map<UUID, ArenaPosition> POINT_POS_2 = new HashMap<>();

    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);
    private static final SuggestionProvider<CommandSourceStack> CLASS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(MatchManager.get().classes().all().stream().map(ClassDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> RULE_SUGGESTIONS = (context, builder) -> {
        boolean domination = GameModeRegistry.DOMINATION.equals(SFGameSavedData.get(context.getSource().getServer()).selectedMode());
        java.util.stream.Stream<String> keys = java.util.Arrays.stream(COMMON_RULE_KEYS);
        if (domination) keys = java.util.stream.Stream.concat(keys, java.util.Arrays.stream(DOMINATION_RULE_KEYS));
        return SharedSuggestionProvider.suggest(keys, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(GameModeRegistry.all().stream().map(GameModeDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).maps().stream()
                    .map(ArenaMap::id), builder);
    private static final SuggestionProvider<CommandSourceStack> POINT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).activeMap() == null
                    ? java.util.stream.Stream.empty() : SFGameSavedData.get(context.getSource().getServer()).activeMap()
                    .domination().points().stream().map(CapturePointDefinition::id), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sfgame")
                .then(Commands.literal("menu").executes(SFGameCommands::menu))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(SFGameCommands::status))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(SFGameCommands::start))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(SFGameCommands::stop))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(SFGameCommands::reset))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(SFGameCommands::reload))
                .then(Commands.literal("joinnow").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(SFGameCommands::joinNow)))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
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
                                .then(Commands.literal("red").executes(c -> spawnClear(c, TeamSide.RED)))
                                .then(Commands.literal("blue").executes(c -> spawnClear(c, TeamSide.BLUE)))
                                .then(Commands.literal("yellow").executes(c -> spawnClear(c, TeamSide.YELLOW)))
                                .then(Commands.literal("green").executes(c -> spawnClear(c, TeamSide.GREEN)))))
                .then(Commands.literal("point").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("pos1").executes(c -> pointPosition(c, true)))
                        .then(Commands.literal("pos2").executes(c -> pointPosition(c, false)))
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
                        .then(Commands.literal("remove").then(Commands.argument("players", EntityArgument.players())
                                .executes(SFGameCommands::removeTeam))))
                .then(Commands.literal("rules").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(SFGameCommands::rulesList))
                        .then(Commands.literal("reset").executes(SFGameCommands::rulesReset))
                        .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.word())
                                .suggests(RULE_SUGGESTIONS).executes(SFGameCommands::rulesGet)))
                        .then(Commands.literal("set")
                                .then(Commands.literal("captureUsePlayerDifference")
                                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(SFGameCommands::rulesSetBoolean)))
                                .then(Commands.literal("captureDifferenceCoefficient")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0)).executes(SFGameCommands::rulesSetDouble)))
                                .then(Commands.argument("key", StringArgumentType.word()).suggests(RULE_SUGGESTIONS)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(SFGameCommands::rulesSet)))))
                .then(Commands.literal("class").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reload").executes(SFGameCommands::classReload))
                        .then(Commands.literal("validate").executes(SFGameCommands::classValidate))
                        .then(Commands.literal("list").executes(SFGameCommands::classList))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASS_SUGGESTIONS)
                                        .executes(SFGameCommands::classSet))))));
    }

    private static int menu(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SFGameNetwork.openMenu(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MatchManager manager = MatchManager.get();
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        send(context, "Phase=" + manager.phase() + ", scores=" + TeamSide.PLAYABLE.stream()
                .map(side -> side.id() + ":" + manager.score(side)).collect(java.util.stream.Collectors.joining(","))
                + ", mode=" + data.selectedMode() + ", map=" + data.selectedMap()
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
        return classReload(context);
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
        if (!checkPointEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition position = ArenaPosition.from(player);
        (first ? POINT_POS_1 : POINT_POS_2).put(player.getUUID(), position);
        return success(context, "Set capture point pos" + (first ? "1" : "2") + " to " + positionText(position));
    }

    private static int pointAddBox(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!checkPointEdit(context)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        ArenaPosition first = POINT_POS_1.get(player.getUUID()), second = POINT_POS_2.get(player.getUUID());
        if (first == null || second == null) return failure(context, "Set point pos1 and pos2 first");
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
        if (first == null || second == null) return failure(context, "Set point pos1 and pos2 first");
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
                + ", lobby=" + (map.lobby() != null) + ", enabledTeams=" + map.enabledTeams()
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
        long assigned = context.getSource().getServer().getPlayerList().getPlayers().stream()
                .filter(p -> manager.teams().sideOf(p, data) != TeamSide.NONE).count();
        long newPlayers = players.stream().filter(p -> manager.teams().sideOf(p, data) == TeamSide.NONE).count();
        if (assigned + newPlayers > data.rules().maxPlayers()) {
            return failure(context, "Assigning " + players.size() + " players would exceed maxPlayers");
        }
        int changed = 0;
        for (ServerPlayer player : players) {
            TeamSide target = side == TeamSide.NONE
                    ? manager.teams().balancedSide(context.getSource().getServer(), data) : side;
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
        MatchRules rules = SFGameSavedData.get(context.getSource().getServer()).rules();
        send(context, rulesText(rules));
        return 1;
    }

    private static int rulesGet(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        MatchRules rules = SFGameSavedData.get(context.getSource().getServer()).rules();
        String value;
        try { value = ruleValue(rules, key); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
        send(context, key + "=" + value);
        return 1;
    }

    private static int rulesSet(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        int value = IntegerArgumentType.getInteger(context, "value");
        try { MatchManager.get().setRule(key, value); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
        return success(context, key + "=" + ruleValue(SFGameSavedData.get(context.getSource().getServer()).rules(), key));
    }

    private static int rulesSetBoolean(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        try { MatchManager.get().setRule("captureUsePlayerDifference", value); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
        return success(context, "captureUsePlayerDifference=" + value);
    }

    private static int rulesSetDouble(CommandContext<CommandSourceStack> context) {
        double value = DoubleArgumentType.getDouble(context, "value");
        try { MatchManager.get().setRule("captureDifferenceCoefficient", value); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
        return success(context, "captureDifferenceCoefficient="
                + SFGameSavedData.get(context.getSource().getServer()).rules().captureDifferenceCoefficient());
    }

    private static int rulesReset(CommandContext<CommandSourceStack> context) {
        MatchManager.get().resetRules();
        return success(context, "Rules reset to defaults");
    }

    private static int classReload(CommandContext<CommandSourceStack> context) {
        List<String> errors = new ArrayList<>(MatchManager.get().classes().reload());
        errors.addAll(MatchManager.get().loadouts().validate(MatchManager.get().classes()).stream().filter(e -> !errors.contains(e)).toList());
        if (!errors.isEmpty()) {
            errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
            return 0;
        }
        return success(context, "Loaded " + MatchManager.get().classes().all().size() + " classes");
    }

    private static int classValidate(CommandContext<CommandSourceStack> context) {
        List<String> errors = MatchManager.get().loadouts().validate(MatchManager.get().classes());
        if (!errors.isEmpty()) {
            errors.forEach(error -> context.getSource().sendFailure(Component.literal(error)));
            return 0;
        }
        return success(context, "All class and TACZ resources are valid");
    }

    private static int classList(CommandContext<CommandSourceStack> context) {
        MatchManager.get().classes().all().forEach(c -> send(context, c.id() + " - " + c.displayName() + " (" + c.gunId() + ")"));
        return MatchManager.get().classes().all().size();
    }

    private static int classSet(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String classId = StringArgumentType.getString(context, "class");
        return MatchManager.get().selectClass(player, classId) ? success(context, "Selected " + classId + " for " + player.getGameProfile().getName())
                : failure(context, "Unknown class " + classId);
    }

    private static String ruleValue(MatchRules rules, String key) {
        if (!GameModeRegistry.DOMINATION.equals(rules.modeId()) && isDominationRule(key)) {
            throw new IllegalArgumentException(key + " is only available in domination mode");
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
            default -> throw new IllegalArgumentException("Unknown rule " + key);
        };
    }

    private static boolean isDominationRule(String key) {
        return key.startsWith("capture") || key.equals("scoreIntervalSeconds")
                || key.equals("scorePerPoint") || key.equals("syncHoldSeconds");
    }

    private static String rulesText(MatchRules r) {
        String common = "maxPlayers=" + r.maxPlayers() + ", scoreLimit=" + r.scoreLimit() + ", timeLimitSeconds=" + r.timeLimitSeconds()
                + ", startCountdownSeconds=" + r.startCountdownSeconds() + ", respawnSeconds=" + r.respawnSeconds()
                + ", respawnProtectionSeconds=" + r.respawnProtectionSeconds() + ", resultSeconds=" + r.resultSeconds();
        if (!GameModeRegistry.DOMINATION.equals(r.modeId())) return common;
        return common + ", captureTimeSeconds=" + r.captureTimeSeconds()
                + ", captureUsePlayerDifference=" + r.captureUsePlayerDifference()
                + ", captureDifferenceCoefficient=" + r.captureDifferenceCoefficient()
                + ", captureMaxMultiplier=" + r.captureMaxMultiplier()
                + ", scoreIntervalSeconds=" + r.scoreIntervalSeconds() + ", scorePerPoint=" + r.scorePerPoint()
                + ", syncHoldSeconds=" + r.syncHoldSeconds();
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

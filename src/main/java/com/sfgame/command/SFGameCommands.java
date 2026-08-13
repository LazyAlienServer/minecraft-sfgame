package com.sfgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sfgame.classsystem.ClassDefinition;
import com.sfgame.data.ArenaPosition;
import com.sfgame.data.ArenaMap;
import com.sfgame.data.MatchRules;
import com.sfgame.data.SFGameSavedData;
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

public final class SFGameCommands {
    private static final String[] RULE_KEYS = {"maxPlayers", "scoreLimit", "timeLimitSeconds",
            "startCountdownSeconds", "respawnSeconds", "respawnProtectionSeconds", "resultSeconds"};

    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(context.getSource().getServer().getScoreboard().getTeamNames(), builder);
    private static final SuggestionProvider<CommandSourceStack> CLASS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(MatchManager.get().classes().all().stream().map(ClassDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> RULE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(RULE_KEYS, builder);
    private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(GameModeRegistry.all().stream().map(GameModeDefinition::id), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SFGameSavedData.get(context.getSource().getServer()).maps().stream()
                    .map(ArenaMap::id), builder);

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
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("lobby").executes(c -> setPosition(c, "lobby")))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("red").executes(c -> setPosition(c, "red")))
                                .then(Commands.literal("blue").executes(c -> setPosition(c, "blue")))))
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
                                        .suggests(TEAM_SUGGESTIONS).executes(c -> bindTeam(c, TeamSide.BLUE)))))
                        .then(Commands.literal("set").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.literal("red").executes(c -> setTeam(c, TeamSide.RED)))
                                .then(Commands.literal("blue").executes(c -> setTeam(c, TeamSide.BLUE)))
                                .then(Commands.literal("random").executes(c -> setTeam(c, TeamSide.NONE)))))
                        .then(Commands.literal("remove").then(Commands.argument("players", EntityArgument.players())
                                .executes(SFGameCommands::removeTeam))))
                .then(Commands.literal("rules").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(SFGameCommands::rulesList))
                        .then(Commands.literal("reset").executes(SFGameCommands::rulesReset))
                        .then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.word())
                                .suggests(RULE_SUGGESTIONS).executes(SFGameCommands::rulesGet)))
                        .then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.word())
                                .suggests(RULE_SUGGESTIONS).then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(SFGameCommands::rulesSet)))))
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
        send(context, "Phase=" + manager.phase() + ", score=" + manager.redScore() + ":" + manager.blueScore()
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
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        ArenaPosition position = ArenaPosition.from(context.getSource().getPlayerOrException());
        switch (type) {
            case "lobby" -> data.lobby(position);
            case "red" -> data.redSpawn(position);
            case "blue" -> data.blueSpawn(position);
        }
        MatchManager.get().arenaSelectionChanged();
        return success(context, "Set " + type + " position for " + data.selectedMode() + "/" + data.selectedMap());
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
                + map.id() + (map.configured() ? " [configured]" : " [incomplete]")));
        return data.maps().size();
    }

    private static int mapStatus(CommandContext<CommandSourceStack> context) {
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        ArenaMap map = data.activeMap();
        if (map == null) return failure(context, "No active map");
        send(context, "Mode=" + data.selectedMode() + ", map=" + map.id() + ", configured=" + map.configured()
                + ", lobby=" + (map.lobby() != null) + ", redSpawn=" + (map.redSpawn() != null)
                + ", blueSpawn=" + (map.blueSpawn() != null));
        return map.configured() ? 1 : 0;
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
        send(context, "RED -> " + data.redTeam() + ", BLUE -> " + data.blueTeam());
        return 1;
    }

    private static int bindTeam(CommandContext<CommandSourceStack> context, TeamSide side) {
        String name = StringArgumentType.getString(context, "team");
        PlayerTeam team = context.getSource().getServer().getScoreboard().getPlayerTeam(name);
        if (team == null) return failure(context, "Vanilla team does not exist: " + name);
        SFGameSavedData data = SFGameSavedData.get(context.getSource().getServer());
        if (side == TeamSide.RED && name.equals(data.blueTeam()) || side == TeamSide.BLUE && name.equals(data.redTeam())) {
            return failure(context, "Red and blue cannot bind the same team");
        }
        if (side == TeamSide.RED) data.redTeam(name); else data.blueTeam(name);
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
        int value;
        try { value = ruleValue(rules, key); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
        send(context, key + "=" + value);
        return value;
    }

    private static int rulesSet(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        int value = IntegerArgumentType.getInteger(context, "value");
        try { MatchManager.get().setRule(key, value); }
        catch (IllegalArgumentException exception) { return failure(context, exception.getMessage()); }
        return success(context, key + "=" + ruleValue(SFGameSavedData.get(context.getSource().getServer()).rules(), key));
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

    private static int ruleValue(MatchRules rules, String key) {
        return switch (key) {
            case "maxPlayers" -> rules.maxPlayers();
            case "scoreLimit" -> rules.scoreLimit();
            case "timeLimitSeconds" -> rules.timeLimitSeconds();
            case "startCountdownSeconds" -> rules.startCountdownSeconds();
            case "respawnSeconds" -> rules.respawnSeconds();
            case "respawnProtectionSeconds" -> rules.respawnProtectionSeconds();
            case "resultSeconds" -> rules.resultSeconds();
            default -> throw new IllegalArgumentException("Unknown rule " + key);
        };
    }

    private static String rulesText(MatchRules r) {
        return "maxPlayers=" + r.maxPlayers() + ", scoreLimit=" + r.scoreLimit() + ", timeLimitSeconds=" + r.timeLimitSeconds()
                + ", startCountdownSeconds=" + r.startCountdownSeconds() + ", respawnSeconds=" + r.respawnSeconds()
                + ", respawnProtectionSeconds=" + r.respawnProtectionSeconds() + ", resultSeconds=" + r.resultSeconds();
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

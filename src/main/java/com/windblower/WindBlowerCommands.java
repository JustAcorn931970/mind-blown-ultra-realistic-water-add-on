package com.windblower;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WindBlowerCommands {
    private static final Set<UUID> USE_MODE = ConcurrentHashMap.newKeySet();
    private WindBlowerCommands() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(handler.getPlayer(), new WindModePayload(USE_MODE.contains(handler.getPlayer().getUUID()))));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> USE_MODE.remove(handler.getPlayer().getUUID()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("windblow")
                        .executes(context -> showSettings(context.getSource()))
                        .then(Commands.literal("settings").executes(context -> showSettings(context.getSource())))
                        .then(Commands.literal("use").executes(context -> setUseMode(context.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("reuse").executes(context -> setUseMode(context.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("reUse").executes(context -> setUseMode(context.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("pulse").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!USE_MODE.contains(player.getUUID())) {
                                context.getSource().sendFailure(Component.literal("Run /windblow use first to arm the Z key."));
                                return 0;
                            }
                            WindBlowerPhysics.gust(player);
                            return 1;
                        }))
                        .then(Commands.literal("set")
                                .then(Commands.argument("setting", StringArgumentType.word())
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(context -> setSetting(context.getSource(),
                                                        StringArgumentType.getString(context, "setting"),
                                                        DoubleArgumentType.getDouble(context, "value"), false))
                                                .then(Commands.literal("silent").executes(context -> setSetting(context.getSource(),
                                                        StringArgumentType.getString(context, "setting"),
                                                        DoubleArgumentType.getDouble(context, "value"), true))))))
                        .then(Commands.literal("reset")
                                .executes(context -> resetSettings(context.getSource(), false))
                                .then(Commands.literal("silent").executes(context -> resetSettings(context.getSource(), true))))));
    }

    private static int setSetting(net.minecraft.commands.CommandSourceStack source, String key, double value, boolean silent) {
        if (!WindBlowerConfig.set(key, value)) {
            source.sendFailure(Component.literal("Invalid setting/value. Run /windblow settings for supported values."));
            return 0;
        }
        if (!silent) source.sendSuccess(() -> Component.literal("Wind Blower · " + key + " = " + WindBlowerConfig.valueFor(key)), false);
        return 1;
    }

    private static int resetSettings(net.minecraft.commands.CommandSourceStack source, boolean silent) {
        WindBlowerConfig.reset();
        if (!silent) source.sendSuccess(() -> Component.literal("Wind Blower settings reset."), false);
        return 1;
    }

    private static int setUseMode(ServerPlayer player, boolean enabled) {
        if (enabled) USE_MODE.add(player.getUUID()); else USE_MODE.remove(player.getUUID());
        ServerPlayNetworking.send(player, new WindModePayload(enabled));
        player.sendSystemMessage(Component.literal(enabled
                ? "Wind Blower armed · press Z to release a gust · /windblow reUse to disarm"
                : "Wind Blower disarmed · Z gusts disabled"));
        return 1;
    }

    private static int showSettings(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§bWIND BLOWER §8/§f COMMANDS"), false);
        source.sendSuccess(() -> Component.literal("/windblow use · arm Z gusts"), false);
        source.sendSuccess(() -> Component.literal("/windblow reUse · disarm Z gusts"), false);
        source.sendSuccess(() -> Component.literal("/windblow set <range|strength|cone|lift|maxspeed|particles|waterforce|waterpulse|blocklaunch> <value>"), false);
        source.sendSuccess(() -> Component.literal("/windblow reset · reset settings · press O for the UI"), false);
        source.sendSuccess(() -> Component.literal("Current values: range=" + WindBlowerConfig.range + ", strength=" + WindBlowerConfig.strength
                + ", cone=" + WindBlowerConfig.cone + ", maxspeed=" + WindBlowerConfig.maxSpeed), false);
        source.sendSuccess(() -> Component.literal("Block launch: " + (WindBlowerConfig.blockLaunchEnabled ? "ON" : "OFF")
                + " · realistic water needs the mod's TNT reaction enabled in /water gui."), false);
        return 1;
    }

    public static boolean isEnabled(ServerPlayer player) { return USE_MODE.contains(player.getUUID()); }
}

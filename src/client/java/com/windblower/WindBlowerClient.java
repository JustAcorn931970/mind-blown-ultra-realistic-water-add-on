package com.windblower;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class WindBlowerClient implements ClientModInitializer {
    private static KeyMapping openSettings;
    private static KeyMapping gust;
    private static volatile boolean useModeEnabled;

    @Override public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(WindBlowerMod.id("controls"));
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.windblower.open_settings", InputConstants.KEY_O, category));
        gust = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.windblower.gust", InputConstants.KEY_Z, category));

        ClientPlayNetworking.registerGlobalReceiver(WindModePayload.TYPE,
                (payload, context) -> context.client().execute(() -> useModeEnabled = payload.enabled()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> useModeEnabled = false);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (gust.consumeClick()) {
                if (useModeEnabled && client.player != null && client.level != null && client.gui.screen() == null) {
                    var player = client.player;
                    var look = player.getLookAngle().normalize();
                    WindBlowerWaterCompat.pulse(client.level, player, player.getEyePosition(), look, client.level.getGameTime());
                    player.connection.sendCommand("windblow pulse");
                }
            }
            while (openSettings.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new WindBlowerSettingsScreen(null));
                }
            }
        });
    }

    public static boolean isUseModeEnabled() { return useModeEnabled; }
    public static void openSettings() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.gui.setScreen(new WindBlowerSettingsScreen(client.gui.screen()));
    }
}

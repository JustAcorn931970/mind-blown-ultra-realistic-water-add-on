package com.windblower;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WindBlowerMod implements ModInitializer {
    public static final String MOD_ID = "windblower";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override public void onInitialize() {
        WindBlowerConfig.load();
        PayloadTypeRegistry.clientboundPlay().register(WindModePayload.TYPE, WindModePayload.CODEC);
        WindBlowerCommands.register();
        LOGGER.info("Wind Blower 1.4.1 loaded: /windblow use arms Z gusts; /windblow reUse disarms them.");
    }
}

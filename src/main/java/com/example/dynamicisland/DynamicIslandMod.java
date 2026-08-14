package com.example.dynamicisland;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point. Dynamic Island is a client-only UI mod, so this class
 * exists only to register the mod id / logger. All real work happens in
 * {@link com.example.dynamicisland.client.DynamicIslandClient}.
 */
public class DynamicIslandMod implements ModInitializer {
    public static final String MOD_ID = "dynamicisland";
    public static final Logger LOGGER = LoggerFactory.getLogger("Dynamic Island");

    @Override
    public void onInitialize() {
        LOGGER.info("Dynamic Island initialised (client overlay will activate on join).");
    }
}

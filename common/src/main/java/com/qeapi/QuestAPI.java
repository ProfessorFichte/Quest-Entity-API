package com.qeapi;

import com.qeapi.config.QuestAPIConfig;
import com.qeapi.registry.QEDataComponents;
import com.qeapi.registry.QERegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuestAPI {
    public static final String MOD_ID = "quest_api";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Initializing Quest API");

        QuestAPIConfig.register();

        // Register built-in task, requirement, and reward types
        QERegistries.init();

        // Register data components
        QEDataComponents.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

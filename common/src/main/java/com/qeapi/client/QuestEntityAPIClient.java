package com.qeapi.client;

import com.qeapi.QuestEntityAPI;

// Client-side initialization for Quest Entity API.
public final class QuestEntityAPIClient {

    private QuestEntityAPIClient() {}

    public static void init() {
        QuestEntityAPI.LOGGER.info("Initializing Quest Entity API client");

        registerPacketHandlers();
        registerRenderers();
    }

    private static void registerPacketHandlers() {
        // none yet
    }

    private static void registerRenderers() {
        // none yet
    }
}

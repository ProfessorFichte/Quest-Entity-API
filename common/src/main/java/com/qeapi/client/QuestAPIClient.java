package com.qeapi.client;

import com.qeapi.QuestAPI;

public final class QuestAPIClient {

    private QuestAPIClient() {}

    public static void init() {
        QuestAPI.LOGGER.info("Initializing Quest API client");

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

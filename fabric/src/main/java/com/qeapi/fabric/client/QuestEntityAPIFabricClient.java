package com.qeapi.fabric.client;

import com.qeapi.QuestEntityAPI;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.client.QuestEntityAPIClient;
import com.qeapi.client.QuestKeybinds;
import com.qeapi.client.render.QuestMarkerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

public final class QuestEntityAPIFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QuestEntityAPIClient.init();
        init();
    }

    public static void init() {
        QuestEntityAPI.LOGGER.info("Initializing Fabric client for Quest Entity API");

        registerRenderLayers();
        registerPacketHandlers();
        registerClientEvents();
        registerKeybinds();
    }

    private static void registerKeybinds() {
        KeyBindingHelper.registerKeyBinding(QuestKeybinds.OPEN_ACTIVE_QUESTS);
        ClientTickEvents.END_CLIENT_TICK.register(client -> QuestKeybinds.tick());
    }

    private static void registerRenderLayers() {
        // quest markers are rendered via LivingEntityRendererMixin, not a feature-renderer layer -
        // this registration exists only so the callback fires and the entity type is initialized
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
        });
    }

    private static void registerPacketHandlers() {
        com.qeapi.fabric.network.FabricNetworking.registerClient();
    }

    private static void registerClientEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            QuestEntityAPI.LOGGER.debug("Clearing client quest cache on disconnect");
            ClientQuestCache.clear();
        });
    }
}

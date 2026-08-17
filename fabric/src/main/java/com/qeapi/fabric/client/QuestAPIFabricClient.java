package com.qeapi.fabric.client;

import com.qeapi.QuestAPI;
import com.qeapi.client.ClientHudMessageState;
import com.qeapi.client.ClientQuestCache;
import com.qeapi.client.QuestAPIClient;
import com.qeapi.client.QuestKeybinds;
import com.qeapi.client.render.QuestHudMessageRenderer;
import com.qeapi.client.render.QuestMarkerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

public final class QuestAPIFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QuestAPIClient.init();
        init();
    }

    public static void init() {
        QuestAPI.LOGGER.info("Initializing Fabric client for Quest API");

        registerRenderLayers();
        registerPacketHandlers();
        registerClientEvents();
        registerKeybinds();
        registerHudMessageOverlay();
    }

    private static void registerKeybinds() {
        KeyBindingHelper.registerKeyBinding(QuestKeybinds.OPEN_ACTIVE_QUESTS);
        ClientTickEvents.END_CLIENT_TICK.register(client -> QuestKeybinds.tick());
    }

    private static void registerHudMessageOverlay() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHudMessageState.tick());
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> QuestHudMessageRenderer.render(graphics));
    }

    private static void registerRenderLayers() {
        // markers render via LivingEntityRendererMixin; this registration only exists so the callback fires and the entity type initializes
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
        });
    }

    private static void registerPacketHandlers() {
        com.qeapi.fabric.network.FabricNetworking.registerClient();
    }

    private static void registerClientEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            QuestAPI.LOGGER.debug("Clearing client quest cache on disconnect");
            ClientQuestCache.clear();
            ClientHudMessageState.clear();
        });
    }
}

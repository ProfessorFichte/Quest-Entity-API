package com.qeapi.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qeapi.network.ClientPacketSender;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// The Active Quests screen's keybind. The KeyMapping itself is shared here; actual registration
// with the key-binding settings screen is platform-specific (Fabric's KeyBindingHelper vs
// NeoForge's RegisterKeyMappingsEvent), done from each platform's client init.
public final class QuestKeybinds {

    public static final String CATEGORY = "key.categories.qe_api";

    public static final KeyMapping OPEN_ACTIVE_QUESTS = new KeyMapping(
            "key.qe_api.open_active_quests",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY
    );

    private QuestKeybinds() {}

    // Called once per client tick from platform code.
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_ACTIVE_QUESTS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                ClientPacketSender.sendRequestActiveQuests();
            }
        }
    }
}

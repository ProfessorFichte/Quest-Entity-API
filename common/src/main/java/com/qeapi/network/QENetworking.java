package com.qeapi.network;

import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;

// Central networking configuration for Quest Entity API. Platform-specific implementations
// handle the actual packet registration.
public final class QENetworking {

    // Packet IDs
    public static final ResourceLocation OPEN_QUEST_MENU = QuestEntityAPI.id("open_quest_menu");
    public static final ResourceLocation ACCEPT_QUEST = QuestEntityAPI.id("accept_quest");
    public static final ResourceLocation DISMISS_QUEST = QuestEntityAPI.id("dismiss_quest");
    public static final ResourceLocation CLAIM_REWARDS = QuestEntityAPI.id("claim_rewards");
    public static final ResourceLocation QUEST_PROGRESS = QuestEntityAPI.id("quest_progress");
    public static final ResourceLocation SYNC_QUEST_DATA = QuestEntityAPI.id("sync_quest_data");
    public static final ResourceLocation REQUEST_QUEST_MENU = QuestEntityAPI.id("request_quest_menu");
    public static final ResourceLocation REQUEST_MERCHANT_MENU = QuestEntityAPI.id("request_merchant_menu");

    private QENetworking() {}

    public static final int PROTOCOL_VERSION = 1;
}

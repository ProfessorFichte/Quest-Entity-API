package com.qeapi.network;

import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;

// Central networking configuration for Quest Entity API. Platform-specific implementations
// handle the actual packet registration.
public final class QENetworking {

    public static final ResourceLocation OPEN_QUEST_MENU = QuestEntityAPI.id("open_quest_menu");
    public static final ResourceLocation ACCEPT_QUEST = QuestEntityAPI.id("accept_quest");
    public static final ResourceLocation DISMISS_QUEST = QuestEntityAPI.id("dismiss_quest");
    public static final ResourceLocation CLAIM_REWARDS = QuestEntityAPI.id("claim_rewards");
    public static final ResourceLocation QUEST_PROGRESS = QuestEntityAPI.id("quest_progress");
    public static final ResourceLocation SYNC_QUEST_DATA = QuestEntityAPI.id("sync_quest_data");
    public static final ResourceLocation REQUEST_QUEST_MENU = QuestEntityAPI.id("request_quest_menu");
    public static final ResourceLocation REQUEST_MERCHANT_MENU = QuestEntityAPI.id("request_merchant_menu");
    public static final ResourceLocation REQUEST_ACTIVE_QUESTS = QuestEntityAPI.id("request_active_quests");
    public static final ResourceLocation ACTIVE_QUESTS = QuestEntityAPI.id("active_quests");
    public static final ResourceLocation CHOOSE_QUEST_LINE = QuestEntityAPI.id("choose_quest_line");
    public static final ResourceLocation CLAIM_QUEST_LINE_ROOT = QuestEntityAPI.id("claim_quest_line_root");
    public static final ResourceLocation CANCEL_QUEST_LINE = QuestEntityAPI.id("cancel_quest_line");
    public static final ResourceLocation DISMISS_QUEST_LINE_ROOT = QuestEntityAPI.id("dismiss_quest_line_root");

    private QENetworking() {}

    public static final int PROTOCOL_VERSION = 1;
}

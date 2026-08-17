package com.qeapi.network;

import com.qeapi.QuestAPI;
import net.minecraft.resources.ResourceLocation;

// Just the channel IDs; platform-specific code handles actual packet registration.
public final class QENetworking {

    public static final ResourceLocation OPEN_QUEST_MENU = QuestAPI.id("open_quest_menu");
    public static final ResourceLocation ACCEPT_QUEST = QuestAPI.id("accept_quest");
    public static final ResourceLocation DISMISS_QUEST = QuestAPI.id("dismiss_quest");
    public static final ResourceLocation CLAIM_REWARDS = QuestAPI.id("claim_rewards");
    public static final ResourceLocation QUEST_PROGRESS = QuestAPI.id("quest_progress");
    public static final ResourceLocation SYNC_QUEST_DATA = QuestAPI.id("sync_quest_data");
    public static final ResourceLocation REQUEST_QUEST_MENU = QuestAPI.id("request_quest_menu");
    public static final ResourceLocation REQUEST_MERCHANT_MENU = QuestAPI.id("request_merchant_menu");
    public static final ResourceLocation REQUEST_ACTIVE_QUESTS = QuestAPI.id("request_active_quests");
    public static final ResourceLocation ACTIVE_QUESTS = QuestAPI.id("active_quests");
    public static final ResourceLocation CHOOSE_QUEST_LINE = QuestAPI.id("choose_quest_line");
    public static final ResourceLocation CLAIM_QUEST_LINE_ROOT = QuestAPI.id("claim_quest_line_root");
    public static final ResourceLocation CANCEL_QUEST_LINE = QuestAPI.id("cancel_quest_line");
    public static final ResourceLocation DISMISS_QUEST_LINE_ROOT = QuestAPI.id("dismiss_quest_line_root");

    private QENetworking() {}

    public static final int PROTOCOL_VERSION = 1;
}

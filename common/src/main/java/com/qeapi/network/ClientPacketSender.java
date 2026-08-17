package com.qeapi.network;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Platform-specific senders register themselves via setInstance().
public final class ClientPacketSender {

    private static PacketSender instance;

    private ClientPacketSender() {}

    public static void setInstance(PacketSender sender) {
        instance = sender;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void sendAcceptQuest(int entityId, ResourceLocation questId) {
        if (instance != null) {
            instance.sendAcceptQuest(entityId, questId);
        }
    }

    public static void sendDismissQuest(int entityId) {
        if (instance != null) {
            instance.sendDismissQuest(entityId);
        }
    }

    // poolChoices: chosen option indices per reward-choice-pool.
    // rewardTargetSlots: target slot per reward, or -1.
    // bringItemSlots: slots chosen per BringItemTask, empty to auto-consume.
    public static void sendClaimRewards(int entityId, List<List<Integer>> poolChoices,
                                         List<Integer> rewardTargetSlots, List<List<Integer>> bringItemSlots) {
        if (instance != null) {
            instance.sendClaimRewards(entityId, poolChoices, rewardTargetSlots, bringItemSlots);
        }
    }

    // used when clicking the Q button on the merchant screen
    public static void sendRequestQuestMenu(int entityId) {
        if (instance != null) {
            instance.sendRequestQuestMenu(entityId);
        }
    }

    // used when clicking the back button on the quest screen
    public static void sendRequestMerchantMenu(int entityId) {
        if (instance != null) {
            instance.sendRequestMerchantMenu(entityId);
        }
    }

    // used when opening the Active Quests screen (keybind)
    public static void sendRequestActiveQuests() {
        if (instance != null) {
            instance.sendRequestActiveQuests();
        }
    }

    // used by QuestScreen's quest_line_choice picker row
    public static void sendChooseQuestLine(int entityId, ResourceLocation rootQuestId, String lineId) {
        if (instance != null) {
            instance.sendChooseQuestLine(entityId, rootQuestId, lineId);
        }
    }

    // used by QuestScreen's Claim button when the selected quest is a resolved quest_line_choice root
    public static void sendClaimQuestLineRoot(int entityId, ResourceLocation rootQuestId) {
        if (instance != null) {
            instance.sendClaimQuestLineRoot(entityId, rootQuestId);
        }
    }

    // used by QuestScreen's confirm-dismiss dialog when it was opened from clicking the active
    // line's own bordered icon, rather than a normal quest's checkbox
    public static void sendCancelQuestLine(int entityId, ResourceLocation rootQuestId, String lineId) {
        if (instance != null) {
            instance.sendCancelQuestLine(entityId, rootQuestId, lineId);
        }
    }

    // used by QuestScreen's confirm-dismiss dialog when it was opened from clicking an
    // accepted-not-yet-claimed root's own checkbox, rather than a line's bordered icon
    public static void sendDismissQuestLineRoot(int entityId, ResourceLocation rootQuestId) {
        if (instance != null) {
            instance.sendDismissQuestLineRoot(entityId, rootQuestId);
        }
    }

    public interface PacketSender {
        void sendAcceptQuest(int entityId, ResourceLocation questId);
        void sendDismissQuest(int entityId);
        void sendClaimRewards(int entityId, List<List<Integer>> poolChoices,
                              List<Integer> rewardTargetSlots, List<List<Integer>> bringItemSlots);
        void sendRequestQuestMenu(int entityId);
        void sendRequestMerchantMenu(int entityId);
        void sendRequestActiveQuests();
        void sendChooseQuestLine(int entityId, ResourceLocation rootQuestId, String lineId);
        void sendClaimQuestLineRoot(int entityId, ResourceLocation rootQuestId);
        void sendCancelQuestLine(int entityId, ResourceLocation rootQuestId, String lineId);
        void sendDismissQuestLineRoot(int entityId, ResourceLocation rootQuestId);
    }
}

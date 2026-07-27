package com.qeapi.network;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Platform-agnostic interface for sending packets from client to server. Platform-specific
// implementations register their sender via setInstance().
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

    // poolChoices: per reward-choice-pool (in quest-defined order), the chosen option indices -
    // empty lists for quests with no reward choice pools. rewardTargetSlots: per reward (in
    // quest-defined order), the inventory slot picked as that reward's target item, or -1.
    // bringItemSlots: per task (in quest-defined order), the inventory slots explicitly chosen to
    // satisfy a BringItemTask, or empty to fall back to automatic consumption.
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

    public interface PacketSender {
        void sendAcceptQuest(int entityId, ResourceLocation questId);
        void sendDismissQuest(int entityId);
        void sendClaimRewards(int entityId, List<List<Integer>> poolChoices,
                              List<Integer> rewardTargetSlots, List<List<Integer>> bringItemSlots);
        void sendRequestQuestMenu(int entityId);
        void sendRequestMerchantMenu(int entityId);
    }
}

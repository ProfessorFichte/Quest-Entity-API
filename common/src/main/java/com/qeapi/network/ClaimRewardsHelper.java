package com.qeapi.network;

import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestProgress;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// Shared by FabricNetworking/NeoForgeNetworking's handleClaimRewards, so the slot-aware
// disambiguation (see ClaimRewardsPacket.bringItemSlots) only needs implementing once.
public final class ClaimRewardsHelper {

    private ClaimRewardsHelper() {}

    // Prefers the player's GUI-picked slots when still valid, else falls back to a greedy scan.
    // Tasks only satisfied as an unused taskChoiceGroup alternative (see Quest.isTaskConsumable) are skipped,
    // so an "iron or gold" quest doesn't consume both just because both are held.
    public static void consumeBringItemTasks(ServerPlayer player, Quest quest, QuestProgress progress,
                                              List<List<Integer>> bringItemSlots) {
        List<QuestTask> tasks = quest.tasks();
        for (int i = 0; i < tasks.size(); i++) {
            if (!(tasks.get(i) instanceof BringItemTask bringTask)) continue;
            if (!quest.isTaskConsumable(progress, i)) continue;

            List<Integer> chosenSlots = (bringItemSlots != null && i < bringItemSlots.size())
                    ? bringItemSlots.get(i) : List.of();

            if (!chosenSlots.isEmpty() && isValidSelection(player, bringTask, chosenSlots)) {
                int amountToConsume = bringTask.amount();
                for (int slot : chosenSlots) {
                    if (amountToConsume <= 0) break;
                    ItemStack stack = player.getInventory().items.get(slot);
                    int toRemove = Math.min(stack.getCount(), amountToConsume);
                    stack.shrink(toRemove);
                    amountToConsume -= toRemove;
                }
            } else {
                consumeGreedy(player, bringTask);
            }
        }
    }

    private static boolean isValidSelection(ServerPlayer player, BringItemTask task, List<Integer> slots) {
        int total = 0;
        for (int slot : slots) {
            if (slot < 0 || slot >= player.getInventory().items.size()) return false;
            ItemStack stack = player.getInventory().items.get(slot);
            if (!task.matches(stack)) return false;
            total += stack.getCount();
        }
        return total >= task.amount();
    }

    private static void consumeGreedy(ServerPlayer player, BringItemTask bringTask) {
        int amountToConsume = bringTask.amount();
        for (int slot = 0; slot < player.getInventory().items.size() && amountToConsume > 0; slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (bringTask.matches(stack)) {
                int toRemove = Math.min(stack.getCount(), amountToConsume);
                stack.shrink(toRemove);
                amountToConsume -= toRemove;
            }
        }
    }
}

package com.qeapi.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Implemented (alongside QuestReward, never in place of it - see below) by rewards that apply an
// effect to an item the player already owns, rather than granting a freshly created stack. The
// player picks which inventory item to target in the quest GUI's item-picker overlay; the chosen
// slot travels to the server in ClaimRewardsPacket's rewardTargetSlots and must be re-validated
// with isValidTarget before applyToTarget runs - never trust the client's selection. These
// rewards' own grant(ServerPlayer) is unreachable in normal play (Quest.grantRewards routes them
// through applyToTarget instead) - it only exists to satisfy the QuestReward interface, and logs
// a warning if it's ever actually invoked.
//
// Deliberately does NOT extend QuestReward: QuestReward is a sealed interface whose permits
// clause lists concrete reward records directly, so each targeted reward record implements both
// interfaces separately (`implements QuestReward, TargetItemReward`) rather than through this one
// - a sealed interface's permitted subtypes must be *direct* subtypes.
//
// isValidTarget takes a Level (rather than ServerPlayer) so the exact same check can filter the
// client-side picker's candidate list (using the client's ClientLevel) and re-validate
// server-side before applyToTarget runs - both sides can resolve dynamic registries (like
// Enchantment) through Level.registryAccess().
public interface TargetItemReward {
    boolean isValidTarget(Level level, ItemStack stack);

    void applyToTarget(ServerPlayer player, ItemStack stack);
}

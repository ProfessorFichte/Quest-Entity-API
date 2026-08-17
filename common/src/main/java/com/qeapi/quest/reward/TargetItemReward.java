package com.qeapi.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Applies to an item the player already owns, picked via the GUI's item-picker; the chosen slot must be re-validated with isValidTarget server-side before applyToTarget runs - never trust the client selection.
// Not itself a QuestReward (a sealed interface needs direct subtypes) - each record implements both.
// isValidTarget takes a Level, not ServerPlayer, so the same check works for the client-side picker too.
public interface TargetItemReward {
    boolean isValidTarget(Level level, ItemStack stack);

    void applyToTarget(ServerPlayer player, ItemStack stack);
}

package com.qeapi.compat;

import net.dungeon_difficulty.logic.Difficulty;
import net.dungeon_difficulty.logic.EntityDifficultyScalable;
import net.dungeon_difficulty.logic.ItemScaling;
import net.dungeon_difficulty.logic.PatternMatching;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// Optional integration with Dungeon Difficulty (net.dungeon_difficulty). All direct references to its
// classes live in this file only - callers must check isLoaded() first, so the JVM never needs to
// resolve these classes when Dungeon Difficulty isn't present.
public final class DungeonDifficultyCompat {

    private static final String MOD_ID = "dungeon_difficulty";

    private DungeonDifficultyCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded(MOD_ID);
    }

    // Uses rescale (not scale) - it's idempotent, clearing any existing scaling before reapplying,
    // same as Dungeon Difficulty's own /power_level command.
    public static void applyPowerLevel(ItemStack stack, int level) {
        ItemScaling.rescale(stack, level);
    }

    public static int getPowerLevel(ItemStack stack) {
        return ItemScaling.isScaled(stack) ? ItemScaling.getScaleFactor(stack) : 0;
    }

    // Raises an existing item's power level by amount (relative, unlike applyPowerLevel's
    // absolute set), clamped to cap.
    public static void increasePowerLevel(ItemStack stack, int amount, int cap) {
        int newLevel = Math.min(cap, getPowerLevel(stack) + amount);
        ItemScaling.rescale(stack, newLevel);
    }

    // Every LivingEntity implements EntityDifficultyScalable via Dungeon Difficulty's own mixin,
    // whether or not it's actually been scaled (spawned in a location/structure the pack's
    // pattern-matching config bumped up) - 0 if it hasn't.
    public static int getPowerLevel(LivingEntity entity) {
        EntityDifficultyScalable scalable = (EntityDifficultyScalable) entity;
        return scalable.isAlreadyScaled() ? scalable.getScalingLevel() : 0;
    }

    // Location-only power level, independent of any entity - resolves Dungeon Difficulty's own
    // pattern-matching config (the same config that decides how much to scale a mob spawned there)
    // for the given position. 0 if the position doesn't match any configured pattern.
    public static int getLocationPowerLevel(ServerLevel level, BlockPos pos) {
        PatternMatching.LocationData locationData = PatternMatching.LocationData.create(level, pos);
        Difficulty difficulty = PatternMatching.getDifficulty(locationData, level);
        return difficulty != null ? difficulty.level() : 0;
    }
}

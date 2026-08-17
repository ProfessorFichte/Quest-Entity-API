package com.qeapi.compat;

import net.enchant_limiter.api.ItemComponentTypes;
import net.enchant_limiter.api.LimitComponent;
import net.minecraft.world.item.ItemStack;

// All direct references to Enchant Limiter's classes live in this file only - callers must check
// isLoaded() first, so the JVM never needs to resolve them when the mod isn't present.
public final class EnchantLimiterCompat {

    private static final String MOD_ID = "enchant_limiter";

    private EnchantLimiterCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded(MOD_ID);
    }

    // Enchant Limiter's own default when an item has no explicit limit component yet.
    public static int getEnchantLimit(ItemStack stack) {
        LimitComponent component = stack.get(ItemComponentTypes.ENCHANT_LIMITER);
        return component != null ? component.count() : LimitComponent.DEFAULT_COUNT;
    }

    // Only ever touches this one custom component, so existing enchantments stay untouched.
    public static void increaseEnchantLimit(ItemStack stack, int amount, int cap) {
        int newCount = Math.min(cap, getEnchantLimit(stack) + amount);
        stack.set(ItemComponentTypes.ENCHANT_LIMITER, new LimitComponent(newCount));
    }
}

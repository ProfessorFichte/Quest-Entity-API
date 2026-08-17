package com.qeapi.compat;

import net.minecraft.world.entity.Entity;

// Villager Names has no separate name store of its own - it names villagers by calling vanilla
// Entity.setCustomName() directly. So once it's run, the generated name already IS the entity's
// vanilla custom name; this class only needs to gate on isLoaded() so the raw fallback still
// applies when the mod isn't present.
public final class VillagerNamesCompat {

    private static final String MOD_ID = "villagernames";

    private VillagerNamesCompat() {}

    public static boolean isLoaded() {
        return ModCompatUtil.isModLoaded(MOD_ID);
    }

    public static String resolveName(Entity entity) {
        return entity.hasCustomName() ? entity.getCustomName().getString() : entity.getName().getString();
    }
}

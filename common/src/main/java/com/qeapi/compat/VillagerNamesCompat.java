package com.qeapi.compat;

import net.minecraft.world.entity.Entity;

// Optional integration with Villager Names (Serilum, net.natamus.villagernames). That mod names
// villagers by calling vanilla Entity.setCustomName() directly (see its EntityFunctions.nameEntity) -
// it has no separate name store or query API of its own. So once it's loaded and has run, the
// generated name already IS the entity's vanilla custom name; this class only needs to gate on
// isLoaded() so the "raw" (non-custom-named) fallback still applies correctly when the mod isn't
// present.
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

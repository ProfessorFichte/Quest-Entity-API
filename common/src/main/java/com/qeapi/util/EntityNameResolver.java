package com.qeapi.util;

import com.qeapi.compat.VillagerNamesCompat;
import net.minecraft.world.entity.Entity;

// Resolves the display name used for the {entity_name} description/text placeholder.
public final class EntityNameResolver {

    private EntityNameResolver() {}

    public static String resolve(Entity entity) {
        if (entity == null) {
            return "";
        }
        if (VillagerNamesCompat.isLoaded()) {
            return VillagerNamesCompat.resolveName(entity);
        }
        return entity.getName().getString();
    }
}

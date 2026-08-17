package com.qeapi.util;

import net.minecraft.world.entity.projectile.FireworkRocketEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

// Flags a firework spawned purely for the claim-effect visual as damage-exempt, see FireworkRocketEntityMixin.
public final class NoDamageFireworks {

    private static final Set<FireworkRocketEntity> MARKED = Collections.newSetFromMap(new WeakHashMap<>());

    private NoDamageFireworks() {}

    public static void mark(FireworkRocketEntity firework) {
        MARKED.add(firework);
    }

    public static boolean isMarked(FireworkRocketEntity firework) {
        return MARKED.contains(firework);
    }
}

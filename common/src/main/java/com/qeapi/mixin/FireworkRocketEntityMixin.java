package com.qeapi.mixin;

import com.qeapi.util.NoDamageFireworks;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla deals proximity explosion damage on detonation for any firework, not just crossbow-fired
// ones - skip it for the decorative quest-claim firework (see NoDamageFireworks).
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Inject(method = "dealExplosionDamage()V", at = @At("HEAD"), cancellable = true)
    private void quest_api$skipDecorativeDamage(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        if (NoDamageFireworks.isMarked(self)) {
            ci.cancel();
        }
    }
}

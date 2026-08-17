package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks actual dealt damage (post-armor/absorption) for deal_damage_amount tasks - no generic
// "damage dealt" hook exists (Fabric/NeoForge events only cover death, not every hit).
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("TAIL"))
    private void quest_api$onActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        QuestEventHandler.onDamageDealt(self, source, amount);
    }
}

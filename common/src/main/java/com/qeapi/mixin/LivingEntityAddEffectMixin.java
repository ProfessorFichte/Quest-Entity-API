package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Tracks status-effect application for apply_status_effect quest tasks - no vanilla/Fabric API/
// NeoForge event fires on this, so it's hooked directly.
@Mixin(LivingEntity.class)
public abstract class LivingEntityAddEffectMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("TAIL"))
    private void qe_api$onAddEffect(MobEffectInstance effectInstance, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        QuestEventHandler.onEffectApplied(self, effectInstance, source);
    }
}

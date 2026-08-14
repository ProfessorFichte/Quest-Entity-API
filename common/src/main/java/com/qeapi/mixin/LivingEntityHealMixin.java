package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks healing for do_healing_amount quest tasks - vanilla heal(float) carries no source
// parameter at all, unlike damage, so attribution of healing OTHERS is a best-effort Spell Engine
// heuristic handled entirely in QuestEventHandler.onEntityHealed (self-healing is unconditional).
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {

    @Inject(method = "heal(F)V", at = @At("TAIL"))
    private void qe_api$onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        QuestEventHandler.onEntityHealed(self, amount);
    }
}

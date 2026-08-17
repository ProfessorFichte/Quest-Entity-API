package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks healing for do_healing_amount tasks - heal(float) carries no source param, so attributing
// healing OTHERS is a best-effort heuristic in QuestEventHandler.onEntityHealed (self-healing is
// unconditional).
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {

    @Inject(method = "heal(F)V", at = @At("TAIL"))
    private void quest_api$onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        QuestEventHandler.onEntityHealed(self, amount);
    }
}

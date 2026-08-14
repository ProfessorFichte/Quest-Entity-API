package com.qeapi.mixin.spellengine;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hooks the exact trigger Spell Engine's own Spell Binding Table advancement
@Mixin(targets = "net.spell_engine.spellbinding.SpellBindingCriteria", remap = false)
public abstract class SpellBindingCriteriaMixin {

    @Inject(method = "trigger", at = @At("TAIL"))
    private void qe_api$onSpellBound(ServerPlayer player, ResourceLocation spellPoolId, boolean isComplete, CallbackInfo ci) {
        QuestEventHandler.onSpellBound(player, spellPoolId, isComplete);
    }
}

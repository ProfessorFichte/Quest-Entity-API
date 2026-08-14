package com.qeapi.mixin.spellengine;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Soft dependency on Spell Engine - see SpellBindingCriteriaMixin for the required:false setup.
// Hooks Spell Engine's SpellBookCreationCriteria, fired when a player directly creates a pre-made
// spellbook for a pool at the Spell Binding Table (the other path to "the player now has a
// completed spellbook for this pool", alongside SpellBindingCriteria's isComplete flag).
@Mixin(targets = "net.spell_engine.spellbinding.SpellBookCreationCriteria", remap = false)
public abstract class SpellBookCreationCriteriaMixin {

    @Inject(method = "trigger", at = @At("TAIL"))
    private void qe_api$onSpellBookCreated(ServerPlayer player, ResourceLocation spellPoolId, CallbackInfo ci) {
        QuestEventHandler.onSpellPoolCompleted(player, spellPoolId);
    }
}

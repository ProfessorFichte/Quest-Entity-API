package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.advancements.critereon.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

// Tracks fishing catches for FishingTask by piggybacking on the same trigger vanilla uses for its
// own "Fishy Business" advancement - fires with an empty items collection when the catch was just
// a hooked entity/junk with no loot, and with the actual rewarded stacks otherwise.
@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingRodHookedTriggerMixin {

    @Inject(method = "trigger", at = @At("TAIL"))
    private void qe_api$onFishingRodHooked(ServerPlayer player, ItemStack rod, FishingHook hookEntity,
                                            Collection<ItemStack> items, CallbackInfo ci) {
        QuestEventHandler.onFishCaught(player, items);
    }
}

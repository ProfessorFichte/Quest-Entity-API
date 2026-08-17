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

// Tracks fishing catches for FishingTask via vanilla's "Fishy Business" trigger - fires with an
// empty items collection for hooked junk/entities with no loot, actual stacks otherwise.
@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingRodHookedTriggerMixin {

    @Inject(method = "trigger", at = @At("TAIL"))
    private void quest_api$onFishingRodHooked(ServerPlayer player, ItemStack rod, FishingHook hookEntity,
                                            Collection<ItemStack> items, CallbackInfo ci) {
        QuestEventHandler.onFishCaught(player, items);
    }
}

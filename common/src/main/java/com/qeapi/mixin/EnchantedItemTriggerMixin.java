package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.advancements.critereon.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks Enchanting Table use for EnchantingTask, off the same trigger vanilla's own "enchanter"
// advancement uses - only EnchantmentMenu calls this, so anvil-merging enchanted books doesn't.
@Mixin(EnchantedItemTrigger.class)
public abstract class EnchantedItemTriggerMixin {

    @Inject(method = "trigger", at = @At("TAIL"))
    private void quest_api$onEnchantedItem(ServerPlayer player, ItemStack item, int levels, CallbackInfo ci) {
        QuestEventHandler.onItemEnchanted(player, item);
    }
}

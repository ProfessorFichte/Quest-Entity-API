package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks item usage for quest tasks.
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    public abstract ItemStack getUseItem();

    // finishing use (eating, drinking, etc.)
    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void qe_api$onCompleteUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof ServerPlayer player) {
            ItemStack usingItem = getUseItem();
            if (!usingItem.isEmpty()) {
                QuestEventHandler.onItemUsed(player, usingItem);
            }
        }
    }

    // releasing an item (e.g. shooting a bow)
    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void qe_api$onReleaseUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof ServerPlayer player) {
            ItemStack usingItem = getUseItem();
            if (!usingItem.isEmpty()) {
                QuestEventHandler.onItemUsed(player, usingItem);
            }
        }
    }
}

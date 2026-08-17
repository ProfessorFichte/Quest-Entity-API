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

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    public abstract ItemStack getUseItem();

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void quest_api$onCompleteUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof ServerPlayer player) {
            ItemStack usingItem = getUseItem();
            if (!usingItem.isEmpty()) {
                QuestEventHandler.onItemUsed(player, usingItem);
            }
        }
    }

    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void quest_api$onReleaseUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof ServerPlayer player) {
            ItemStack usingItem = getUseItem();
            if (!usingItem.isEmpty()) {
                QuestEventHandler.onItemUsed(player, usingItem);
            }
        }
    }
}

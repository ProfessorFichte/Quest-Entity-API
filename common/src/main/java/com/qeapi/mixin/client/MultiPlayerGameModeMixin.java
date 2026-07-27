package com.qeapi.mixin.client;

import com.qeapi.client.ClientQuestCache;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Tracks when the player interacts with merchant entities, so the quest button on the merchant
// screen knows which entity to query.
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "interact", at = @At("HEAD"))
    private void qe_api$trackMerchantInteraction(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // villager or wandering trader
        if (target instanceof AbstractVillager) {
            ClientQuestCache.setLastMerchantEntity(target.getId(), target.getUUID());
        }
    }

    // interactAt covers entity interactions with a specific hit position
    @Inject(method = "interactAt", at = @At("HEAD"))
    private void qe_api$trackMerchantInteractionAt(Player player, Entity target, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (target instanceof AbstractVillager) {
            ClientQuestCache.setLastMerchantEntity(target.getId(), target.getUUID());
        }
    }
}

package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks Smithing Table results for SmithingTask. onTake only fires once mayPickup confirmed a
// matching recipe, so itemStack here is always the actual smithed result.
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void quest_api$onSmithingTake(Player player, ItemStack itemStack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestEventHandler.onItemSmithed(serverPlayer, itemStack);
        }
    }
}

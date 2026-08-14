package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Tracks anvil operations for AnvilTask. onTake only ever runs once mayPickup already confirmed a
// real (cost > 0) operation, so the input slot's item is still the pre-operation stack at HEAD -
// comparing it against the taken result is how AnvilTask tells an actual repair apart from a
// rename or an enchant-merge with no durability repaired. Reads the input slot through the public
// getSlot(int)/INPUT_SLOT API instead of @Shadow-ing ItemCombinerMenu's inherited inputSlots field,
// since Mixin can't reliably refmap a @Shadow field that's declared on a superclass.
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void qe_api$onAnvilTake(Player player, ItemStack itemStack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack before = self.getSlot(AnvilMenu.INPUT_SLOT).getItem().copy();
        QuestEventHandler.onAnvilUsed(serverPlayer, before, itemStack);
    }
}

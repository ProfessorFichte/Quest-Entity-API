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

// Input slot is still the pre-operation stack at HEAD; AnvilTask compares it against the taken
// result to tell an actual repair apart from a rename or an enchant-merge with no durability fixed.
// Reads via getSlot()/INPUT_SLOT rather than @Shadow since Mixin can't reliably refmap a shadowed
// field inherited from a superclass.
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void quest_api$onAnvilTake(Player player, ItemStack itemStack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack before = self.getSlot(AnvilMenu.INPUT_SLOT).getItem().copy();
        QuestEventHandler.onAnvilUsed(serverPlayer, before, itemStack);
    }
}

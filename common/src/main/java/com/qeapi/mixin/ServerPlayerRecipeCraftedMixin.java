package com.qeapi.mixin;

import com.qeapi.event.QuestEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Tracks crafting completions for CraftingTask off ServerPlayer.triggerRecipeCrafted, the one call
// site vanilla's own "recipe crafted" criterion uses for the crafting table, 2x2 grid, and Crafter
// block alike. The list param is the ingredients used, not the output, so the crafted item has to
// be resolved from the recipe itself.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRecipeCraftedMixin {

    @Inject(method = "triggerRecipeCrafted", at = @At("TAIL"))
    private void quest_api$onRecipeCrafted(RecipeHolder<?> recipeHolder, List<ItemStack> ingredients, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Recipe<?> recipe = recipeHolder.value();
        ItemStack result = recipe.getResultItem(self.level().registryAccess());
        QuestEventHandler.onItemCrafted(self, result);
    }
}

package com.qeapi.quest.reward.function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// modifies an ItemStack reward, like a simplified loot table function. player can be null for display purposes.
public interface ItemFunction {

    ItemStack apply(ItemStack stack, ServerPlayer player);

    ResourceLocation getTypeId();

    MapCodec<? extends ItemFunction> getCodec();

    // dispatches on the "function" field
    Codec<ItemFunction> CODEC = ItemFunctionTypes.CODEC;
}

package com.qeapi.quest.reward.function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public record SetLoreFunction(List<Component> lore) implements ItemFunction {

    public static final MapCodec<SetLoreFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ComponentSerialization.CODEC.listOf().fieldOf("lore").forGetter(SetLoreFunction::lore)
            ).apply(instance, SetLoreFunction::new)
    );

    @Override
    public ItemStack apply(ItemStack stack, ServerPlayer player) {
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("set_lore");
    }

    @Override
    public MapCodec<? extends ItemFunction> getCodec() {
        return CODEC;
    }
}

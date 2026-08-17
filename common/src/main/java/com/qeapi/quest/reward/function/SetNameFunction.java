package com.qeapi.quest.reward.function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record SetNameFunction(Component name) implements ItemFunction {

    public static final MapCodec<SetNameFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ComponentSerialization.CODEC.fieldOf("name").forGetter(SetNameFunction::name)
            ).apply(instance, SetNameFunction::new)
    );

    @Override
    public ItemStack apply(ItemStack stack, ServerPlayer player) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("set_name");
    }

    @Override
    public MapCodec<? extends ItemFunction> getCodec() {
        return CODEC;
    }
}

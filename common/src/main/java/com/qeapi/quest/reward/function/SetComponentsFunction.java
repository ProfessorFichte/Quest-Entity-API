package com.qeapi.quest.reward.function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// equivalent to minecraft:set_components loot function
public record SetComponentsFunction(DataComponentPatch components) implements ItemFunction {

    public static final MapCodec<SetComponentsFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    DataComponentPatch.CODEC.fieldOf("components").forGetter(SetComponentsFunction::components)
            ).apply(instance, SetComponentsFunction::new)
    );

    @Override
    public ItemStack apply(ItemStack stack, ServerPlayer player) {
        stack.applyComponents(components);
        return stack;
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("set_components");
    }

    @Override
    public MapCodec<? extends ItemFunction> getCodec() {
        return CODEC;
    }
}

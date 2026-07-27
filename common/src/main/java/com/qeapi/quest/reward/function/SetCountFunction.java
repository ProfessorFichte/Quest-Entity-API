package com.qeapi.quest.reward.function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record SetCountFunction(int count) implements ItemFunction {

    public static final MapCodec<SetCountFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("count").forGetter(SetCountFunction::count)
            ).apply(instance, SetCountFunction::new)
    );

    @Override
    public ItemStack apply(ItemStack stack, ServerPlayer player) {
        stack.setCount(count);
        return stack;
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("set_count");
    }

    @Override
    public MapCodec<? extends ItemFunction> getCodec() {
        return CODEC;
    }
}

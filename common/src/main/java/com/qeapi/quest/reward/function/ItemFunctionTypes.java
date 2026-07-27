package com.qeapi.quest.reward.function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ItemFunctionTypes {

    private static final Map<ResourceLocation, MapCodec<? extends ItemFunction>> CODECS = new HashMap<>();

    static {
        register(QuestEntityAPI.id("set_components"), SetComponentsFunction.CODEC);
        register(QuestEntityAPI.id("set_enchantments"), SetEnchantmentsFunction.CODEC);
        register(QuestEntityAPI.id("set_count"), SetCountFunction.CODEC);
        register(QuestEntityAPI.id("set_name"), SetNameFunction.CODEC);
        register(QuestEntityAPI.id("set_lore"), SetLoreFunction.CODEC);
        register(QuestEntityAPI.id("set_power_level"), SetPowerLevelFunction.CODEC);
    }

    private ItemFunctionTypes() {}

    public static <T extends ItemFunction> void register(ResourceLocation id, MapCodec<T> codec) {
        CODECS.put(id, codec);
    }

    public static MapCodec<? extends ItemFunction> getCodec(ResourceLocation id) {
        return CODECS.get(id);
    }

    public static final Codec<ItemFunction> CODEC = ResourceLocation.CODEC.dispatch(
            "function",
            ItemFunction::getTypeId,
            id -> {
                MapCodec<? extends ItemFunction> codec = CODECS.get(id);
                if (codec == null) {
                    throw new IllegalArgumentException("Unknown item function type: " + id);
                }
                return codec;
            }
    );
}

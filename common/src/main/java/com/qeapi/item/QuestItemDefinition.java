package com.qeapi.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;

// custom_model_data is author-chosen and doubles as this item's identity - two quest items must use different values, but that's the only uniqueness rule needed
public record QuestItemDefinition(
        ResourceLocation texture,
        Component name,
        int customModelData,
        Rarity rarity
) {
    public static final Codec<QuestItemDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("texture").forGetter(QuestItemDefinition::texture),
                    ComponentSerialization.CODEC.fieldOf("name").forGetter(QuestItemDefinition::name),
                    Codec.INT.fieldOf("custom_model_data").forGetter(QuestItemDefinition::customModelData),
                    Rarity.CODEC.optionalFieldOf("rarity", Rarity.COMMON).forGetter(QuestItemDefinition::rarity)
            ).apply(instance, QuestItemDefinition::new)
    );

    public ItemStack createStack(int count) {
        ItemStack stack = new ItemStack(QuestItems.QUEST_ITEM, count);
        stack.set(DataComponents.CUSTOM_NAME, name);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(customModelData));
        stack.set(DataComponents.RARITY, rarity);
        return stack;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != QuestItems.QUEST_ITEM) {
            return false;
        }
        CustomModelData stackModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return stackModelData != null && stackModelData.value() == customModelData;
    }
}

package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// One step of an EnhanceItemReward - reuses the existing TargetItemReward records as operations instead of duplicating their logic;
// adds a second dispatch codec (field "type" instead of "reward") for that list.
public sealed interface EnhanceOperation extends TargetItemReward permits
        EnchantRandomlyReward,
        EnchantSpecificReward,
        RepairItemReward,
        SpellBindReward,
        IncreasePowerLevelReward,
        IncreaseEnchantSlotsReward {

    ResourceLocation getTypeId();

    Component getDisplayText();

    Map<ResourceLocation, MapCodec<? extends EnhanceOperation>> OPERATION_TYPES = Map.of(
            QuestAPI.id("enchant_randomly"), EnchantRandomlyReward.CODEC,
            QuestAPI.id("enchant_specific"), EnchantSpecificReward.CODEC,
            QuestAPI.id("repair_item"), RepairItemReward.CODEC,
            ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_bind"), SpellBindReward.CODEC,
            ResourceLocation.fromNamespaceAndPath("dungeon_difficulty", "increase_power_level"), IncreasePowerLevelReward.CODEC,
            ResourceLocation.fromNamespaceAndPath("enchant_limiter", "increase_enchant_slots"), IncreaseEnchantSlotsReward.CODEC
    );

    @SuppressWarnings("unchecked")
    Codec<EnhanceOperation> CODEC = Codec.lazyInitialized(() ->
            ResourceLocation.CODEC.dispatch(
                    "type",
                    EnhanceOperation::getTypeId,
                    id -> {
                        MapCodec<? extends EnhanceOperation> codec = OPERATION_TYPES.get(id);
                        if (codec == null) {
                            throw new IllegalArgumentException("Unknown enhance operation type: " + id);
                        }
                        return (MapCodec<EnhanceOperation>) codec;
                    }
            )
    );
}

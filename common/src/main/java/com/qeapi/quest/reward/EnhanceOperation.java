package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestEntityAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// One step of an EnhanceItemReward - reuses the existing single-purpose TargetItemReward records
// as operations instead of duplicating their isValidTarget/applyToTarget logic. Each permitted
// type already implements TargetItemReward via QuestReward, so this only adds a second, smaller
// dispatch codec (keyed the same as REWARD_TYPES, field name "type" instead of "reward") for use
// inside EnhanceItemReward's operations list.
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
            QuestEntityAPI.id("enchant_randomly"), EnchantRandomlyReward.CODEC,
            QuestEntityAPI.id("enchant_specific"), EnchantSpecificReward.CODEC,
            QuestEntityAPI.id("repair_item"), RepairItemReward.CODEC,
            QuestEntityAPI.id("spell_bind"), SpellBindReward.CODEC,
            QuestEntityAPI.id("increase_power_level"), IncreasePowerLevelReward.CODEC,
            QuestEntityAPI.id("increase_enchant_slots"), IncreaseEnchantSlotsReward.CODEC
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

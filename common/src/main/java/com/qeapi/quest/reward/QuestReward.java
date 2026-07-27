package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestEntityAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// A reward granted when a player completes a quest and claims it.
public sealed interface QuestReward permits
        AdvancementReward,
        CommandReward,
        ExperienceReward,
        ItemReward,
        LootTableReward,
        StatusEffectReward,
        SkillExperienceReward,
        SkillLevelReward,
        SpellScrollReward,
        EnchantRandomlyReward,
        EnchantSpecificReward,
        RepairItemReward,
        SpellBindReward,
        IncreasePowerLevelReward,
        IncreaseEnchantSlotsReward,
        EnhanceItemReward,
        SetQuestGroupReward {

    Map<ResourceLocation, RewardType<?>> REWARD_TYPES = new HashMap<>();

    Codec<QuestReward> CODEC = Codec.lazyInitialized(() ->
            ResourceLocation.CODEC.dispatch(
                    "reward",
                    QuestReward::getTypeId,
                    id -> {
                        RewardType<?> type = REWARD_TYPES.get(id);
                        if (type == null) {
                            throw new IllegalArgumentException("Unknown reward type: " + id);
                        }
                        return type.mapCodec();
                    }
            )
    );

    ResourceLocation getTypeId();

    void grant(ServerPlayer player);

    Component getDisplayText();

    // Item to display in the GUI for this reward, if applicable.
    Optional<ItemStack> getDisplayItem();

    static <T extends QuestReward> void registerType(ResourceLocation id, MapCodec<T> codec) {
        REWARD_TYPES.put(id, new RewardType<>(id, codec));
    }

    static void registerBuiltInTypes() {
        registerType(QuestEntityAPI.id("advancement"), AdvancementReward.CODEC);
        registerType(QuestEntityAPI.id("command"), CommandReward.CODEC);
        registerType(QuestEntityAPI.id("experience"), ExperienceReward.CODEC);
        registerType(QuestEntityAPI.id("item"), ItemReward.CODEC);
        registerType(QuestEntityAPI.id("loot_table"), LootTableReward.CODEC);
        registerType(QuestEntityAPI.id("status_effect"), StatusEffectReward.CODEC);
        registerType(QuestEntityAPI.id("skill_experience"), SkillExperienceReward.CODEC);
        registerType(QuestEntityAPI.id("skill_level"), SkillLevelReward.CODEC);
        registerType(QuestEntityAPI.id("spell_scroll"), SpellScrollReward.CODEC);
        registerType(QuestEntityAPI.id("enchant_randomly"), EnchantRandomlyReward.CODEC);
        registerType(QuestEntityAPI.id("enchant_specific"), EnchantSpecificReward.CODEC);
        registerType(QuestEntityAPI.id("repair_item"), RepairItemReward.CODEC);
        registerType(QuestEntityAPI.id("spell_bind"), SpellBindReward.CODEC);
        registerType(QuestEntityAPI.id("increase_power_level"), IncreasePowerLevelReward.CODEC);
        registerType(QuestEntityAPI.id("increase_enchant_slots"), IncreaseEnchantSlotsReward.CODEC);
        registerType(QuestEntityAPI.id("enhance_item"), EnhanceItemReward.CODEC);
        registerType(QuestEntityAPI.id("set_quest_group"), SetQuestGroupReward.CODEC);
    }

    record RewardType<T extends QuestReward>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestReward> mapCodec() {
            return (MapCodec<QuestReward>) (MapCodec<?>) codec;
        }
    }
}

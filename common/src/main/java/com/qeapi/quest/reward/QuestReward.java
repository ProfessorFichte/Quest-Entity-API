package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public sealed interface QuestReward permits
        AdvancementReward,
        CommandReward,
        ExperienceReward,
        ItemReward,
        LootTableReward,
        StatusEffectReward,
        SkillExperienceReward,
        SkillLevelReward,
        LevelZSkillLevelReward,
        SpellScrollReward,
        EnchantRandomlyReward,
        EnchantSpecificReward,
        RepairItemReward,
        SpellBindReward,
        IncreasePowerLevelReward,
        IncreaseEnchantSlotsReward,
        EnhanceItemReward,
        SetQuestGroupReward,
        TeleportReward,
        MapToStructureReward {

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

    Optional<ItemStack> getDisplayItem();

    // see QuestTask.textureOverrideId
    Optional<ResourceLocation> textureOverrideId();

    static <T extends QuestReward> void registerType(ResourceLocation id, MapCodec<T> codec) {
        REWARD_TYPES.put(id, new RewardType<>(id, codec));
    }

    static void registerBuiltInTypes() {
        registerType(QuestAPI.id("advancement"), AdvancementReward.CODEC);
        registerType(QuestAPI.id("command"), CommandReward.CODEC); // supports {player}/{uuid}/{x}/{y}/{z} placeholders
        registerType(QuestAPI.id("experience"), ExperienceReward.CODEC);
        registerType(QuestAPI.id("item"), ItemReward.CODEC);
        registerType(QuestAPI.id("loot_table"), LootTableReward.CODEC);
        registerType(QuestAPI.id("status_effect"), StatusEffectReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("puffish_skills", "skill_experience"), SkillExperienceReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("puffish_skills", "skill_level"), SkillLevelReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("levelz", "levelz_skill_level"), LevelZSkillLevelReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_scroll"), SpellScrollReward.CODEC);
        registerType(QuestAPI.id("enchant_randomly"), EnchantRandomlyReward.CODEC);
        registerType(QuestAPI.id("enchant_specific"), EnchantSpecificReward.CODEC);
        registerType(QuestAPI.id("repair_item"), RepairItemReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_bind"), SpellBindReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("dungeon_difficulty", "increase_power_level"), IncreasePowerLevelReward.CODEC);
        registerType(ResourceLocation.fromNamespaceAndPath("enchant_limiter", "increase_enchant_slots"), IncreaseEnchantSlotsReward.CODEC);
        registerType(QuestAPI.id("enhance_item"), EnhanceItemReward.CODEC);
        registerType(QuestAPI.id("set_quest_group"), SetQuestGroupReward.CODEC);
        registerType(QuestAPI.id("teleport"), TeleportReward.CODEC);
        registerType(QuestAPI.id("map_to_structure"), MapToStructureReward.CODEC);
    }

    record RewardType<T extends QuestReward>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestReward> mapCodec() {
            return (MapCodec<QuestReward>) (MapCodec<?>) codec;
        }
    }
}

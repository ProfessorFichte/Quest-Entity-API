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
        TeleportToStructureReward,
        TeleportToCoordinatesReward,
        TeleportToBiomeReward,
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
        registerType(QuestEntityAPI.id("advancement"), AdvancementReward.CODEC); // grants an advancement
        registerType(QuestEntityAPI.id("command"), CommandReward.CODEC); // runs a command, with {player}/{uuid}/{x}/{y}/{z} placeholders
        registerType(QuestEntityAPI.id("experience"), ExperienceReward.CODEC); // grants XP points
        registerType(QuestEntityAPI.id("item"), ItemReward.CODEC); // grants an item stack, optionally with components/functions applied
        registerType(QuestEntityAPI.id("loot_table"), LootTableReward.CODEC); // rolls a loot table for random items
        registerType(QuestEntityAPI.id("status_effect"), StatusEffectReward.CODEC); // applies a status effect
        registerType(QuestEntityAPI.id("skill_experience"), SkillExperienceReward.CODEC); // grants XP in a Pufferfish's Skills tree
        registerType(QuestEntityAPI.id("skill_level"), SkillLevelReward.CODEC); // grants whole levels in a Pufferfish's Skills tree
        registerType(QuestEntityAPI.id("levelz_skill_level"), LevelZSkillLevelReward.CODEC); // grants whole levels in a LevelZ skill
        registerType(QuestEntityAPI.id("spell_scroll"), SpellScrollReward.CODEC); // grants a Spell Engine spell scroll, random or specific
        registerType(QuestEntityAPI.id("enchant_randomly"), EnchantRandomlyReward.CODEC); // enchants a player-picked item with a random valid enchantment
        registerType(QuestEntityAPI.id("enchant_specific"), EnchantSpecificReward.CODEC); // enchants a player-picked item with one specific enchantment
        registerType(QuestEntityAPI.id("repair_item"), RepairItemReward.CODEC); // fully repairs a player-picked item's durability
        registerType(QuestEntityAPI.id("spell_bind"), SpellBindReward.CODEC); // binds a specific spell onto a player-picked item
        registerType(QuestEntityAPI.id("increase_power_level"), IncreasePowerLevelReward.CODEC); // raises a player-picked item's Dungeon Difficulty power level
        registerType(QuestEntityAPI.id("increase_enchant_slots"), IncreaseEnchantSlotsReward.CODEC); // raises a player-picked item's Enchant Limiter slot cap
        registerType(QuestEntityAPI.id("enhance_item"), EnhanceItemReward.CODEC); // bundles several enhance operations onto one player-picked item
        registerType(QuestEntityAPI.id("set_quest_group"), SetQuestGroupReward.CODEC); // records the player's chosen quest_group/path for this entity
        registerType(QuestEntityAPI.id("teleport_to_structure"), TeleportToStructureReward.CODEC); // teleports the player near the nearest instance of a structure
        registerType(QuestEntityAPI.id("teleport_to_coordinates"), TeleportToCoordinatesReward.CODEC); // teleports the player to fixed coordinates (optional dimension)
        registerType(QuestEntityAPI.id("teleport_to_biome"), TeleportToBiomeReward.CODEC); // teleports the player to the nearest instance of a biome
        registerType(QuestEntityAPI.id("map_to_structure"), MapToStructureReward.CODEC); // grants a filled map to the nearest instance of a structure
    }

    record RewardType<T extends QuestReward>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestReward> mapCodec() {
            return (MapCodec<QuestReward>) (MapCodec<?>) codec;
        }
    }
}

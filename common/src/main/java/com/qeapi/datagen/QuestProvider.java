package com.qeapi.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.qeapi.QuestAPI;
import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.requirement.HasAdvancementRequirement;
import com.qeapi.quest.requirement.HasItemRequirement;
import com.qeapi.quest.requirement.HasLevelRequirement;
import com.qeapi.quest.requirement.HasLevelZSkillRequirement;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.*;
import com.qeapi.quest.task.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.biome.Biome;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// extend this and implement addQuests() with the builder API below to generate quest JSON for a mod
public abstract class QuestProvider implements DataProvider {

    protected final PackOutput output;
    protected final String modId;
    private final Map<ResourceLocation, QuestPool> pools = new HashMap<>();
    // "#tag"/quest-id references (see includePool) written into a pool's generated tag so one quest can belong to several pools
    private final Map<ResourceLocation, List<String>> poolExtraTagValues = new HashMap<>();

    protected QuestProvider(PackOutput output, String modId) {
        this.output = output;
        this.modId = modId;
    }

    protected abstract void addQuests();

    // exposed so LangProvider can trigger the same literal-text registration on LangEntries without relying on provider registration order
    public void collectQuests() {
        pools.clear();
        poolExtraTagValues.clear();
        addQuests();
    }

    // used by QuestItemModelProvider to find inline quest_item definitions without depending on provider registration order either
    List<Quest> getAllGeneratedQuests() {
        return pools.values().stream()
                .flatMap(pool -> pool.getQuestsByTier().values().stream())
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        collectQuests();

        List<CompletableFuture<?>> futures = new ArrayList<>();

        for (Map.Entry<ResourceLocation, QuestPool> entry : pools.entrySet()) {
            ResourceLocation tagId = entry.getKey();
            QuestPool pool = entry.getValue();

            List<Quest> quests = pool.getQuestsByTier().values().stream().flatMap(List::stream).toList();

            for (Quest quest : quests) {
                Path questPath = output.getOutputFolder()
                        .resolve("data")
                        .resolve(quest.id().getNamespace())
                        .resolve("entity_quest")
                        .resolve(quest.id().getPath() + ".json");

                JsonElement json = Quest.CODEC.encodeStart(JsonOps.INSTANCE, quest)
                        .resultOrPartial(error -> QuestAPI.LOGGER.error("Failed to encode quest {}: {}", quest.id(), error))
                        .orElse(null);

                if (json != null) {
                    futures.add(DataProvider.saveStable(cache, json, questPath));
                }
            }

            Path tagPath = output.getOutputFolder()
                    .resolve("data")
                    .resolve(tagId.getNamespace())
                    .resolve("tags/entity_quests")
                    .resolve(tagId.getPath() + ".json");

            JsonObject tagJson = new JsonObject();
            tagJson.addProperty("replace", false);
            JsonArray values = new JsonArray();
            quests.forEach(quest -> values.add(quest.id().toString()));
            poolExtraTagValues.getOrDefault(tagId, List.of()).forEach(values::add);
            tagJson.add("values", values);

            futures.add(DataProvider.saveStable(cache, tagJson, tagPath));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Quest API Quests: " + modId;
    }

    // ==================== Builder API ====================

    protected PoolBuilder createPool(String name) {
        return new PoolBuilder(ResourceLocation.fromNamespaceAndPath(modId, name));
    }

    protected PoolBuilder createPool(ResourceLocation id) {
        return new PoolBuilder(id);
    }

    // ==================== Task Helpers ====================

    protected EntityKillTask entityKill(EntityType<?> entity, int amount) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .build();
    }

    protected EntityKillTask entityKill(TagKey<EntityType<?>> tag, int amount) {
        return EntityKillTask.builder()
                .entityTag(tag)
                .amount(amount)
                .build();
    }

    protected EntityKillTask entityKill(String entityId, int amount) {
        return EntityKillTask.builder()
                .entityId(entityId)
                .amount(amount)
                .build();
    }

    protected EntityKillTask entityKill(EntityType<?> entity, int amount, ResourceLocation textureOverrideId) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    // OR logic - any of these entity types counts
    protected EntityKillTask entityKillMultiple(int amount, String... entityIds) {
        return EntityKillTask.builder()
                .entityIds(entityIds)
                .amount(amount)
                .build();
    }

    protected EntityKillTask entityKillInBiome(EntityType<?> entity, int amount, String biomeId) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .inBiome(biomeId)
                .build();
    }

    protected EntityKillTask entityKillInBiomeTag(EntityType<?> entity, int amount, TagKey<Biome> biomeTag) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .inBiomeTag(biomeTag)
                .build();
    }

    protected EntityKillTask entityKillInDimension(EntityType<?> entity, int amount, String dimensionId) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .inDimension(dimensionId)
                .build();
    }

    protected EntityKillTask entityKillInStructure(EntityType<?> entity, int amount, String structureId) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .inStructure(structureId)
                .build();
    }

    // like entityKillInStructure, but also grants a one-time treasure map to the structure on accept
    protected EntityKillTask entityKillInStructureWithMap(EntityType<?> entity, int amount, String structureId) {
        return EntityKillTask.builder()
                .entityId(BuiltInRegistries.ENTITY_TYPE.getKey(entity))
                .amount(amount)
                .inStructure(structureId)
                .providesMap(true)
                .build();
    }

    protected FindStructureTask findStructure(String structureId) {
        return FindStructureTask.of(structureId);
    }

    protected FindStructureTask findStructure(ResourceLocation structureId) {
        return FindStructureTask.of(structureId);
    }

    // never re-granted, even across decline/re-accept cycles (see PlayerQuestData.hasMapBeenGranted)
    protected FindStructureTask findStructureWithMap(String structureId) {
        return FindStructureTask.ofWithMap(structureId);
    }

    protected FindStructureTask findStructureWithMap(ResourceLocation structureId) {
        return FindStructureTask.ofWithMap(structureId);
    }

    protected FindStructureTask findStructure(String structureId, ResourceLocation textureOverrideId) {
        return FindStructureTask.of(ResourceLocation.parse(structureId), textureOverrideId);
    }

    protected FindStructureTask findStructure(ResourceLocation structureId, ResourceLocation textureOverrideId) {
        return FindStructureTask.of(structureId, textureOverrideId);
    }

    // caps how far the nearest matching structure may be from the quest-giver for this task to be offered at all
    protected FindStructureTask findStructure(String structureId, int maxDistance) {
        return FindStructureTask.of(structureId).withMaxDistance(maxDistance);
    }

    protected FindStructureTask findStructureWithMap(String structureId, int maxDistance) {
        return FindStructureTask.ofWithMap(structureId).withMaxDistance(maxDistance);
    }

    // Dungeon Difficulty compat - requires the found structure's location to be at least this power level
    protected FindStructureTask findStructure(String structureId, int maxDistance, int minPowerLevel) {
        return FindStructureTask.of(structureId).withMaxDistance(maxDistance).withMinPowerLevel(minPowerLevel);
    }

    protected BringItemTask bringItem(Item item, int amount) {
        return BringItemTask.builder()
                .item(item)
                .amount(amount)
                .build();
    }

    protected BringItemTask bringItem(String itemId, int amount) {
        return BringItemTask.builder()
                .itemId(itemId)
                .amount(amount)
                .build();
    }

    protected BringItemTask bringItem(Item item, int amount, ResourceLocation textureOverrideId) {
        return BringItemTask.builder()
                .item(item)
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    protected BringItemTask bringItemQuestItem(QuestItemDefinition questItem, int amount) {
        return BringItemTask.builder()
                .questItem(questItem)
                .amount(amount)
                .build();
    }

    protected DeliverItemTask deliverItem(Item item, int amount, EntityType<?> targetEntity) {
        return DeliverItemTask.builder()
                .item(item)
                .amount(amount)
                .targetEntityId(BuiltInRegistries.ENTITY_TYPE.getKey(targetEntity))
                .build();
    }

    protected DeliverItemTask deliverItem(Item item, int amount, TagKey<EntityType<?>> targetEntityTag) {
        return DeliverItemTask.builder()
                .item(item)
                .amount(amount)
                .targetEntityTag(targetEntityTag)
                .build();
    }

    // translation key is synthesized from this mod's ID and customModelData, which must be unique per quest item
    protected QuestItemDefinition questItem(ResourceLocation texture, int customModelData, String literalName) {
        return questItem(texture, customModelData, literalName, Rarity.COMMON);
    }

    protected QuestItemDefinition questItem(ResourceLocation texture, int customModelData, String literalName, Rarity rarity) {
        String key = "quest_item." + modId + "." + customModelData;
        LangEntries.add(key, literalName);
        return new QuestItemDefinition(texture, Component.translatable(key), customModelData, rarity);
    }

    protected QuestItemDefinition questItem(ResourceLocation texture, int customModelData, Component name, Rarity rarity) {
        return new QuestItemDefinition(texture, name, customModelData, rarity);
    }

    protected BlocksTraveledTask blocksTraveled(int distance) {
        return BlocksTraveledTask.of(distance);
    }

    protected BlocksTraveledTask blocksTraveled(int distance, ResourceLocation textureOverrideId) {
        return BlocksTraveledTask.of(distance, textureOverrideId);
    }

    protected ItemUsedTask itemUsed(Item item, int amount) {
        return ItemUsedTask.builder()
                .item(item)
                .amount(amount)
                .build();
    }

    protected ItemUsedTask itemUsed(Item item, int amount, ResourceLocation textureOverrideId) {
        return ItemUsedTask.builder()
                .item(item)
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    protected BrewPotionTask brewPotion(net.minecraft.world.item.alchemy.Potion potion, int amount) {
        return BrewPotionTask.builder()
                .potion(potion)
                .amount(amount)
                .build();
    }

    protected BrewPotionTask brewPotion(net.minecraft.world.item.alchemy.Potion potion, int amount, ResourceLocation textureOverrideId) {
        return BrewPotionTask.builder()
                .potion(potion)
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    protected MineBlockTask mineBlock(net.minecraft.world.level.block.Block block, int amount) {
        return MineBlockTask.builder()
                .block(block)
                .amount(amount)
                .build();
    }

    protected MineBlockTask mineBlock(net.minecraft.world.level.block.Block block, int amount, ResourceLocation textureOverrideId) {
        return MineBlockTask.builder()
                .block(block)
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    protected MineBlockTask mineBlockTag(TagKey<net.minecraft.world.level.block.Block> blockTag, int amount) {
        return MineBlockTask.builder()
                .blockTag(blockTag)
                .amount(amount)
                .build();
    }

    protected FishingTask fishing(int amount) {
        return FishingTask.builder().amount(amount).build();
    }

    protected FishingTask fishing(Item fish, int amount) {
        return FishingTask.builder().fish(fish).amount(amount).build();
    }

    protected FishingTask fishingTag(TagKey<Item> fishTag, int amount) {
        return FishingTask.builder().fishTag(fishTag).amount(amount).build();
    }

    protected FishingTask fishing(int amount, ResourceLocation textureOverrideId) {
        return FishingTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    protected HarvestCropsTask harvestCrops(int amount) {
        return HarvestCropsTask.builder().amount(amount).build();
    }

    protected HarvestCropsTask harvestCrops(net.minecraft.world.level.block.Block crop, int amount) {
        return HarvestCropsTask.builder().crop(crop).amount(amount).build();
    }

    protected HarvestCropsTask harvestCropsTag(TagKey<net.minecraft.world.level.block.Block> cropTag, int amount) {
        return HarvestCropsTask.builder().cropTag(cropTag).amount(amount).build();
    }

    protected HarvestCropsTask harvestCrops(int amount, ResourceLocation textureOverrideId) {
        return HarvestCropsTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    protected AnvilTask anvilRepair(int amount) {
        return AnvilTask.builder().amount(amount).build();
    }

    protected AnvilTask anvilRepair(Item resultItem, int amount) {
        return AnvilTask.builder().resultItem(resultItem).amount(amount).build();
    }

    protected AnvilTask anvilRepair(int amount, ResourceLocation textureOverrideId) {
        return AnvilTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    protected SmithingTask smithing(int amount) {
        return SmithingTask.builder().amount(amount).build();
    }

    protected SmithingTask smithing(Item resultItem, int amount) {
        return SmithingTask.builder().resultItem(resultItem).amount(amount).build();
    }

    protected SmithingTask smithingTag(TagKey<Item> resultItemTag, int amount) {
        return SmithingTask.builder().resultItemTag(resultItemTag).amount(amount).build();
    }

    protected SmithingTask smithing(int amount, ResourceLocation textureOverrideId) {
        return SmithingTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    protected CraftingTask crafting(int amount) {
        return CraftingTask.builder().amount(amount).build();
    }

    protected CraftingTask crafting(Item resultItem, int amount) {
        return CraftingTask.builder().resultItem(resultItem).amount(amount).build();
    }

    protected CraftingTask craftingTag(TagKey<Item> resultItemTag, int amount) {
        return CraftingTask.builder().resultItemTag(resultItemTag).amount(amount).build();
    }

    protected CraftingTask crafting(int amount, ResourceLocation textureOverrideId) {
        return CraftingTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    protected EnchantingTask enchanting(int amount) {
        return EnchantingTask.builder().amount(amount).build();
    }

    protected EnchantingTask enchanting(ResourceLocation enchantmentId, int amount) {
        return EnchantingTask.builder().enchantmentId(enchantmentId).amount(amount).build();
    }

    protected EnchantingTask enchanting(int amount, ResourceLocation textureOverrideId) {
        return EnchantingTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    // Spell Engine integration tasks
    protected SpellBindTask spellBindTask(int amount) {
        return SpellBindTask.builder().amount(amount).build();
    }

    protected SpellBindTask spellBindTask(ResourceLocation spellPool, int amount) {
        return SpellBindTask.builder().spellPool(spellPool).amount(amount).build();
    }

    protected SpellBindTask spellBindTask(int amount, ResourceLocation textureOverrideId) {
        return SpellBindTask.builder().amount(amount).textureOverrideId(textureOverrideId).build();
    }

    protected SpellPoolCompleteTask spellPoolCompleteTask(ResourceLocation spellPool) {
        return SpellPoolCompleteTask.builder().spellPool(spellPool).build();
    }

    protected SpellPoolCompleteTask spellPoolCompleteTask(ResourceLocation spellPool, ResourceLocation textureOverrideId) {
        return SpellPoolCompleteTask.builder().spellPool(spellPool).textureOverrideId(textureOverrideId).build();
    }

    protected SpellCastTask spellCast(ResourceLocation spellId, int amount) {
        return SpellCastTask.builder()
                .spellId(spellId)
                .amount(amount)
                .build();
    }

    protected SpellCastTask spellCast(ResourceLocation spellId, int amount, ResourceLocation textureOverrideId) {
        return SpellCastTask.builder()
                .spellId(spellId)
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    protected SpellCastTask spellCastFromPool(ResourceLocation spellPool, int amount) {
        return SpellCastTask.builder()
                .spellPool(spellPool)
                .amount(amount)
                .build();
    }

    protected SpellCastTask spellCastFromSchool(ResourceLocation spellSchool, int amount) {
        return SpellCastTask.builder()
                .spellSchool(spellSchool)
                .amount(amount)
                .build();
    }

    protected RaidCompleteTask raidComplete(int amount) {
        return RaidCompleteTask.builder().amount(amount).build();
    }

    protected RaidCompleteTask raidComplete(int amount, int minRaidLevel) {
        return RaidCompleteTask.builder().amount(amount).minRaidLevel(minRaidLevel).build();
    }

    protected TrialSpawnerCompleteTask trialSpawnerComplete(int amount) {
        return TrialSpawnerCompleteTask.builder().amount(amount).build();
    }

    protected TrialSpawnerCompleteTask trialSpawnerComplete(int amount, boolean ominous) {
        return TrialSpawnerCompleteTask.builder().amount(amount).ominous(ominous).build();
    }

    protected VisitBiomeTask visitBiome(String biomeId) {
        return VisitBiomeTask.builder().biomeId(biomeId).build();
    }

    protected VisitBiomeTask visitBiome(TagKey<Biome> biomeTag, int amount) {
        return VisitBiomeTask.builder().biomeTag(biomeTag).amount(amount).build();
    }

    protected VisitBiomeTask visitBiomeAny(int amount) {
        return VisitBiomeTask.builder().amount(amount).build();
    }

    protected ApplyStatusEffectTask applyStatusEffect(ResourceLocation effectId, int amount) {
        return ApplyStatusEffectTask.builder().effectId(effectId).amount(amount).build();
    }

    protected DealDamageAmountTask dealDamageAmount(double amount) {
        return DealDamageAmountTask.builder().amount(amount).build();
    }

    protected DoHealingAmountTask doHealingAmount(double amount) {
        return DoHealingAmountTask.builder().amount(amount).build();
    }

    // ==================== Requirement Helpers ====================

    protected HasAdvancementRequirement hasAdvancement(String advancementId) {
        return HasAdvancementRequirement.of(advancementId);
    }

    protected HasAdvancementRequirement hasAdvancement(ResourceLocation advancementId) {
        return HasAdvancementRequirement.of(advancementId);
    }

    protected HasAdvancementRequirement hasAdvancement(String advancementId, ResourceLocation textureOverrideId) {
        return new HasAdvancementRequirement(ResourceLocation.parse(advancementId), Optional.of(textureOverrideId));
    }

    protected HasLevelRequirement hasLevel(int level) {
        return HasLevelRequirement.of(level);
    }

    protected HasLevelRequirement hasLevel(int level, ResourceLocation textureOverrideId) {
        return new HasLevelRequirement(level, Optional.of(textureOverrideId));
    }

    protected HasItemRequirement hasItem(Item item, int amount) {
        return HasItemRequirement.of(BuiltInRegistries.ITEM.getKey(item), amount);
    }

    protected HasItemRequirement hasItem(String itemId, int amount) {
        return HasItemRequirement.of(itemId, amount);
    }

    protected HasItemRequirement hasItem(Item item) {
        return hasItem(item, 1);
    }

    protected HasItemRequirement hasItem(Item item, int amount, ResourceLocation textureOverrideId) {
        return new HasItemRequirement(BuiltInRegistries.ITEM.getKey(item), amount, Optional.of(textureOverrideId));
    }

    // LevelZ integration - skillId is LevelZ's skill key, e.g. "melee", "mining"
    protected HasLevelZSkillRequirement hasLevelZSkill(String skillId, int level) {
        return HasLevelZSkillRequirement.of(skillId, level);
    }

    protected HasLevelZSkillRequirement hasLevelZSkill(String skillId, int level, ResourceLocation textureOverrideId) {
        return new HasLevelZSkillRequirement(skillId, level, Optional.of(textureOverrideId));
    }

    // ==================== Reward Helpers ====================

    protected ExperienceReward experience(int amount) {
        return ExperienceReward.of(amount);
    }

    protected ExperienceReward experience(int amount, ResourceLocation textureOverrideId) {
        return new ExperienceReward(amount, Optional.of(textureOverrideId));
    }

    // usually one option among several in rewardChoicePool(...), so picking it commits the player to that group
    protected SetQuestGroupReward setQuestGroup(String group) {
        return new SetQuestGroupReward(group, Optional.empty());
    }

    protected SetQuestGroupReward setQuestGroup(String group, ResourceLocation textureOverrideId) {
        return new SetQuestGroupReward(group, textureOverrideId);
    }

    protected ItemReward item(Item item, int amount) {
        return ItemReward.builder()
                .item(item)
                .amount(amount)
                .build();
    }

    protected ItemReward item(Item item, int amount, ResourceLocation textureOverrideId) {
        return ItemReward.builder()
                .item(item)
                .amount(amount)
                .textureOverrideId(textureOverrideId)
                .build();
    }

    protected TeleportReward teleportToStructure(String structureId) {
        return TeleportReward.toStructure(ResourceLocation.parse(structureId));
    }

    protected TeleportReward teleportToStructure(String structureId, int maxDistanceRange) {
        return TeleportReward.toStructure(ResourceLocation.parse(structureId), maxDistanceRange);
    }

    protected TeleportReward teleportToStructure(String structureId, ResourceLocation textureOverrideId) {
        return new TeleportReward(new TeleportReward.ToStructure(ResourceLocation.parse(structureId),
                com.qeapi.util.StructureDistanceUtil.DEFAULT_MAX_DISTANCE), Optional.of(textureOverrideId));
    }

    protected TeleportReward teleportToCoordinates(double x, double y, double z) {
        return TeleportReward.toCoordinates(x, y, z);
    }

    protected TeleportReward teleportToCoordinates(double x, double y, double z, String dimension) {
        return TeleportReward.toCoordinates(x, y, z, ResourceLocation.parse(dimension));
    }

    protected TeleportReward teleportToBiome(String biomeId) {
        return TeleportReward.toBiome(ResourceLocation.parse(biomeId));
    }

    protected TeleportReward teleportToBiome(String biomeId, int maxDistanceRange) {
        return TeleportReward.toBiome(ResourceLocation.parse(biomeId), maxDistanceRange);
    }

    protected MapToStructureReward mapToStructure(String structureId) {
        return MapToStructureReward.of(ResourceLocation.parse(structureId));
    }

    protected MapToStructureReward mapToStructure(String structureId, int maxDistanceRange) {
        return new MapToStructureReward(ResourceLocation.parse(structureId), maxDistanceRange);
    }

    protected MapToStructureReward mapToStructure(String structureId, ResourceLocation textureOverrideId) {
        return new MapToStructureReward(ResourceLocation.parse(structureId),
                com.qeapi.util.StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.of(textureOverrideId));
    }

    protected ItemReward item(String itemId, int amount) {
        return ItemReward.builder()
                .itemId(itemId)
                .amount(amount)
                .build();
    }

    // Supports placeholders: {player}, {uuid}, {x}, {y}, {z}
    protected CommandReward command(String command) {
        return CommandReward.of(command);
    }

    protected CommandReward command(String command, String displayName) {
        return CommandReward.of(command, displayName);
    }

    protected CommandReward command(String command, String displayName, ResourceLocation textureOverrideId) {
        return new CommandReward(command, Optional.of(displayName), Optional.of(textureOverrideId));
    }

    protected AdvancementReward advancement(String advancementId) {
        return AdvancementReward.of(advancementId);
    }

    protected AdvancementReward advancement(ResourceLocation advancementId) {
        return AdvancementReward.of(advancementId);
    }

    protected AdvancementReward advancement(ResourceLocation advancementId, ResourceLocation textureOverrideId) {
        return new AdvancementReward(advancementId, Optional.of(textureOverrideId));
    }

    protected LootTableReward lootTable(String lootTableId) {
        return LootTableReward.of(lootTableId);
    }

    protected LootTableReward lootTable(ResourceLocation lootTableId) {
        return LootTableReward.of(lootTableId);
    }

    protected LootTableReward lootTable(ResourceLocation lootTableId, ResourceLocation textureOverrideId) {
        return new LootTableReward(lootTableId, Optional.of(textureOverrideId));
    }

    protected StatusEffectReward statusEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) {
        return StatusEffectReward.of(effect, durationTicks, amplifier);
    }

    protected StatusEffectReward statusEffect(String effectId, int durationTicks, int amplifier) {
        return StatusEffectReward.of(effectId, durationTicks, amplifier);
    }

    protected StatusEffectReward statusEffect(String effectId, int durationTicks, int amplifier, ResourceLocation textureOverrideId) {
        return new StatusEffectReward(ResourceLocation.parse(effectId), durationTicks / 20, amplifier, Optional.of(textureOverrideId));
    }

    // Pufferfish's Skills integration - grants raw experience in a skill tree
    protected SkillExperienceReward skillExperience(ResourceLocation skillTreeId, int amount) {
        return new SkillExperienceReward(skillTreeId, amount);
    }

    protected SkillExperienceReward skillExperience(String skillTreeId, int amount) {
        return new SkillExperienceReward(ResourceLocation.parse(skillTreeId), amount);
    }

    // same as above, with a custom icon (e.g. the skill tree's own category icon) in the quest GUI
    protected SkillExperienceReward skillExperience(ResourceLocation skillTreeId, int amount, ResourceLocation textureOverrideId) {
        return new SkillExperienceReward(skillTreeId, amount, Optional.of(textureOverrideId));
    }

    // Pufferfish's Skills integration - grants whole skill-tree levels
    protected SkillLevelReward skillLevel(ResourceLocation skillTreeId, int levels) {
        return new SkillLevelReward(skillTreeId, levels);
    }

    protected SkillLevelReward skillLevel(String skillTreeId, int levels) {
        return new SkillLevelReward(ResourceLocation.parse(skillTreeId), levels);
    }

    // same as above, with a custom icon in the quest GUI
    protected SkillLevelReward skillLevel(ResourceLocation skillTreeId, int levels, ResourceLocation textureOverrideId) {
        return new SkillLevelReward(skillTreeId, levels, Optional.of(textureOverrideId));
    }

    // icon is always the skill's own sprite (LevelZCompat.skillIcon), so unlike the Pufferfish rewards above there's no icon param
    protected LevelZSkillLevelReward levelZSkillLevel(String skillId, int levels) {
        return new LevelZSkillLevelReward(skillId, levels);
    }

    protected LevelZSkillLevelReward levelZSkillLevel(String skillId, int levels, ResourceLocation textureOverrideId) {
        return new LevelZSkillLevelReward(skillId, levels, Optional.of(textureOverrideId));
    }

    // Spell Engine integration - random spell from a pool (tag), within the tier range (inclusive)
    protected SpellScrollReward spellScrollFromPool(ResourceLocation pool, int tierMin, int tierMax) {
        return SpellScrollReward.builder().pool(pool).tierRange(tierMin, tierMax).build();
    }

    // one exact spell, bypassing random selection entirely
    protected SpellScrollReward spellScroll(ResourceLocation spellId) {
        return SpellScrollReward.builder().spellId(spellId).build();
    }

    protected SpellScrollReward spellScroll(ResourceLocation spellId, ResourceLocation textureOverrideId) {
        return SpellScrollReward.builder().spellId(spellId).textureOverrideId(textureOverrideId).build();
    }

    // target-item rewards - the player picks which of their own inventory items each applies to, in the quest GUI's item-picker overlay
    protected EnchantRandomlyReward enchantRandomly(int levelCap) {
        return new EnchantRandomlyReward(levelCap);
    }

    protected EnchantRandomlyReward enchantRandomly(int levelCap, ResourceLocation textureOverrideId) {
        return new EnchantRandomlyReward(levelCap, Optional.of(textureOverrideId));
    }

    protected EnchantSpecificReward enchantSpecific(ResourceLocation enchantmentId, int level) {
        return new EnchantSpecificReward(enchantmentId, level);
    }

    protected EnchantSpecificReward enchantSpecific(String enchantmentId, int level) {
        return new EnchantSpecificReward(ResourceLocation.parse(enchantmentId), level);
    }

    protected EnchantSpecificReward enchantSpecific(ResourceLocation enchantmentId, int level, ResourceLocation textureOverrideId) {
        return new EnchantSpecificReward(enchantmentId, level, Optional.of(textureOverrideId));
    }

    protected RepairItemReward repairItem() {
        return new RepairItemReward();
    }

    protected RepairItemReward repairItem(ResourceLocation textureOverrideId) {
        return new RepairItemReward(Optional.of(textureOverrideId));
    }

    // Spell Engine integration - binds one specific spell onto the chosen item, making it a spell container if it isn't one yet
    protected SpellBindReward spellBind(ResourceLocation spellId) {
        return new SpellBindReward(spellId);
    }

    protected SpellBindReward spellBind(String spellId) {
        return new SpellBindReward(ResourceLocation.parse(spellId));
    }

    protected SpellBindReward spellBind(ResourceLocation spellId, boolean clearExisting) {
        return new SpellBindReward(spellId, clearExisting);
    }

    protected SpellBindReward spellBind(String spellId, boolean clearExisting) {
        return new SpellBindReward(ResourceLocation.parse(spellId), clearExisting);
    }

    protected SpellBindReward spellBind(ResourceLocation spellId, ResourceLocation textureOverrideId) {
        return new SpellBindReward(spellId, false, Optional.of(textureOverrideId));
    }

    // Dungeon Difficulty integration - raises the chosen item's existing power level by amount, up to cap
    protected IncreasePowerLevelReward increasePowerLevel(int amount, int cap) {
        return new IncreasePowerLevelReward(amount, cap);
    }

    protected IncreasePowerLevelReward increasePowerLevel(int amount, int cap, ResourceLocation textureOverrideId) {
        return new IncreasePowerLevelReward(amount, cap, Optional.of(textureOverrideId));
    }

    // Enchant Limiter integration - grants the chosen item extra enchantment slots, up to cap
    protected IncreaseEnchantSlotsReward increaseEnchantSlots(int amount, int cap) {
        return new IncreaseEnchantSlotsReward(amount, cap);
    }

    protected IncreaseEnchantSlotsReward increaseEnchantSlots(int amount, int cap, ResourceLocation textureOverrideId) {
        return new IncreaseEnchantSlotsReward(amount, cap, Optional.of(textureOverrideId));
    }

    // bundles any of the above target-item operations onto one player-chosen item, claimed with a single item pick instead of one per reward
    protected EnhanceItemReward enhanceItem(EnhanceOperation... operations) {
        return new EnhanceItemReward(List.of(operations));
    }

    protected EnhanceItemReward enhanceItem(ResourceLocation textureOverrideId, EnhanceOperation... operations) {
        return new EnhanceItemReward(List.of(operations), Optional.of(textureOverrideId));
    }

    // restrictionItemTag narrows the item picker to only that tag, on top of each operation's own isValidTarget check
    protected EnhanceItemReward enhanceItemRestricted(TagKey<Item> restrictionItemTag, EnhanceOperation... operations) {
        return new EnhanceItemReward(List.of(operations), Optional.of(restrictionItemTag), Optional.empty());
    }

    protected EnhanceItemReward enhanceItemRestricted(TagKey<Item> restrictionItemTag, ResourceLocation textureOverrideId, EnhanceOperation... operations) {
        return new EnhanceItemReward(List.of(operations), Optional.of(restrictionItemTag), Optional.of(textureOverrideId));
    }

    // ==================== Builder Classes ====================

    public class PoolBuilder {
        private final ResourceLocation id;
        private boolean followOrder = true;
        private final Map<Integer, List<Quest>> tiers = new HashMap<>();
        private final List<String> extraTagValues = new ArrayList<>();

        PoolBuilder(ResourceLocation id) {
            this.id = id;
        }

        public PoolBuilder followOrder(boolean follow) {
            this.followOrder = follow;
            return this;
        }

        // the tag reference is resolved at runtime, so the other pool just has to exist in loaded data, not be built before this one
        public PoolBuilder includePool(String poolName) {
            return includeTag(ResourceLocation.fromNamespaceAndPath(modId, poolName));
        }

        public PoolBuilder includePool(ResourceLocation poolId) {
            return includeTag(poolId);
        }

        public PoolBuilder includeTag(ResourceLocation tagId) {
            extraTagValues.add("#" + tagId);
            return this;
        }

        public PoolBuilder includeQuest(ResourceLocation questId) {
            extraTagValues.add(questId.toString());
            return this;
        }

        public TierBuilder tier(int tier) {
            return new TierBuilder(this, tier);
        }

        void addQuest(int tier, Quest quest) {
            tiers.computeIfAbsent(tier, k -> new ArrayList<>()).add(quest);
        }

        public void build() {
            QuestPool pool = new QuestPool(id, followOrder, tiers);
            pools.put(id, pool);
            poolExtraTagValues.put(id, List.copyOf(extraTagValues));
        }
    }

    public class TierBuilder {
        private final PoolBuilder poolBuilder;
        private final int tier;

        TierBuilder(PoolBuilder poolBuilder, int tier) {
            this.poolBuilder = poolBuilder;
            this.tier = tier;
        }

        public QuestBuilder quest(String name) {
            ResourceLocation questId = ResourceLocation.fromNamespaceAndPath(modId,
                    poolBuilder.id.getPath() + "/" + name);
            return new QuestBuilder(this, questId);
        }

        public TierBuilder tier(int tier) {
            return poolBuilder.tier(tier);
        }

        public void build() {
            poolBuilder.build();
        }

        void addQuest(Quest quest) {
            poolBuilder.addQuest(tier, quest);
        }
    }

    public class QuestBuilder {
        private final TierBuilder tierBuilder;
        private final ResourceLocation id;
        private Optional<String> requiredMod = Optional.empty();
        private Optional<Component> name = Optional.empty();
        private Optional<Component> description = Optional.empty();
        private final List<QuestTask> tasks = new ArrayList<>();
        private final List<QuestRequirement> requirements = new ArrayList<>();
        private final List<QuestReward> rewards = new ArrayList<>();
        private final List<com.qeapi.quest.reward.RewardChoicePool> rewardChoicePools = new ArrayList<>();
        private int weight = 100;
        private Optional<Integer> repeatAfterDays = Optional.empty();
        private boolean shuffleRefreshingQuests = false;
        private Optional<String> questGroup = Optional.empty();
        private Optional<String> questLine = Optional.empty();
        private Optional<Boolean> followQuestOrderOverride = Optional.empty();
        private Quest.TaskMode taskMode = Quest.TaskMode.ALL;
        private int decisionCount = 1;
        private final List<com.qeapi.quest.TaskChoiceGroup> taskChoiceGroups = new ArrayList<>();
        private Optional<ResourceLocation> acceptQuestSoundOverride = Optional.empty();
        private Optional<ResourceLocation> finishQuestSoundOverride = Optional.empty();

        QuestBuilder(TierBuilder tierBuilder, ResourceLocation id) {
            this.tierBuilder = tierBuilder;
            this.id = id;
        }

        public QuestBuilder requiredMod(String modId) {
            this.requiredMod = Optional.of(modId);
            return this;
        }

        public QuestBuilder name(Component name) {
            this.name = Optional.of(name);
            return this;
        }

        // registers against the default translation key instead of setting a component directly, so the generated JSON has no quest_name field
        public QuestBuilder name(String literalText) {
            LangEntries.add(Quest.defaultNameKey(id), literalText);
            return this;
        }

        public QuestBuilder description(Component description) {
            this.description = Optional.of(description);
            return this;
        }

        // see name(String)
        public QuestBuilder description(String literalText) {
            LangEntries.add(Quest.defaultDescriptionKey(id), literalText);
            return this;
        }

        public QuestBuilder task(QuestTask task) {
            this.tasks.add(task);
            return this;
        }

        // overrides a choice_group's requiredCount above its default of 1 - membership itself comes from each task's own .choiceGroup(String) call
        public QuestBuilder taskChoiceGroup(String groupId, int requiredCount) {
            this.taskChoiceGroups.add(new com.qeapi.quest.TaskChoiceGroup(groupId, requiredCount));
            return this;
        }

        public QuestBuilder requirement(QuestRequirement requirement) {
            this.requirements.add(requirement);
            return this;
        }

        public QuestBuilder reward(QuestReward reward) {
            this.rewards.add(reward);
            return this;
        }

        public QuestBuilder rewardChoicePool(int pick, QuestReward... options) {
            this.rewardChoicePools.add(new com.qeapi.quest.reward.RewardChoicePool(
                    Arrays.stream(options).map(com.qeapi.quest.reward.RewardChoicePool.Option::of).toList(), pick));
            return this;
        }

        // per-option variant, for when one or more choices should only show up while a given mod is loaded
        public QuestBuilder rewardChoicePoolOptions(int pick, com.qeapi.quest.reward.RewardChoicePool.Option... options) {
            this.rewardChoicePools.add(new com.qeapi.quest.reward.RewardChoicePool(List.of(options), pick));
            return this;
        }

        public QuestBuilder weight(int weight) {
            this.weight = weight;
            return this;
        }

        // not calling this means once completed, it's done forever
        public QuestBuilder repeatAfterDays(int days) {
            this.repeatAfterDays = Optional.of(days);
            return this;
        }

        public QuestBuilder repeatable() {
            return repeatAfterDays(Quest.DEFAULT_REPEAT_AFTER_DAYS);
        }

        // on refresh, offer a different weighted-random repeatable quest from the same tier instead of always re-offering this one
        public QuestBuilder shuffleRefreshingQuests() {
            this.shuffleRefreshingQuests = true;
            return this;
        }

        // not calling this means the quest is offered regardless of any chosen group
        public QuestBuilder questGroup(String group) {
            this.questGroup = Optional.of(group);
            return this;
        }

        // Marks this quest as one step of a named quest line - see Quest.Builder.questLine.
        public QuestBuilder questLine(String lineId) {
            this.questLine = Optional.of(lineId);
            return this;
        }

        // needed by a quest_line step gated by the questLine filter instead of tier order, or it would deadlock behind its own root
        public QuestBuilder followQuestOrder(boolean follow) {
            this.followQuestOrderOverride = Optional.of(follow);
            return this;
        }

        public QuestBuilder taskMode(Quest.TaskMode mode) {
            this.taskMode = mode;
            return this;
        }

        // shorthand for taskMode(ORDER) - tasks unlock in task_order sequence
        public QuestBuilder ordered() {
            return taskMode(Quest.TaskMode.ORDER);
        }

        // shorthand for taskMode(DECISION) - the whole tasks list becomes one implicit choice group; use taskChoiceGroup(...) for partial/multi-group cases
        public QuestBuilder decision(int requiredCount) {
            this.decisionCount = requiredCount;
            return taskMode(Quest.TaskMode.DECISION);
        }

        public QuestBuilder decision() {
            return decision(1);
        }

        public QuestBuilder acceptQuestSoundOverride(ResourceLocation soundId) {
            this.acceptQuestSoundOverride = Optional.of(soundId);
            return this;
        }

        public QuestBuilder finishQuestSoundOverride(ResourceLocation soundId) {
            this.finishQuestSoundOverride = Optional.of(soundId);
            return this;
        }

        public TierBuilder add() {
            boolean followOrder = followQuestOrderOverride.orElse(tierBuilder.poolBuilder.followOrder);
            List<com.qeapi.quest.reward.RewardEntry> rewardEntries = new ArrayList<>();
            for (QuestReward reward : rewards) {
                rewardEntries.add(new com.qeapi.quest.reward.RewardEntry.All(reward));
            }
            for (com.qeapi.quest.reward.RewardChoicePool pool : rewardChoicePools) {
                rewardEntries.add(new com.qeapi.quest.reward.RewardEntry.Choice(pool));
            }
            Quest quest = new Quest(id, tierBuilder.tier, requiredMod, followOrder, taskMode, decisionCount,
                    List.copyOf(taskChoiceGroups), name, description, requirements, tasks, rewardEntries,
                    weight, repeatAfterDays, shuffleRefreshingQuests, questGroup, questLine,
                    acceptQuestSoundOverride, finishQuestSoundOverride);
            tierBuilder.addQuest(quest);
            return tierBuilder;
        }
    }
}

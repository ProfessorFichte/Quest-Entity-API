package com.qeapi.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.qeapi.QuestEntityAPI;
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
import net.minecraft.world.level.biome.Biome;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

// extend this and implement addQuests() with the builder API below to generate quest JSON for a mod
public abstract class QuestProvider implements DataProvider {

    protected final PackOutput output;
    protected final String modId;
    private final Map<ResourceLocation, QuestPool> pools = new HashMap<>();

    protected QuestProvider(PackOutput output, String modId) {
        this.output = output;
        this.modId = modId;
    }

    protected abstract void addQuests();

    // re-runs addQuests() to repopulate pools - exposed so LangProvider can trigger the same
    // literal-text registration on LangEntries without relying on provider registration order
    public void collectQuests() {
        pools.clear();
        addQuests();
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
                        .resultOrPartial(error -> QuestEntityAPI.LOGGER.error("Failed to encode quest {}: {}", quest.id(), error))
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
            tagJson.add("values", values);

            futures.add(DataProvider.saveStable(cache, tagJson, tagPath));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Quest Entity API Quests: " + modId;
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
    // (see FindStructureTask.ofWithMap for the one-time guarantee)
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

    // like findStructure(String), but also grants a one-time treasure map on accept - never
    // re-granted, even across decline/re-accept cycles (see PlayerQuestData.hasMapBeenGranted)
    protected FindStructureTask findStructureWithMap(String structureId) {
        return FindStructureTask.ofWithMap(structureId);
    }

    protected FindStructureTask findStructureWithMap(ResourceLocation structureId) {
        return FindStructureTask.ofWithMap(structureId);
    }

    protected FindStructureTask findStructure(String structureId, ResourceLocation textureId) {
        return FindStructureTask.of(ResourceLocation.parse(structureId), textureId);
    }

    protected FindStructureTask findStructure(ResourceLocation structureId, ResourceLocation textureId) {
        return FindStructureTask.of(structureId, textureId);
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

    protected BlocksTraveledTask blocksTraveled(int distance) {
        return BlocksTraveledTask.of(distance);
    }

    protected ItemUsedTask itemUsed(Item item, int amount) {
        return ItemUsedTask.builder()
                .item(item)
                .amount(amount)
                .build();
    }

    protected BrewPotionTask brewPotion(net.minecraft.world.item.alchemy.Potion potion, int amount) {
        return BrewPotionTask.builder()
                .potion(potion)
                .amount(amount)
                .build();
    }

    protected MineBlockTask mineBlock(net.minecraft.world.level.block.Block block, int amount) {
        return MineBlockTask.builder()
                .block(block)
                .amount(amount)
                .build();
    }

    protected MineBlockTask mineBlockTag(TagKey<net.minecraft.world.level.block.Block> blockTag, int amount) {
        return MineBlockTask.builder()
                .blockTag(blockTag)
                .amount(amount)
                .build();
    }

    // Spell Engine integration tasks
    protected SpellCastTask spellCast(ResourceLocation spellId, int amount) {
        return SpellCastTask.builder()
                .spellId(spellId)
                .amount(amount)
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

    // ==================== Requirement Helpers ====================

    protected HasAdvancementRequirement hasAdvancement(String advancementId) {
        return HasAdvancementRequirement.of(advancementId);
    }

    protected HasAdvancementRequirement hasAdvancement(ResourceLocation advancementId) {
        return HasAdvancementRequirement.of(advancementId);
    }

    protected HasLevelRequirement hasLevel(int level) {
        return HasLevelRequirement.of(level);
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

    // LevelZ integration - skillId is LevelZ's skill key, e.g. "melee", "mining"
    protected HasLevelZSkillRequirement hasLevelZSkill(String skillId, int level) {
        return HasLevelZSkillRequirement.of(skillId, level);
    }

    // ==================== Reward Helpers ====================

    protected ExperienceReward experience(int amount) {
        return ExperienceReward.of(amount);
    }

    // usually one option among several in rewardChoicePool(...), so picking it commits the player to
    // that group (see Quest.questGroup/QuestBuilder.questGroup). icon is required here, unlike
    // skillExperience/skillLevel's optional one - see SetQuestGroupReward.
    protected SetQuestGroupReward setQuestGroup(String group, ResourceLocation icon) {
        return new SetQuestGroupReward(group, icon);
    }

    protected ItemReward item(Item item, int amount) {
        return ItemReward.builder()
                .item(item)
                .amount(amount)
                .build();
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

    protected AdvancementReward advancement(String advancementId) {
        return AdvancementReward.of(advancementId);
    }

    protected AdvancementReward advancement(ResourceLocation advancementId) {
        return AdvancementReward.of(advancementId);
    }

    protected LootTableReward lootTable(String lootTableId) {
        return LootTableReward.of(lootTableId);
    }

    protected LootTableReward lootTable(ResourceLocation lootTableId) {
        return LootTableReward.of(lootTableId);
    }

    protected StatusEffectReward statusEffect(Holder<MobEffect> effect, int durationTicks, int amplifier) {
        return StatusEffectReward.of(effect, durationTicks, amplifier);
    }

    protected StatusEffectReward statusEffect(String effectId, int durationTicks, int amplifier) {
        return StatusEffectReward.of(effectId, durationTicks, amplifier);
    }

    // Pufferfish's Skills integration - grants raw experience in a skill tree
    protected SkillExperienceReward skillExperience(ResourceLocation skillTreeId, int amount) {
        return new SkillExperienceReward(skillTreeId, amount);
    }

    protected SkillExperienceReward skillExperience(String skillTreeId, int amount) {
        return new SkillExperienceReward(ResourceLocation.parse(skillTreeId), amount);
    }

    // same as above, with a custom icon (e.g. the skill tree's own category icon) in the quest GUI
    protected SkillExperienceReward skillExperience(ResourceLocation skillTreeId, int amount, ResourceLocation icon) {
        return new SkillExperienceReward(skillTreeId, amount, java.util.Optional.of(icon));
    }

    // Pufferfish's Skills integration - grants whole skill-tree levels
    protected SkillLevelReward skillLevel(ResourceLocation skillTreeId, int levels) {
        return new SkillLevelReward(skillTreeId, levels);
    }

    protected SkillLevelReward skillLevel(String skillTreeId, int levels) {
        return new SkillLevelReward(ResourceLocation.parse(skillTreeId), levels);
    }

    // same as above, with a custom icon in the quest GUI
    protected SkillLevelReward skillLevel(ResourceLocation skillTreeId, int levels, ResourceLocation icon) {
        return new SkillLevelReward(skillTreeId, levels, java.util.Optional.of(icon));
    }

    // LevelZ integration - grants whole levels in a LevelZ skill; icon is always the skill's own
    // sprite (LevelZCompat.skillIcon), so unlike the Pufferfish rewards above there's no icon param
    protected LevelZSkillLevelReward levelZSkillLevel(String skillId, int levels) {
        return new LevelZSkillLevelReward(skillId, levels);
    }

    // Spell Engine integration - random spell from a pool (tag), within the tier range (inclusive)
    protected SpellScrollReward spellScrollFromPool(ResourceLocation pool, int tierMin, int tierMax) {
        return SpellScrollReward.builder().pool(pool).tierRange(tierMin, tierMax).build();
    }

    // one exact spell, bypassing random selection entirely
    protected SpellScrollReward spellScroll(ResourceLocation spellId) {
        return SpellScrollReward.builder().spellId(spellId).build();
    }

    // Target-item rewards - the player picks which of their own inventory items each of these
    // applies to, in the quest GUI's item-picker overlay (see TargetItemReward).

    protected EnchantRandomlyReward enchantRandomly(int levelCap) {
        return new EnchantRandomlyReward(levelCap);
    }

    protected EnchantSpecificReward enchantSpecific(ResourceLocation enchantmentId, int level) {
        return new EnchantSpecificReward(enchantmentId, level);
    }

    protected EnchantSpecificReward enchantSpecific(String enchantmentId, int level) {
        return new EnchantSpecificReward(ResourceLocation.parse(enchantmentId), level);
    }

    protected RepairItemReward repairItem() {
        return new RepairItemReward();
    }

    // Spell Engine integration - binds one specific spell onto the chosen item (making it a spell
    // container if it isn't one yet, keeping any spells already bound)
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

    // Dungeon Difficulty integration - raises the chosen item's existing power level by amount, up to cap
    protected IncreasePowerLevelReward increasePowerLevel(int amount, int cap) {
        return new IncreasePowerLevelReward(amount, cap);
    }

    // Enchant Limiter integration - grants the chosen item extra enchantment slots, up to cap
    protected IncreaseEnchantSlotsReward increaseEnchantSlots(int amount, int cap) {
        return new IncreaseEnchantSlotsReward(amount, cap);
    }

    // Bundles any of the above target-item operations onto one player-chosen item, claimed with a
    // single item pick instead of one per reward
    protected EnhanceItemReward enhanceItem(EnhanceOperation... operations) {
        return new EnhanceItemReward(List.of(operations));
    }

    // ==================== Builder Classes ====================

    public class PoolBuilder {
        private final ResourceLocation id;
        private boolean followOrder = true;
        private final Map<Integer, List<Quest>> tiers = new HashMap<>();

        PoolBuilder(ResourceLocation id) {
            this.id = id;
        }

        public PoolBuilder followOrder(boolean follow) {
            this.followOrder = follow;
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
        private Optional<String> questGroup = Optional.empty();

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

        // Registers literal text against this quest's default translation key (Quest.defaultNameKey)
        // for LangProvider to pick up, rather than setting a component directly - so the generated
        // quest JSON has no quest_name field and falls back to that key at runtime, same as a quest
        // that never specified one.
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

        public QuestBuilder requirement(QuestRequirement requirement) {
            this.requirements.add(requirement);
            return this;
        }

        public QuestBuilder reward(QuestReward reward) {
            this.rewards.add(reward);
            return this;
        }

        public QuestBuilder rewardChoicePool(int pick, QuestReward... options) {
            this.rewardChoicePools.add(new com.qeapi.quest.reward.RewardChoicePool(List.of(options), pick));
            return this;
        }

        public QuestBuilder weight(int weight) {
            this.weight = weight;
            return this;
        }

        // Quest becomes acceptable again this many in-game days after it was last completed -
        // not calling this means once completed, it's done forever.
        public QuestBuilder repeatAfterDays(int days) {
            this.repeatAfterDays = Optional.of(days);
            return this;
        }

        public QuestBuilder repeatable() {
            return repeatAfterDays(Quest.DEFAULT_REPEAT_AFTER_DAYS);
        }

        // Only offered to a player who's chosen this group for the pool - see SetQuestGroupReward.
        // Not calling this means the quest is offered regardless of any chosen group.
        public QuestBuilder questGroup(String group) {
            this.questGroup = Optional.of(group);
            return this;
        }

        public TierBuilder add() {
            Quest quest = new Quest(id, tierBuilder.tier, requiredMod, tierBuilder.poolBuilder.followOrder,
                    name, description, requirements, tasks, rewards, List.copyOf(rewardChoicePools), weight,
                    repeatAfterDays, questGroup);
            tierBuilder.addQuest(quest);
            return tierBuilder;
        }
    }
}

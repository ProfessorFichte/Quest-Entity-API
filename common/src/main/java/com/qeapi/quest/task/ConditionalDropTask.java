package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.LocationMatchUtil;
import com.qeapi.util.TextMutator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Grants item_id/quest_item on a matching mob kill and/or a matching chest-loot resolution,
// capped at amount (tracked via the same QuestProgress machinery every other task uses). Mob
// targeting and chest targeting are each fully optional and independent - a quest can use either,
// both, or neither block, though a task with neither never actually grants anything.
public record ConditionalDropTask(
        Optional<ResourceLocation> itemId,
        Optional<QuestItemDefinition> questItem,
        int amount,
        Optional<ResourceLocation> entityId,
        Optional<TagKey<EntityType<?>>> entityTag,
        List<ResourceLocation> entityIds,
        List<ResourceLocation> damageTypes,
        Optional<ResourceLocation> inStructure,
        Optional<ResourceLocation> inBiome,
        Optional<TagKey<Biome>> inBiomeTag,
        Optional<ResourceLocation> inDimension,
        double mobDropChance,
        List<ResourceLocation> lootTableIds,
        List<ResourceLocation> chestInStructures,
        double chestDropChance,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<ConditionalDropTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("item_id").forGetter(ConditionalDropTask::itemId),
                    QuestItemDefinition.CODEC.optionalFieldOf("quest_item").forGetter(ConditionalDropTask::questItem),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(ConditionalDropTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("entity_id").forGetter(ConditionalDropTask::entityId),
                    TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("entity_tag").forGetter(ConditionalDropTask::entityTag),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("entity_ids", List.of()).forGetter(ConditionalDropTask::entityIds),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(ConditionalDropTask::damageTypes),
                    ResourceLocation.CODEC.optionalFieldOf("in_structure").forGetter(ConditionalDropTask::inStructure),
                    ResourceLocation.CODEC.optionalFieldOf("in_biome").forGetter(ConditionalDropTask::inBiome),
                    TagKey.codec(Registries.BIOME).optionalFieldOf("in_biome_tag").forGetter(ConditionalDropTask::inBiomeTag),
                    ResourceLocation.CODEC.optionalFieldOf("in_dimension").forGetter(ConditionalDropTask::inDimension),
                    Codec.doubleRange(0.0, 1.0).optionalFieldOf("mob_drop_chance", 1.0).forGetter(ConditionalDropTask::mobDropChance),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("loot_table_ids", List.of()).forGetter(ConditionalDropTask::lootTableIds),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("chest_in_structures", List.of()).forGetter(ConditionalDropTask::chestInStructures),
                    Codec.doubleRange(0.0, 1.0).optionalFieldOf("chest_drop_chance", 1.0).forGetter(ConditionalDropTask::chestDropChance),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(ConditionalDropTask::textureOverrideId)
            ).apply(instance, ConditionalDropTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("conditional_drop");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "item_amount", String.valueOf(amount),
                        "item_name", getItemDisplayName(),
                        "current_found", String.valueOf(current),
                        "mob_drop_chance", formatChance(mobDropChance),
                        "chest_drop_chance", formatChance(chestDropChance)
                )
        );
    }

    private static String formatChance(double chance) {
        return Math.round(chance * 100) + "%";
    }

    public String getItemDisplayName() {
        if (itemId.isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(itemId.get());
            return item != null ? item.getDescription().getString() : itemId.get().toString();
        }
        return questItem.map(def -> def.name().getString()).orElse("item");
    }

    public ItemStack getDisplayStack() {
        return createGrantStack(1);
    }

    public ItemStack createGrantStack(int count) {
        if (questItem.isPresent()) {
            return questItem.get().createStack(count);
        }
        return itemId.map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id), count)).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.conditional_drop";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // False whenever no mob-targeting selector is configured at all - an empty selector matches
    // nothing here, unlike EntityKillTask where at least one is mandatory.
    public boolean matchesKill(LivingEntity killed, DamageSource source, Level level) {
        if (entityId.isEmpty() && entityTag.isEmpty() && entityIds.isEmpty()) {
            return false;
        }

        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());

        if (entityId.isPresent() && !killedId.equals(entityId.get())) {
            return false;
        }

        if (!entityIds.isEmpty() && entityIds.stream().noneMatch(id -> killedId.equals(id))) {
            return false;
        }

        if (entityTag.isPresent() && !killed.getType().is(entityTag.get())) {
            return false;
        }

        if (!damageTypes.isEmpty()) {
            ResourceLocation sourceTypeId = source.typeHolder().unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);
            if (sourceTypeId == null || damageTypes.stream().noneMatch(requiredType -> requiredType.equals(sourceTypeId))) {
                return false;
            }
        }

        if (inDimension.isPresent() && !level.dimension().location().equals(inDimension.get())) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = killed.blockPosition();

            if (inBiome.isPresent() && !LocationMatchUtil.isInBiome(serverLevel, pos, inBiome.get())) {
                return false;
            }
            if (inBiomeTag.isPresent() && !LocationMatchUtil.isInBiomeTag(serverLevel, pos, inBiomeTag.get())) {
                return false;
            }
            if (inStructure.isPresent() && !LocationMatchUtil.isInStructure(serverLevel, pos, inStructure.get())) {
                return false;
            }
        }

        return true;
    }

    // False whenever neither loot_table_ids nor chest_in_structures is configured, same "at least
    // one selector required" contract as matchesKill. originPos may be null (e.g. loot contexts
    // that don't carry LootContextParams.ORIGIN, like fishing) - treated as "can't check
    // chest_in_structures" rather than throwing, so it simply fails to match.
    public boolean matchesChestLoot(ResourceLocation resolvedLootTableId, ServerLevel level, BlockPos originPos) {
        if (lootTableIds.isEmpty() && chestInStructures.isEmpty()) {
            return false;
        }

        if (!lootTableIds.isEmpty() && !lootTableIds.contains(resolvedLootTableId)) {
            return false;
        }

        if (!chestInStructures.isEmpty()) {
            if (level == null || originPos == null
                    || chestInStructures.stream().noneMatch(structure -> LocationMatchUtil.isInStructure(level, originPos, structure))) {
                return false;
            }
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> itemId = Optional.empty();
        private Optional<QuestItemDefinition> questItem = Optional.empty();
        private int amount = 1;
        private Optional<ResourceLocation> entityId = Optional.empty();
        private Optional<TagKey<EntityType<?>>> entityTag = Optional.empty();
        private List<ResourceLocation> entityIds = List.of();
        private List<ResourceLocation> damageTypes = List.of();
        private Optional<ResourceLocation> inStructure = Optional.empty();
        private Optional<ResourceLocation> inBiome = Optional.empty();
        private Optional<TagKey<Biome>> inBiomeTag = Optional.empty();
        private Optional<ResourceLocation> inDimension = Optional.empty();
        private double mobDropChance = 1.0;
        private List<ResourceLocation> lootTableIds = List.of();
        private List<ResourceLocation> chestInStructures = List.of();
        private double chestDropChance = 1.0;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder itemId(ResourceLocation id) {
            this.itemId = Optional.of(id);
            return this;
        }

        public Builder itemId(String id) {
            return itemId(ResourceLocation.parse(id));
        }

        public Builder item(Item item) {
            return itemId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder questItem(QuestItemDefinition definition) {
            this.questItem = Optional.of(definition);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder entityId(ResourceLocation id) {
            this.entityId = Optional.of(id);
            return this;
        }

        public Builder entityId(String id) {
            return entityId(ResourceLocation.parse(id));
        }

        public Builder entityTag(TagKey<EntityType<?>> tag) {
            this.entityTag = Optional.of(tag);
            return this;
        }

        public Builder entityIds(ResourceLocation... ids) {
            this.entityIds = List.of(ids);
            return this;
        }

        public Builder entityIds(String... ids) {
            this.entityIds = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
            return this;
        }

        public Builder damageTypes(ResourceLocation... types) {
            this.damageTypes = List.of(types);
            return this;
        }

        public Builder inStructure(ResourceLocation structure) {
            this.inStructure = Optional.of(structure);
            return this;
        }

        public Builder inStructure(String structure) {
            return inStructure(ResourceLocation.parse(structure));
        }

        public Builder inBiome(ResourceLocation biome) {
            this.inBiome = Optional.of(biome);
            return this;
        }

        public Builder inBiomeTag(TagKey<Biome> biomeTag) {
            this.inBiomeTag = Optional.of(biomeTag);
            return this;
        }

        public Builder inDimension(ResourceLocation dimension) {
            this.inDimension = Optional.of(dimension);
            return this;
        }

        public Builder mobDropChance(double chance) {
            this.mobDropChance = chance;
            return this;
        }

        public Builder lootTableIds(ResourceLocation... ids) {
            this.lootTableIds = List.of(ids);
            return this;
        }

        public Builder lootTableIds(String... ids) {
            this.lootTableIds = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
            return this;
        }

        public Builder chestInStructures(ResourceLocation... structures) {
            this.chestInStructures = List.of(structures);
            return this;
        }

        public Builder chestInStructures(String... structures) {
            this.chestInStructures = java.util.Arrays.stream(structures).map(ResourceLocation::parse).toList();
            return this;
        }

        public Builder chestDropChance(double chance) {
            this.chestDropChance = chance;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public ConditionalDropTask build() {
            if (itemId.isEmpty() == questItem.isEmpty()) {
                throw new IllegalStateException("ConditionalDropTask requires exactly one of itemId or questItem");
            }
            return new ConditionalDropTask(itemId, questItem, amount, entityId, entityTag, entityIds, damageTypes,
                    inStructure, inBiome, inBiomeTag, inDimension, mobDropChance, lootTableIds, chestInStructures,
                    chestDropChance, textureOverrideId);
        }
    }
}

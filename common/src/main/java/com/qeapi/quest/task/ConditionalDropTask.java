package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.FlexibleListCodec;
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

// mob-kill and chest-loot targeting are independent (a quest can use either, both, or neither), so they're kept in separate nested filter objects rather than one shared bag
public record ConditionalDropTask(
        Optional<ResourceLocation> itemId,
        Optional<QuestItemDefinition> questItem,
        int amount,
        MobDropFilters mobDropFilters,
        ChestLootFilters chestLootFilters,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public record MobDropFilters(
            List<ResourceLocation> entityIds,
            List<TagKey<EntityType<?>>> entityTags,
            List<ResourceLocation> damageTypes,
            Optional<ResourceLocation> inStructure,
            Optional<ResourceLocation> inBiome,
            Optional<TagKey<Biome>> inBiomeTag,
            Optional<ResourceLocation> inDimension,
            double dropChance
    ) {
        public static final MobDropFilters EMPTY = new MobDropFilters(List.of(), List.of(),
                List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1.0);

        public static final Codec<MobDropFilters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("entity_ids", List.of()).forGetter(MobDropFilters::entityIds),
                        FlexibleListCodec.listOrSingle(TagKey.codec(Registries.ENTITY_TYPE)).optionalFieldOf("entity_tags", List.of()).forGetter(MobDropFilters::entityTags),
                        ResourceLocation.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(MobDropFilters::damageTypes),
                        ResourceLocation.CODEC.optionalFieldOf("in_structure").forGetter(MobDropFilters::inStructure),
                        ResourceLocation.CODEC.optionalFieldOf("in_biome").forGetter(MobDropFilters::inBiome),
                        TagKey.codec(Registries.BIOME).optionalFieldOf("in_biome_tag").forGetter(MobDropFilters::inBiomeTag),
                        ResourceLocation.CODEC.optionalFieldOf("in_dimension").forGetter(MobDropFilters::inDimension),
                        Codec.doubleRange(0.0, 1.0).optionalFieldOf("drop_chance", 1.0).forGetter(MobDropFilters::dropChance)
                ).apply(instance, MobDropFilters::new)
        );
    }

    public record ChestLootFilters(
            List<ResourceLocation> lootTableIds,
            List<ResourceLocation> chestInStructures,
            double dropChance
    ) {
        public static final ChestLootFilters EMPTY = new ChestLootFilters(List.of(), List.of(), 1.0);

        public static final Codec<ChestLootFilters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.listOf().optionalFieldOf("loot_table_ids", List.of()).forGetter(ChestLootFilters::lootTableIds),
                        ResourceLocation.CODEC.listOf().optionalFieldOf("chest_in_structures", List.of()).forGetter(ChestLootFilters::chestInStructures),
                        Codec.doubleRange(0.0, 1.0).optionalFieldOf("drop_chance", 1.0).forGetter(ChestLootFilters::dropChance)
                ).apply(instance, ChestLootFilters::new)
        );
    }

    public static final MapCodec<ConditionalDropTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("item_id").forGetter(ConditionalDropTask::itemId),
                    QuestItemDefinition.CODEC.optionalFieldOf("quest_item").forGetter(ConditionalDropTask::questItem),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(ConditionalDropTask::amount),
                    MobDropFilters.CODEC.optionalFieldOf("mob_drop_filters", MobDropFilters.EMPTY).forGetter(ConditionalDropTask::mobDropFilters),
                    ChestLootFilters.CODEC.optionalFieldOf("chest_loot_filters", ChestLootFilters.EMPTY).forGetter(ConditionalDropTask::chestLootFilters),
                    Codec.INT.optionalFieldOf("task_order").forGetter(ConditionalDropTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(ConditionalDropTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(ConditionalDropTask::textureOverrideId)
            ).apply(instance, ConditionalDropTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("conditional_drop");
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
                        "mob_drop_chance", formatChance(mobDropFilters.dropChance()),
                        "chest_drop_chance", formatChance(chestLootFilters.dropChance())
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
        return "task.quest_api.conditional_drop";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // unlike EntityKillTask, an empty selector here matches nothing rather than being mandatory
    public boolean matchesKill(LivingEntity killed, DamageSource source, Level level) {
        if (mobDropFilters.entityIds().isEmpty() && mobDropFilters.entityTags().isEmpty()) {
            return false;
        }

        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());

        if (!mobDropFilters.entityIds().isEmpty() && mobDropFilters.entityIds().stream().noneMatch(id -> killedId.equals(id))) {
            return false;
        }

        if (!mobDropFilters.entityTags().isEmpty() && mobDropFilters.entityTags().stream().noneMatch(tag -> killed.getType().is(tag))) {
            return false;
        }

        if (!mobDropFilters.damageTypes().isEmpty()) {
            ResourceLocation sourceTypeId = source.typeHolder().unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);
            if (sourceTypeId == null || mobDropFilters.damageTypes().stream().noneMatch(requiredType -> requiredType.equals(sourceTypeId))) {
                return false;
            }
        }

        if (mobDropFilters.inDimension().isPresent() && !level.dimension().location().equals(mobDropFilters.inDimension().get())) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = killed.blockPosition();

            if (mobDropFilters.inBiome().isPresent() && !LocationMatchUtil.isInBiome(serverLevel, pos, mobDropFilters.inBiome().get())) {
                return false;
            }
            if (mobDropFilters.inBiomeTag().isPresent() && !LocationMatchUtil.isInBiomeTag(serverLevel, pos, mobDropFilters.inBiomeTag().get())) {
                return false;
            }
            if (mobDropFilters.inStructure().isPresent() && !LocationMatchUtil.isInStructure(serverLevel, pos, mobDropFilters.inStructure().get())) {
                return false;
            }
        }

        return true;
    }

    // originPos can be null for loot contexts without LootContextParams.ORIGIN (e.g. fishing) - treated as a non-match rather than thrown
    public boolean matchesChestLoot(ResourceLocation resolvedLootTableId, ServerLevel level, BlockPos originPos) {
        if (chestLootFilters.lootTableIds().isEmpty() && chestLootFilters.chestInStructures().isEmpty()) {
            return false;
        }

        if (!chestLootFilters.lootTableIds().isEmpty() && !chestLootFilters.lootTableIds().contains(resolvedLootTableId)) {
            return false;
        }

        if (!chestLootFilters.chestInStructures().isEmpty()) {
            if (level == null || originPos == null
                    || chestLootFilters.chestInStructures().stream().noneMatch(structure -> LocationMatchUtil.isInStructure(level, originPos, structure))) {
                return false;
            }
        }

        return true;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (questItem.isPresent()) {
            return questItem.get().matches(stack);
        }

        ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return stackItemId.equals(itemId.orElse(null));
    }

    public int countMatchingItems(Iterable<ItemStack> inventory) {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> itemId = Optional.empty();
        private Optional<QuestItemDefinition> questItem = Optional.empty();
        private int amount = 1;
        private List<ResourceLocation> entityIds = List.of();
        private List<TagKey<EntityType<?>>> entityTags = List.of();
        private List<ResourceLocation> damageTypes = List.of();
        private Optional<ResourceLocation> inStructure = Optional.empty();
        private Optional<ResourceLocation> inBiome = Optional.empty();
        private Optional<TagKey<Biome>> inBiomeTag = Optional.empty();
        private Optional<ResourceLocation> inDimension = Optional.empty();
        private double mobDropChance = 1.0;
        private List<ResourceLocation> lootTableIds = List.of();
        private List<ResourceLocation> chestInStructures = List.of();
        private double chestDropChance = 1.0;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
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
            return entityIds(id);
        }

        public Builder entityId(String id) {
            return entityId(ResourceLocation.parse(id));
        }

        public Builder entityTag(TagKey<EntityType<?>> tag) {
            return entityTags(tag);
        }

        @SafeVarargs
        public final Builder entityTags(TagKey<EntityType<?>>... tags) {
            this.entityTags = List.of(tags);
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

        public Builder taskOrder(int order) {
            this.taskOrder = Optional.of(order);
            return this;
        }

        public Builder choiceGroup(String groupId) {
            this.choiceGroup = Optional.of(groupId);
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
            MobDropFilters mobDropFilters = new MobDropFilters(entityIds, entityTags, damageTypes,
                    inStructure, inBiome, inBiomeTag, inDimension, mobDropChance);
            ChestLootFilters chestLootFilters = new ChestLootFilters(lootTableIds, chestInStructures, chestDropChance);
            return new ConditionalDropTask(itemId, questItem, amount, mobDropFilters, chestLootFilters,
                    taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

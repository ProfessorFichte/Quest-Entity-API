package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.FlexibleListCodec;
import com.qeapi.util.LocationMatchUtil;
import com.qeapi.util.TextMutator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// narrowing conditions live in the nested Filters, keeping JSON's core "what to kill, how many" separate from its optional gates
public record EntityKillTask(
        List<ResourceLocation> entityIds,
        List<TagKey<EntityType<?>>> entityTags,
        int amount,
        Filters filters,
        boolean providesMap,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public record Filters(
            List<ResourceLocation> damageTypes,
            Optional<ResourceLocation> inStructure,
            Optional<ResourceLocation> inBiome,
            Optional<TagKey<Biome>> inBiomeTag,
            Optional<ResourceLocation> inDimension,
            List<ResourceLocation> inSpellIds,
            List<ResourceLocation> inSpellPools,
            List<ResourceLocation> inSpellSchools,
            Optional<Integer> minPowerLevel,
            Optional<Double> minRange,
            Optional<Double> maxRange,
            Optional<ResourceLocation> requiredItemId,
            Optional<TagKey<Item>> requiredItemTag,
            Optional<ResourceLocation> requiredEffectOnKilled,
            Optional<ResourceLocation> requiredEffectOnKiller,
            ResourceLocation requiredAttributeId,
            Optional<Double> minAttributeValue,
            Optional<Double> maxAttributeValue
    ) {
        public static final ResourceLocation DEFAULT_ATTRIBUTE_ID = ResourceLocation.withDefaultNamespace("max_health");

        public static final Filters EMPTY = new Filters(List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), DEFAULT_ATTRIBUTE_ID, Optional.empty(), Optional.empty());

        // RecordCodecBuilder's group()/apply() tops out at 16 args; nested here to fit the rest.
        private record ExtraFilters(
                Optional<Double> minRange,
                Optional<Double> maxRange,
                Optional<ResourceLocation> requiredItemId,
                Optional<TagKey<Item>> requiredItemTag,
                Optional<ResourceLocation> requiredEffectOnKilled,
                Optional<ResourceLocation> requiredEffectOnKiller,
                ResourceLocation requiredAttributeId,
                Optional<Double> minAttributeValue,
                Optional<Double> maxAttributeValue
        ) {}

        public static final Codec<Filters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(Filters::damageTypes),
                        ResourceLocation.CODEC.optionalFieldOf("in_structure").forGetter(Filters::inStructure),
                        ResourceLocation.CODEC.optionalFieldOf("in_biome").forGetter(Filters::inBiome),
                        TagKey.codec(Registries.BIOME).optionalFieldOf("in_biome_tag").forGetter(Filters::inBiomeTag),
                        ResourceLocation.CODEC.optionalFieldOf("in_dimension").forGetter(Filters::inDimension),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_ids", List.of()).forGetter(Filters::inSpellIds),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_pools", List.of()).forGetter(Filters::inSpellPools),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_schools", List.of()).forGetter(Filters::inSpellSchools),
                        Codec.INT.optionalFieldOf("min_power_level").forGetter(Filters::minPowerLevel),
                        instance.group(
                                Codec.DOUBLE.optionalFieldOf("min_range").forGetter(Filters::minRange),
                                Codec.DOUBLE.optionalFieldOf("max_range").forGetter(Filters::maxRange),
                                ResourceLocation.CODEC.optionalFieldOf("required_item_id").forGetter(Filters::requiredItemId),
                                TagKey.codec(Registries.ITEM).optionalFieldOf("required_item_tag").forGetter(Filters::requiredItemTag),
                                ResourceLocation.CODEC.optionalFieldOf("required_effect_on_killed").forGetter(Filters::requiredEffectOnKilled),
                                ResourceLocation.CODEC.optionalFieldOf("required_effect_on_killer").forGetter(Filters::requiredEffectOnKiller),
                                ResourceLocation.CODEC.optionalFieldOf("required_attribute_id", DEFAULT_ATTRIBUTE_ID).forGetter(Filters::requiredAttributeId),
                                Codec.DOUBLE.optionalFieldOf("min_attribute_value").forGetter(Filters::minAttributeValue),
                                Codec.DOUBLE.optionalFieldOf("max_attribute_value").forGetter(Filters::maxAttributeValue)
                        ).apply(instance, ExtraFilters::new)
                ).apply(instance, (damageTypes, inStructure, inBiome, inBiomeTag, inDimension, inSpellIds,
                        inSpellPools, inSpellSchools, minPowerLevel, extra) ->
                        new Filters(damageTypes, inStructure, inBiome, inBiomeTag, inDimension, inSpellIds,
                                inSpellPools, inSpellSchools, minPowerLevel, extra.minRange(), extra.maxRange(),
                                extra.requiredItemId(), extra.requiredItemTag(), extra.requiredEffectOnKilled(),
                                extra.requiredEffectOnKiller(), extra.requiredAttributeId(), extra.minAttributeValue(),
                                extra.maxAttributeValue()))
        );
    }

    public static final MapCodec<EntityKillTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("entity_ids", List.of()).forGetter(EntityKillTask::entityIds),
                    FlexibleListCodec.listOrSingle(TagKey.codec(Registries.ENTITY_TYPE)).optionalFieldOf("entity_tags", List.of()).forGetter(EntityKillTask::entityTags),
                    Codec.INT.fieldOf("amount").forGetter(EntityKillTask::amount),
                    Filters.CODEC.optionalFieldOf("filters", Filters.EMPTY).forGetter(EntityKillTask::filters),
                    Codec.BOOL.optionalFieldOf("provides_map", false).forGetter(EntityKillTask::providesMap),
                    Codec.INT.optionalFieldOf("task_order").forGetter(EntityKillTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(EntityKillTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EntityKillTask::textureOverrideId)
            ).apply(instance, EntityKillTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("entity_kill");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String entityName = getEntityDisplayName();

        Map<String, String> values = new java.util.HashMap<>(Map.of(
                "kill_amount", String.valueOf(amount),
                "current_kills", String.valueOf(current),
                "entity_name", entityName,
                "min_power_level", String.valueOf(filters.minPowerLevel().orElse(0)),
                "min_range", String.valueOf(filters.minRange().orElse(0.0)),
                "max_range", String.valueOf(filters.maxRange().orElse(0.0))
        ));
        values.put("required_item", getRequiredItemDisplayName());
        values.put("min_attribute_value", String.valueOf(filters.minAttributeValue().orElse(0.0)));
        values.put("max_attribute_value", String.valueOf(filters.maxAttributeValue().orElse(0.0)));

        return TextMutator.mutate(Component.translatable(getDefaultTranslationKey()), values);
    }

    @Override
    public Map<String, String> getDescriptionValues() {
        Map<String, String> values = new java.util.HashMap<>();
        filters.minRange().ifPresent(r -> values.put("min_range", formatNumber(r)));
        filters.maxRange().ifPresent(r -> values.put("max_range", formatNumber(r)));
        filters.minPowerLevel().ifPresent(p -> values.put("min_power_level", String.valueOf(p)));
        return values;
    }

    private static String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public String getEntityDisplayName() {
        if (!entityIds.isEmpty()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityIds.get(0));
            String firstName = type != null ? type.getDescription().getString() : entityIds.get(0).toString();
            return entityIds.size() == 1 ? firstName : firstName + " (+" + (entityIds.size() - 1) + " others)";
        }

        if (!entityTags.isEmpty()) {
            String firstTag = "#" + entityTags.get(0).location();
            return entityTags.size() == 1 ? firstTag : firstTag + " (+" + (entityTags.size() - 1) + " others)";
        }

        return "entity";
    }

    public String getRequiredItemDisplayName() {
        if (filters.requiredItemId().isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(filters.requiredItemId().get());
            return item != null ? item.getDescription().getString() : filters.requiredItemId().get().toString();
        }
        if (filters.requiredItemTag().isPresent()) {
            return "#" + filters.requiredItemTag().get().location();
        }
        return "";
    }

    // tooltip-only detail lines
    public List<Component> getDetailedInfo() {
        List<Component> info = new ArrayList<>();

        if (!entityIds.isEmpty()) {
            for (ResourceLocation id : entityIds) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
                String name = type != null ? type.getDescription().getString() : id.toString();
                info.add(Component.literal("  - " + name));
            }
        }

        if (!entityTags.isEmpty()) {
            for (TagKey<EntityType<?>> tag : entityTags) {
                info.add(Component.literal("  - #" + tag.location()));
            }
        }

        if (filters.inStructure().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_structure", filters.inStructure().get().toString()));
        }

        if (filters.inBiome().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_biome", filters.inBiome().get().toString()));
        }
        if (filters.inBiomeTag().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_biome", "#" + filters.inBiomeTag().get().location()));
        }

        if (filters.inDimension().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_dimension", filters.inDimension().get().toString()));
        }

        for (ResourceLocation spellId : filters.inSpellIds()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_spell_id", spellId.toString()));
        }
        for (ResourceLocation spellPool : filters.inSpellPools()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_spell_pool", "#" + spellPool));
        }
        for (ResourceLocation spellSchool : filters.inSpellSchools()) {
            info.add(Component.translatable("task.quest_api.entity_kill.in_spell_school", spellSchool.toString()));
        }

        if (!filters.damageTypes().isEmpty()) {
            info.add(Component.translatable("task.quest_api.entity_kill.damage_types"));
            for (ResourceLocation damageType : filters.damageTypes()) {
                info.add(Component.literal("  - " + damageType.toString()));
            }
        }

        if (filters.minPowerLevel().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.min_power_level", filters.minPowerLevel().get()));
        }

        if (filters.minRange().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.min_range", filters.minRange().get()));
        }
        if (filters.maxRange().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.max_range", filters.maxRange().get()));
        }

        if (filters.requiredItemId().isPresent() || filters.requiredItemTag().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.required_item", getRequiredItemDisplayName()));
        }

        if (filters.requiredEffectOnKilled().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.required_effect_on_killed",
                    getEffectDisplayName(filters.requiredEffectOnKilled().get())));
        }
        if (filters.requiredEffectOnKiller().isPresent()) {
            info.add(Component.translatable("task.quest_api.entity_kill.required_effect_on_killer",
                    getEffectDisplayName(filters.requiredEffectOnKiller().get())));
        }

        if (filters.minAttributeValue().isPresent() || filters.maxAttributeValue().isPresent()) {
            if (filters.minAttributeValue().isPresent()) {
                info.add(Component.translatable("task.quest_api.entity_kill.min_attribute_value",
                        filters.requiredAttributeId().toString(), filters.minAttributeValue().get()));
            }
            if (filters.maxAttributeValue().isPresent()) {
                info.add(Component.translatable("task.quest_api.entity_kill.max_attribute_value",
                        filters.requiredAttributeId().toString(), filters.maxAttributeValue().get()));
            }
        }

        return info;
    }

    private static String getEffectDisplayName(ResourceLocation effectId) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        return effect != null ? effect.getDisplayName().getString() : effectId.toString();
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.entity_kill";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // player is the credited killer - the direct attacker, or a nearby player being checked for teammate/environmental crediting
    public boolean matches(LivingEntity killed, DamageSource source, Level level, ServerPlayer player) {
        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());

        if (!entityIds.isEmpty() && entityIds.stream().noneMatch(id -> killedId.equals(id))) {
            QuestAPI.LOGGER.debug("Entity not in allowed list: {} not in {}", killedId, entityIds);
            return false;
        }

        if (!entityTags.isEmpty() && entityTags.stream().noneMatch(tag -> killed.getType().is(tag))) {
            QuestAPI.LOGGER.debug("Entity type {} not in any of {}", killedId, entityTags);
            return false;
        }

        if (!filters.damageTypes().isEmpty()) {
            ResourceLocation sourceTypeId = source.typeHolder().unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);
            if (sourceTypeId == null) {
                QuestAPI.LOGGER.debug("Damage type is null");
                return false;
            }
            boolean damageTypeMatches = filters.damageTypes().stream()
                    .anyMatch(requiredType -> requiredType.equals(sourceTypeId));
            if (!damageTypeMatches) {
                QuestAPI.LOGGER.debug("Damage type mismatch: {} not in {}", sourceTypeId, filters.damageTypes());
                return false;
            }
            QuestAPI.LOGGER.debug("Damage type matched: {} in {}", sourceTypeId, filters.damageTypes());
        }

        if (filters.minPowerLevel().isPresent()) {
            if (!DungeonDifficultyCompat.isLoaded()) {
                QuestAPI.LOGGER.debug("Dungeon Difficulty not loaded - cannot verify entity power level");
                return false;
            }
            int entityPowerLevel = DungeonDifficultyCompat.getPowerLevel(killed);
            if (entityPowerLevel < filters.minPowerLevel().get()) {
                QuestAPI.LOGGER.debug("Entity power level too low: {} < {}", entityPowerLevel, filters.minPowerLevel().get());
                return false;
            }
            QuestAPI.LOGGER.debug("Entity power level matched: {} >= {}", entityPowerLevel, filters.minPowerLevel().get());
        }

        // spell attribution (Spell Engine integration) is best-effort - see SpellEngineCompat
        if (!filters.inSpellIds().isEmpty() || !filters.inSpellPools().isEmpty() || !filters.inSpellSchools().isEmpty()) {
            if (!SpellEngineCompat.isLoaded()) {
                QuestAPI.LOGGER.debug("Spell Engine not loaded - cannot verify spell attribution");
                return false;
            }
            if (!(source.getEntity() instanceof ServerPlayer caster)) {
                return false;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return false;
            }
            long currentTick = level.getGameTime();
            Optional<ResourceLocation> recentSpell = SpellEngineCompat.recentCastSpellId(caster.getUUID(), currentTick);
            if (recentSpell.isEmpty()) {
                QuestAPI.LOGGER.debug("No recently cast spell for spell attribution check");
                return false;
            }
            if (!SpellEngineCompat.matchesSelector(serverLevel, recentSpell.get(), filters.inSpellIds(), filters.inSpellPools(), filters.inSpellSchools())) {
                QuestAPI.LOGGER.debug("Recently cast spell {} doesn't match selector", recentSpell.get());
                return false;
            }
        }

        if (filters.inDimension().isPresent()) {
            ResourceLocation currentDimension = level.dimension().location();
            if (!currentDimension.equals(filters.inDimension().get())) {
                QuestAPI.LOGGER.debug("Dimension mismatch: {} != {}", currentDimension, filters.inDimension().get());
                return false;
            }
            QuestAPI.LOGGER.debug("Dimension matched: {}", currentDimension);
        }

        if (filters.minRange().isPresent() || filters.maxRange().isPresent()) {
            double distance = player.position().distanceTo(killed.position());
            if (filters.minRange().isPresent() && distance < filters.minRange().get()) {
                QuestAPI.LOGGER.debug("Kill too close: {} < min_range {}", distance, filters.minRange().get());
                return false;
            }
            if (filters.maxRange().isPresent() && distance > filters.maxRange().get()) {
                QuestAPI.LOGGER.debug("Kill too far: {} > max_range {}", distance, filters.maxRange().get());
                return false;
            }
        }

        if (filters.requiredItemId().isPresent() || filters.requiredItemTag().isPresent()) {
            ItemStack mainHand = player.getMainHandItem();
            boolean itemMatches = false;
            if (filters.requiredItemId().isPresent() && BuiltInRegistries.ITEM.getKey(mainHand.getItem()).equals(filters.requiredItemId().get())) {
                itemMatches = true;
            }
            if (!itemMatches && filters.requiredItemTag().isPresent() && mainHand.is(filters.requiredItemTag().get())) {
                itemMatches = true;
            }
            if (!itemMatches) {
                QuestAPI.LOGGER.debug("Main-hand item mismatch: {}", BuiltInRegistries.ITEM.getKey(mainHand.getItem()));
                return false;
            }
        }

        if (filters.requiredEffectOnKilled().isPresent()) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(filters.requiredEffectOnKilled().get()).orElse(null);
            if (effect == null || !killed.hasEffect(effect)) {
                QuestAPI.LOGGER.debug("Killed entity missing required effect: {}", filters.requiredEffectOnKilled().get());
                return false;
            }
        }

        if (filters.requiredEffectOnKiller().isPresent()) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(filters.requiredEffectOnKiller().get()).orElse(null);
            if (effect == null || !player.hasEffect(effect)) {
                QuestAPI.LOGGER.debug("Killer missing required effect: {}", filters.requiredEffectOnKiller().get());
                return false;
            }
        }

        if (filters.minAttributeValue().isPresent() || filters.maxAttributeValue().isPresent()) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(filters.requiredAttributeId()).orElse(null);
            if (attribute == null || !killed.getAttributes().hasAttribute(attribute)) {
                QuestAPI.LOGGER.debug("Killed entity has no attribute: {}", filters.requiredAttributeId());
                return false;
            }
            double value = killed.getAttributeValue(attribute);
            if (filters.minAttributeValue().isPresent() && value < filters.minAttributeValue().get()) {
                QuestAPI.LOGGER.debug("Attribute value too low: {} < {}", value, filters.minAttributeValue().get());
                return false;
            }
            if (filters.maxAttributeValue().isPresent() && value > filters.maxAttributeValue().get()) {
                QuestAPI.LOGGER.debug("Attribute value too high: {} > {}", value, filters.maxAttributeValue().get());
                return false;
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = killed.blockPosition();

            if (filters.inBiome().isPresent()) {
                if (!LocationMatchUtil.isInBiome(serverLevel, pos, filters.inBiome().get())) {
                    QuestAPI.LOGGER.debug("Biome mismatch: not in {}", filters.inBiome().get());
                    return false;
                }
                QuestAPI.LOGGER.debug("Biome matched: in {}", filters.inBiome().get());
            }

            if (filters.inBiomeTag().isPresent()) {
                if (!LocationMatchUtil.isInBiomeTag(serverLevel, pos, filters.inBiomeTag().get())) {
                    QuestAPI.LOGGER.debug("Biome tag mismatch: not in {}", filters.inBiomeTag().get().location());
                    return false;
                }
                QuestAPI.LOGGER.debug("Biome tag matched: in {}", filters.inBiomeTag().get().location());
            }

            if (filters.inStructure().isPresent()) {
                if (!LocationMatchUtil.isInStructure(serverLevel, pos, filters.inStructure().get())) {
                    QuestAPI.LOGGER.debug("Structure mismatch: not in {}", filters.inStructure().get());
                    return false;
                }
                QuestAPI.LOGGER.debug("Structure matched: in {}", filters.inStructure().get());
            }
        }

        QuestAPI.LOGGER.debug("Kill task matched! Entity: {}", killedId);
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ResourceLocation> entityIds = List.of();
        private List<TagKey<EntityType<?>>> entityTags = List.of();
        private int amount = 1;
        private List<ResourceLocation> damageTypes = List.of();
        private Optional<ResourceLocation> inStructure = Optional.empty();
        private Optional<ResourceLocation> inBiome = Optional.empty();
        private Optional<TagKey<Biome>> inBiomeTag = Optional.empty();
        private Optional<ResourceLocation> inDimension = Optional.empty();
        private List<ResourceLocation> inSpellIds = List.of();
        private List<ResourceLocation> inSpellPools = List.of();
        private List<ResourceLocation> inSpellSchools = List.of();
        private Optional<Integer> minPowerLevel = Optional.empty();
        private Optional<Double> minRange = Optional.empty();
        private Optional<Double> maxRange = Optional.empty();
        private Optional<ResourceLocation> requiredItemId = Optional.empty();
        private Optional<TagKey<Item>> requiredItemTag = Optional.empty();
        private Optional<ResourceLocation> requiredEffectOnKilled = Optional.empty();
        private Optional<ResourceLocation> requiredEffectOnKiller = Optional.empty();
        private ResourceLocation requiredAttributeId = Filters.DEFAULT_ATTRIBUTE_ID;
        private Optional<Double> minAttributeValue = Optional.empty();
        private Optional<Double> maxAttributeValue = Optional.empty();
        private boolean providesMap = false;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder entityId(ResourceLocation id) {
            return entityIds(id);
        }

        public Builder entityId(String id) {
            return entityId(ResourceLocation.parse(id));
        }

        public Builder entityIds(ResourceLocation... ids) {
            this.entityIds = List.of(ids);
            return this;
        }

        public Builder entityIds(String... ids) {
            this.entityIds = java.util.Arrays.stream(ids)
                    .map(ResourceLocation::parse)
                    .toList();
            return this;
        }

        public Builder entityIds(List<ResourceLocation> ids) {
            this.entityIds = ids;
            return this;
        }

        public Builder entityTag(TagKey<EntityType<?>> tag) {
            return entityTags(tag);
        }

        @SafeVarargs
        public final Builder entityTags(TagKey<EntityType<?>>... tags) {
            this.entityTags = List.of(tags);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder damageType(ResourceLocation type) {
            this.damageTypes = List.of(type);
            return this;
        }

        public Builder damageTypes(ResourceLocation... types) {
            this.damageTypes = List.of(types);
            return this;
        }

        public Builder damageTypes(List<ResourceLocation> types) {
            this.damageTypes = types;
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

        public Builder inBiome(String biome) {
            return inBiome(ResourceLocation.parse(biome));
        }

        public Builder inBiomeTag(TagKey<Biome> biomeTag) {
            this.inBiomeTag = Optional.of(biomeTag);
            return this;
        }

        public Builder inDimension(ResourceLocation dimension) {
            this.inDimension = Optional.of(dimension);
            return this;
        }

        public Builder inDimension(String dimension) {
            return inDimension(ResourceLocation.parse(dimension));
        }

        // best-effort: matches if the killing player cast this spell shortly before the kill (see SpellEngineCompat.recentlyCastSpell)
        public Builder inSpellId(ResourceLocation spellId) {
            return inSpellIds(spellId);
        }

        public Builder inSpellId(String spellId) {
            return inSpellId(ResourceLocation.parse(spellId));
        }

        public Builder inSpellIds(ResourceLocation... spellIds) {
            this.inSpellIds = List.of(spellIds);
            return this;
        }

        public Builder inSpellPool(ResourceLocation spellPool) {
            return inSpellPools(spellPool);
        }

        public Builder inSpellPool(String spellPool) {
            return inSpellPool(ResourceLocation.parse(spellPool));
        }

        public Builder inSpellPools(ResourceLocation... spellPools) {
            this.inSpellPools = List.of(spellPools);
            return this;
        }

        public Builder inSpellSchool(ResourceLocation spellSchool) {
            return inSpellSchools(spellSchool);
        }

        public Builder inSpellSchool(String spellSchool) {
            return inSpellSchool(ResourceLocation.parse(spellSchool));
        }

        public Builder inSpellSchools(ResourceLocation... spellSchools) {
            this.inSpellSchools = List.of(spellSchools);
            return this;
        }

        // same power-level concept as Dungeon Difficulty's item scaling, applied to the killed entity's mob scaling
        public Builder minPowerLevel(int minPowerLevel) {
            this.minPowerLevel = Optional.of(minPowerLevel);
            return this;
        }

        public Builder minRange(double minRange) {
            this.minRange = Optional.of(minRange);
            return this;
        }

        public Builder maxRange(double maxRange) {
            this.maxRange = Optional.of(maxRange);
            return this;
        }

        public Builder requiredItemId(ResourceLocation itemId) {
            this.requiredItemId = Optional.of(itemId);
            return this;
        }

        public Builder requiredItemId(Item item) {
            return requiredItemId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder requiredItemTag(TagKey<Item> tag) {
            this.requiredItemTag = Optional.of(tag);
            return this;
        }

        public Builder requiredEffectOnKilled(ResourceLocation effectId) {
            this.requiredEffectOnKilled = Optional.of(effectId);
            return this;
        }

        public Builder requiredEffectOnKiller(ResourceLocation effectId) {
            this.requiredEffectOnKiller = Optional.of(effectId);
            return this;
        }

        public Builder requiredAttributeId(ResourceLocation attributeId) {
            this.requiredAttributeId = attributeId;
            return this;
        }

        public Builder minAttributeValue(double value) {
            this.minAttributeValue = Optional.of(value);
            return this;
        }

        public Builder maxAttributeValue(double value) {
            this.maxAttributeValue = Optional.of(value);
            return this;
        }

        // only meaningful with inStructure - grants a one-time treasure map to that structure on accept
        public Builder providesMap(boolean providesMap) {
            this.providesMap = providesMap;
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

        public EntityKillTask build() {
            if (entityIds.isEmpty() && entityTags.isEmpty()) {
                throw new IllegalStateException("EntityKillTask requires entityIds or entityTags");
            }
            Filters filters = new Filters(damageTypes, inStructure, inBiome, inBiomeTag, inDimension,
                    inSpellIds, inSpellPools, inSpellSchools, minPowerLevel, minRange, maxRange, requiredItemId,
                    requiredItemTag, requiredEffectOnKilled, requiredEffectOnKiller, requiredAttributeId,
                    minAttributeValue, maxAttributeValue);
            return new EntityKillTask(entityIds, entityTags, amount, filters, providesMap,
                    taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

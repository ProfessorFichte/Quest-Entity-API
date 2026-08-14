package com.qeapi.quest.task;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
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

// filterable by entity type/tag, damage type, location/spell attribution/power level, main-hand
// item, status effects, and a generalized attribute check - all OR logic where a list is involved
public record EntityKillTask(
        Optional<ResourceLocation> entityId,
        Optional<TagKey<EntityType<?>>> entityTag,
        List<ResourceLocation> entityIds,
        int amount,
        List<ResourceLocation> damageTypes,
        Optional<ResourceLocation> inStructure,
        Optional<ResourceLocation> inBiome,
        Optional<TagKey<Biome>> inBiomeTag,
        Optional<ResourceLocation> inDimension,
        Optional<ResourceLocation> inSpellId,
        Optional<ResourceLocation> inSpellPool,
        Optional<ResourceLocation> inSpellSchool,
        Optional<Integer> minPowerLevel,
        Optional<Double> minRange,
        Optional<Double> maxRange,
        Optional<ResourceLocation> requiredItemId,
        Optional<TagKey<Item>> requiredItemTag,
        Optional<ResourceLocation> requiredEffectOnKilled,
        Optional<ResourceLocation> requiredEffectOnKiller,
        ResourceLocation requiredAttributeId,
        Optional<Double> minAttributeValue,
        Optional<Double> maxAttributeValue,
        boolean providesMap,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    private static final ResourceLocation DEFAULT_ATTRIBUTE_ID = ResourceLocation.withDefaultNamespace("max_health");

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

    public static final MapCodec<EntityKillTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("entity_id").forGetter(EntityKillTask::entityId),
                    TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("entity_tag").forGetter(EntityKillTask::entityTag),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("entity_ids", List.of()).forGetter(EntityKillTask::entityIds),
                    Codec.INT.fieldOf("amount").forGetter(EntityKillTask::amount),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(EntityKillTask::damageTypes),
                    ResourceLocation.CODEC.optionalFieldOf("in_structure").forGetter(EntityKillTask::inStructure),
                    ResourceLocation.CODEC.optionalFieldOf("in_biome").forGetter(EntityKillTask::inBiome),
                    TagKey.codec(Registries.BIOME).optionalFieldOf("in_biome_tag").forGetter(EntityKillTask::inBiomeTag),
                    ResourceLocation.CODEC.optionalFieldOf("in_dimension").forGetter(EntityKillTask::inDimension),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_id").forGetter(EntityKillTask::inSpellId),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_pool").forGetter(EntityKillTask::inSpellPool),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_school").forGetter(EntityKillTask::inSpellSchool),
                    Codec.INT.optionalFieldOf("min_power_level").forGetter(EntityKillTask::minPowerLevel),
                    instance.group(
                            Codec.DOUBLE.optionalFieldOf("min_range").forGetter(EntityKillTask::minRange),
                            Codec.DOUBLE.optionalFieldOf("max_range").forGetter(EntityKillTask::maxRange),
                            ResourceLocation.CODEC.optionalFieldOf("required_item_id").forGetter(EntityKillTask::requiredItemId),
                            TagKey.codec(Registries.ITEM).optionalFieldOf("required_item_tag").forGetter(EntityKillTask::requiredItemTag),
                            ResourceLocation.CODEC.optionalFieldOf("required_effect_on_killed").forGetter(EntityKillTask::requiredEffectOnKilled),
                            ResourceLocation.CODEC.optionalFieldOf("required_effect_on_killer").forGetter(EntityKillTask::requiredEffectOnKiller),
                            ResourceLocation.CODEC.optionalFieldOf("required_attribute_id", DEFAULT_ATTRIBUTE_ID).forGetter(EntityKillTask::requiredAttributeId),
                            Codec.DOUBLE.optionalFieldOf("min_attribute_value").forGetter(EntityKillTask::minAttributeValue),
                            Codec.DOUBLE.optionalFieldOf("max_attribute_value").forGetter(EntityKillTask::maxAttributeValue)
                    ).apply(instance, ExtraFilters::new),
                    Codec.BOOL.optionalFieldOf("provides_map", false).forGetter(EntityKillTask::providesMap),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EntityKillTask::textureOverrideId)
            ).apply(instance, (entityId, entityTag, entityIds, amount, damageTypes, inStructure, inBiome,
                    inBiomeTag, inDimension, inSpellId, inSpellPool, inSpellSchool, minPowerLevel, extra,
                    providesMap, textureOverrideId) ->
                    new EntityKillTask(entityId, entityTag, entityIds, amount, damageTypes, inStructure, inBiome,
                            inBiomeTag, inDimension, inSpellId, inSpellPool, inSpellSchool, minPowerLevel,
                            extra.minRange(), extra.maxRange(), extra.requiredItemId(), extra.requiredItemTag(),
                            extra.requiredEffectOnKilled(), extra.requiredEffectOnKiller(), extra.requiredAttributeId(),
                            extra.minAttributeValue(), extra.maxAttributeValue(), providesMap, textureOverrideId))
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("entity_kill");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String entityName = getEntityDisplayName();

        Map<String, String> values = new java.util.HashMap<>(Map.of(
                "kill_amount", String.valueOf(amount),
                "current_kills", String.valueOf(current),
                "entity_name", entityName,
                "min_power_level", String.valueOf(minPowerLevel.orElse(0)),
                "min_range", String.valueOf(minRange.orElse(0.0)),
                "max_range", String.valueOf(maxRange.orElse(0.0))
        ));
        values.put("required_item", getRequiredItemDisplayName());
        values.put("min_attribute_value", String.valueOf(minAttributeValue.orElse(0.0)));
        values.put("max_attribute_value", String.valueOf(maxAttributeValue.orElse(0.0)));

        return TextMutator.mutate(Component.translatable(getDefaultTranslationKey()), values);
    }

    @Override
    public Map<String, String> getDescriptionValues() {
        Map<String, String> values = new java.util.HashMap<>();
        minRange.ifPresent(r -> values.put("min_range", formatNumber(r)));
        maxRange.ifPresent(r -> values.put("max_range", formatNumber(r)));
        minPowerLevel.ifPresent(p -> values.put("min_power_level", String.valueOf(p)));
        return values;
    }

    private static String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public String getEntityDisplayName() {
        if (entityId.isPresent()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId.get());
            return type != null ? type.getDescription().getString() : entityId.get().toString();
        }

        if (!entityIds.isEmpty()) {
            if (entityIds.size() == 1) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityIds.get(0));
                return type != null ? type.getDescription().getString() : entityIds.get(0).toString();
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityIds.get(0));
            String firstName = type != null ? type.getDescription().getString() : entityIds.get(0).toString();
            return firstName + " (+" + (entityIds.size() - 1) + " others)";
        }

        if (entityTag.isPresent()) {
            return "#" + entityTag.get().location();
        }

        return "entity";
    }

    public String getRequiredItemDisplayName() {
        if (requiredItemId.isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(requiredItemId.get());
            return item != null ? item.getDescription().getString() : requiredItemId.get().toString();
        }
        if (requiredItemTag.isPresent()) {
            return "#" + requiredItemTag.get().location();
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

        if (inStructure.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_structure", inStructure.get().toString()));
        }

        if (inBiome.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_biome", inBiome.get().toString()));
        }
        if (inBiomeTag.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_biome", "#" + inBiomeTag.get().location()));
        }

        if (inDimension.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_dimension", inDimension.get().toString()));
        }

        if (inSpellId.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_spell_id", inSpellId.get().toString()));
        }
        if (inSpellPool.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_spell_pool", "#" + inSpellPool.get()));
        }
        if (inSpellSchool.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.in_spell_school", inSpellSchool.get().toString()));
        }

        if (!damageTypes.isEmpty()) {
            info.add(Component.translatable("task.qe_api.entity_kill.damage_types"));
            for (ResourceLocation damageType : damageTypes) {
                info.add(Component.literal("  - " + damageType.toString()));
            }
        }

        if (minPowerLevel.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.min_power_level", minPowerLevel.get()));
        }

        if (minRange.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.min_range", minRange.get()));
        }
        if (maxRange.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.max_range", maxRange.get()));
        }

        if (requiredItemId.isPresent() || requiredItemTag.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.required_item", getRequiredItemDisplayName()));
        }

        if (requiredEffectOnKilled.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.required_effect_on_killed",
                    getEffectDisplayName(requiredEffectOnKilled.get())));
        }
        if (requiredEffectOnKiller.isPresent()) {
            info.add(Component.translatable("task.qe_api.entity_kill.required_effect_on_killer",
                    getEffectDisplayName(requiredEffectOnKiller.get())));
        }

        if (minAttributeValue.isPresent() || maxAttributeValue.isPresent()) {
            if (minAttributeValue.isPresent()) {
                info.add(Component.translatable("task.qe_api.entity_kill.min_attribute_value",
                        requiredAttributeId.toString(), minAttributeValue.get()));
            }
            if (maxAttributeValue.isPresent()) {
                info.add(Component.translatable("task.qe_api.entity_kill.max_attribute_value",
                        requiredAttributeId.toString(), maxAttributeValue.get()));
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
        return "task.qe_api.entity_kill";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // player is the credited killer (the actual attacker for a direct kill, or the nearby player
    // being checked for teammate/environmental-kill crediting) - used for the min_range/max_range,
    // main-hand item, killer-effect, and killed-entity-effect/attribute checks
    public boolean matches(LivingEntity killed, DamageSource source, Level level, ServerPlayer player) {
        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());

        if (entityId.isPresent()) {
            if (!killedId.equals(entityId.get())) {
                QuestEntityAPI.LOGGER.debug("Entity type mismatch: {} != {}", killedId, entityId.get());
                return false;
            }
        }

        if (!entityIds.isEmpty()) {
            boolean anyMatch = entityIds.stream().anyMatch(id -> killedId.equals(id));
            if (!anyMatch) {
                QuestEntityAPI.LOGGER.debug("Entity not in allowed list: {} not in {}", killedId, entityIds);
                return false;
            }
        }

        if (entityTag.isPresent()) {
            if (!killed.getType().is(entityTag.get())) {
                QuestEntityAPI.LOGGER.debug("Entity tag mismatch: {} not in tag {}", killedId, entityTag.get().location());
                return false;
            }
        }

        if (!damageTypes.isEmpty()) {
            ResourceLocation sourceTypeId = source.typeHolder().unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);
            if (sourceTypeId == null) {
                QuestEntityAPI.LOGGER.debug("Damage type is null");
                return false;
            }
            boolean damageTypeMatches = damageTypes.stream()
                    .anyMatch(requiredType -> requiredType.equals(sourceTypeId));
            if (!damageTypeMatches) {
                QuestEntityAPI.LOGGER.debug("Damage type mismatch: {} not in {}", sourceTypeId, damageTypes);
                return false;
            }
            QuestEntityAPI.LOGGER.debug("Damage type matched: {} in {}", sourceTypeId, damageTypes);
        }

        if (minPowerLevel.isPresent()) {
            if (!DungeonDifficultyCompat.isLoaded()) {
                QuestEntityAPI.LOGGER.debug("Dungeon Difficulty not loaded - cannot verify entity power level");
                return false;
            }
            int entityPowerLevel = DungeonDifficultyCompat.getPowerLevel(killed);
            if (entityPowerLevel < minPowerLevel.get()) {
                QuestEntityAPI.LOGGER.debug("Entity power level too low: {} < {}", entityPowerLevel, minPowerLevel.get());
                return false;
            }
            QuestEntityAPI.LOGGER.debug("Entity power level matched: {} >= {}", entityPowerLevel, minPowerLevel.get());
        }

        // spell attribution (Spell Engine integration) is best-effort - see SpellEngineCompat
        if (inSpellId.isPresent() || inSpellPool.isPresent() || inSpellSchool.isPresent()) {
            if (!SpellEngineCompat.isLoaded()) {
                QuestEntityAPI.LOGGER.debug("Spell Engine not loaded - cannot verify spell attribution");
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
                QuestEntityAPI.LOGGER.debug("No recently cast spell for spell attribution check");
                return false;
            }
            if (!SpellEngineCompat.matchesSelector(serverLevel, recentSpell.get(), inSpellId, inSpellPool, inSpellSchool)) {
                QuestEntityAPI.LOGGER.debug("Recently cast spell {} doesn't match selector", recentSpell.get());
                return false;
            }
        }

        if (inDimension.isPresent()) {
            ResourceLocation currentDimension = level.dimension().location();
            if (!currentDimension.equals(inDimension.get())) {
                QuestEntityAPI.LOGGER.debug("Dimension mismatch: {} != {}", currentDimension, inDimension.get());
                return false;
            }
            QuestEntityAPI.LOGGER.debug("Dimension matched: {}", currentDimension);
        }

        if (minRange.isPresent() || maxRange.isPresent()) {
            double distance = player.position().distanceTo(killed.position());
            if (minRange.isPresent() && distance < minRange.get()) {
                QuestEntityAPI.LOGGER.debug("Kill too close: {} < min_range {}", distance, minRange.get());
                return false;
            }
            if (maxRange.isPresent() && distance > maxRange.get()) {
                QuestEntityAPI.LOGGER.debug("Kill too far: {} > max_range {}", distance, maxRange.get());
                return false;
            }
        }

        if (requiredItemId.isPresent() || requiredItemTag.isPresent()) {
            ItemStack mainHand = player.getMainHandItem();
            boolean itemMatches = false;
            if (requiredItemId.isPresent() && BuiltInRegistries.ITEM.getKey(mainHand.getItem()).equals(requiredItemId.get())) {
                itemMatches = true;
            }
            if (!itemMatches && requiredItemTag.isPresent() && mainHand.is(requiredItemTag.get())) {
                itemMatches = true;
            }
            if (!itemMatches) {
                QuestEntityAPI.LOGGER.debug("Main-hand item mismatch: {}", BuiltInRegistries.ITEM.getKey(mainHand.getItem()));
                return false;
            }
        }

        if (requiredEffectOnKilled.isPresent()) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(requiredEffectOnKilled.get()).orElse(null);
            if (effect == null || !killed.hasEffect(effect)) {
                QuestEntityAPI.LOGGER.debug("Killed entity missing required effect: {}", requiredEffectOnKilled.get());
                return false;
            }
        }

        if (requiredEffectOnKiller.isPresent()) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(requiredEffectOnKiller.get()).orElse(null);
            if (effect == null || !player.hasEffect(effect)) {
                QuestEntityAPI.LOGGER.debug("Killer missing required effect: {}", requiredEffectOnKiller.get());
                return false;
            }
        }

        if (minAttributeValue.isPresent() || maxAttributeValue.isPresent()) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(requiredAttributeId).orElse(null);
            if (attribute == null || !killed.getAttributes().hasAttribute(attribute)) {
                QuestEntityAPI.LOGGER.debug("Killed entity has no attribute: {}", requiredAttributeId);
                return false;
            }
            double value = killed.getAttributeValue(attribute);
            if (minAttributeValue.isPresent() && value < minAttributeValue.get()) {
                QuestEntityAPI.LOGGER.debug("Attribute value too low: {} < {}", value, minAttributeValue.get());
                return false;
            }
            if (maxAttributeValue.isPresent() && value > maxAttributeValue.get()) {
                QuestEntityAPI.LOGGER.debug("Attribute value too high: {} > {}", value, maxAttributeValue.get());
                return false;
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = killed.blockPosition();

            if (inBiome.isPresent()) {
                if (!LocationMatchUtil.isInBiome(serverLevel, pos, inBiome.get())) {
                    QuestEntityAPI.LOGGER.debug("Biome mismatch: not in {}", inBiome.get());
                    return false;
                }
                QuestEntityAPI.LOGGER.debug("Biome matched: in {}", inBiome.get());
            }

            if (inBiomeTag.isPresent()) {
                if (!LocationMatchUtil.isInBiomeTag(serverLevel, pos, inBiomeTag.get())) {
                    QuestEntityAPI.LOGGER.debug("Biome tag mismatch: not in {}", inBiomeTag.get().location());
                    return false;
                }
                QuestEntityAPI.LOGGER.debug("Biome tag matched: in {}", inBiomeTag.get().location());
            }

            if (inStructure.isPresent()) {
                if (!LocationMatchUtil.isInStructure(serverLevel, pos, inStructure.get())) {
                    QuestEntityAPI.LOGGER.debug("Structure mismatch: not in {}", inStructure.get());
                    return false;
                }
                QuestEntityAPI.LOGGER.debug("Structure matched: in {}", inStructure.get());
            }
        }

        QuestEntityAPI.LOGGER.debug("Kill task matched! Entity: {}", killedId);
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> entityId = Optional.empty();
        private Optional<TagKey<EntityType<?>>> entityTag = Optional.empty();
        private List<ResourceLocation> entityIds = List.of();
        private int amount = 1;
        private List<ResourceLocation> damageTypes = List.of();
        private Optional<ResourceLocation> inStructure = Optional.empty();
        private Optional<ResourceLocation> inBiome = Optional.empty();
        private Optional<TagKey<Biome>> inBiomeTag = Optional.empty();
        private Optional<ResourceLocation> inDimension = Optional.empty();
        private Optional<ResourceLocation> inSpellId = Optional.empty();
        private Optional<ResourceLocation> inSpellPool = Optional.empty();
        private Optional<ResourceLocation> inSpellSchool = Optional.empty();
        private Optional<Integer> minPowerLevel = Optional.empty();
        private Optional<Double> minRange = Optional.empty();
        private Optional<Double> maxRange = Optional.empty();
        private Optional<ResourceLocation> requiredItemId = Optional.empty();
        private Optional<TagKey<Item>> requiredItemTag = Optional.empty();
        private Optional<ResourceLocation> requiredEffectOnKilled = Optional.empty();
        private Optional<ResourceLocation> requiredEffectOnKiller = Optional.empty();
        private ResourceLocation requiredAttributeId = DEFAULT_ATTRIBUTE_ID;
        private Optional<Double> minAttributeValue = Optional.empty();
        private Optional<Double> maxAttributeValue = Optional.empty();
        private boolean providesMap = false;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder entityId(ResourceLocation id) {
            this.entityId = Optional.of(id);
            return this;
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
            this.entityTag = Optional.of(tag);
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

        // matches if the killing player cast this spell shortly before the kill (best-effort,
        // see SpellEngineCompat.recentlyCastSpell)
        public Builder inSpellId(ResourceLocation spellId) {
            this.inSpellId = Optional.of(spellId);
            return this;
        }

        public Builder inSpellId(String spellId) {
            return inSpellId(ResourceLocation.parse(spellId));
        }

        public Builder inSpellPool(ResourceLocation spellPool) {
            this.inSpellPool = Optional.of(spellPool);
            return this;
        }

        public Builder inSpellPool(String spellPool) {
            return inSpellPool(ResourceLocation.parse(spellPool));
        }

        public Builder inSpellSchool(ResourceLocation spellSchool) {
            this.inSpellSchool = Optional.of(spellSchool);
            return this;
        }

        public Builder inSpellSchool(String spellSchool) {
            return inSpellSchool(ResourceLocation.parse(spellSchool));
        }

        // the killed entity must have been scaled to at least this power level by Dungeon
        // Difficulty's mob scaling (same power-level concept as its item scaling)
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

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public EntityKillTask build() {
            if (entityId.isEmpty() && entityTag.isEmpty() && entityIds.isEmpty()) {
                throw new IllegalStateException("EntityKillTask requires entityId, entityIds, or entityTag");
            }
            return new EntityKillTask(entityId, entityTag, entityIds, amount, damageTypes,
                    inStructure, inBiome, inBiomeTag, inDimension, inSpellId, inSpellPool, inSpellSchool,
                    minPowerLevel, minRange, maxRange, requiredItemId, requiredItemTag,
                    requiredEffectOnKilled, requiredEffectOnKiller, requiredAttributeId,
                    minAttributeValue, maxAttributeValue, providesMap, textureOverrideId);
        }
    }
}

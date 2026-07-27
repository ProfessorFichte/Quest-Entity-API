package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// filterable by entity type/tag, damage type, structure/biome/dimension location - all OR logic where a list is involved
public record EntityKillTask(
        Optional<ResourceLocation> entityId,
        Optional<TagKey<EntityType<?>>> entityTag,
        List<ResourceLocation> entityIds,  // OR logic
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
        boolean providesMap
) implements QuestTask {

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
                    Codec.BOOL.optionalFieldOf("provides_map", false).forGetter(EntityKillTask::providesMap)
            ).apply(instance, EntityKillTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("entity_kill");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String entityName = getEntityDisplayName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "kill_amount", String.valueOf(amount),
                        "current_kills", String.valueOf(current),
                        "entity_name", entityName
                )
        );
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

        return info;
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.entity_kill";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(LivingEntity killed, DamageSource source, Level level) {
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
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return false;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return false;
            }
            long currentTick = level.getGameTime();
            Optional<ResourceLocation> recentSpell = SpellEngineCompat.recentCastSpellId(player.getUUID(), currentTick);
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

        // biome/structure checks need a server-side level
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = killed.blockPosition();

            if (inBiome.isPresent()) {
                if (!isInBiome(serverLevel, pos, inBiome.get())) {
                    QuestEntityAPI.LOGGER.debug("Biome mismatch: not in {}", inBiome.get());
                    return false;
                }
                QuestEntityAPI.LOGGER.debug("Biome matched: in {}", inBiome.get());
            }

            if (inBiomeTag.isPresent()) {
                if (!isInBiomeTag(serverLevel, pos, inBiomeTag.get())) {
                    QuestEntityAPI.LOGGER.debug("Biome tag mismatch: not in {}", inBiomeTag.get().location());
                    return false;
                }
                QuestEntityAPI.LOGGER.debug("Biome tag matched: in {}", inBiomeTag.get().location());
            }

            if (inStructure.isPresent()) {
                if (!isInStructure(serverLevel, pos, inStructure.get())) {
                    QuestEntityAPI.LOGGER.debug("Structure mismatch: not in {}", inStructure.get());
                    return false;
                }
                QuestEntityAPI.LOGGER.debug("Structure matched: in {}", inStructure.get());
            }
        }

        QuestEntityAPI.LOGGER.debug("Kill task matched! Entity: {}", killedId);
        return true;
    }

    private boolean isInBiome(ServerLevel level, BlockPos pos, ResourceLocation biomeId) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();

        if (biomeKey.isEmpty()) {
            return false;
        }

        return biomeKey.get().location().equals(biomeId);
    }

    private boolean isInBiomeTag(ServerLevel level, BlockPos pos, TagKey<Biome> biomeTag) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        return biomeHolder.is(biomeTag);
    }

    private boolean isInStructure(ServerLevel level, BlockPos pos, ResourceLocation structureId) {
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        Structure targetStructure = structureRegistry.get(structureId);
        if (targetStructure == null) {
            QuestEntityAPI.LOGGER.warn("Structure {} not found in registry", structureId);
            return false;
        }

        StructureStart structureStart = level.structureManager().getStructureWithPieceAt(pos, targetStructure);
        return structureStart.isValid();
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
        private boolean providesMap = false;

        public Builder entityId(ResourceLocation id) {
            this.entityId = Optional.of(id);
            return this;
        }

        public Builder entityId(String id) {
            return entityId(ResourceLocation.parse(id));
        }

        // OR logic - kill any of these
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

        // best-effort spell attribution: matches if the killing player cast this spell shortly
        // before the kill - see SpellEngineCompat.recentlyCastSpell
        public Builder inSpellId(ResourceLocation spellId) {
            this.inSpellId = Optional.of(spellId);
            return this;
        }

        public Builder inSpellId(String spellId) {
            return inSpellId(ResourceLocation.parse(spellId));
        }

        // same best-effort attribution as inSpellId, but for a spell pool tag
        public Builder inSpellPool(ResourceLocation spellPool) {
            this.inSpellPool = Optional.of(spellPool);
            return this;
        }

        public Builder inSpellPool(String spellPool) {
            return inSpellPool(ResourceLocation.parse(spellPool));
        }

        // same best-effort attribution as inSpellId, but for a spell school
        public Builder inSpellSchool(ResourceLocation spellSchool) {
            this.inSpellSchool = Optional.of(spellSchool);
            return this;
        }

        public Builder inSpellSchool(String spellSchool) {
            return inSpellSchool(ResourceLocation.parse(spellSchool));
        }

        // (Dungeon Difficulty compat) the killed entity must have been scaled to at least this
        // power level - e.g. mobs Dungeon Difficulty buffed for spawning in a high-tier structure
        // or dimension, same power-level concept as its item scaling.
        public Builder minPowerLevel(int minPowerLevel) {
            this.minPowerLevel = Optional.of(minPowerLevel);
            return this;
        }

        // only meaningful with inStructure - grants a one-time treasure map to that structure on accept
        public Builder providesMap(boolean providesMap) {
            this.providesMap = providesMap;
            return this;
        }

        public EntityKillTask build() {
            if (entityId.isEmpty() && entityTag.isEmpty() && entityIds.isEmpty()) {
                throw new IllegalStateException("EntityKillTask requires entityId, entityIds, or entityTag");
            }
            return new EntityKillTask(entityId, entityTag, entityIds, amount, damageTypes,
                    inStructure, inBiome, inBiomeTag, inDimension, inSpellId, inSpellPool, inSpellSchool,
                    minPowerLevel, providesMap);
        }
    }
}

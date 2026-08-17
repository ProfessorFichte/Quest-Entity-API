package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.FlexibleListCodec;
import com.qeapi.util.TextMutator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// amount is a total-damage threshold, not a hit count - progress accumulates the real per-hit amount (see QuestEventHandler.onDamageDealt), not a flat +1 per hit
public record DealDamageAmountTask(
        double amount,
        Filters filters,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_tasks/deal_damage_amount_default.png");

    public record Filters(
            List<ResourceLocation> entityIds,
            List<TagKey<EntityType<?>>> entityTags,
            List<ResourceLocation> damageTypes,
            List<ResourceLocation> inSpellIds,
            List<ResourceLocation> inSpellPools,
            List<ResourceLocation> inSpellSchools
    ) {
        public static final Filters EMPTY = new Filters(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        public static final Codec<Filters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("entity_ids", List.of()).forGetter(Filters::entityIds),
                        FlexibleListCodec.listOrSingle(TagKey.codec(Registries.ENTITY_TYPE)).optionalFieldOf("entity_tags", List.of()).forGetter(Filters::entityTags),
                        ResourceLocation.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(Filters::damageTypes),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_ids", List.of()).forGetter(Filters::inSpellIds),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_pools", List.of()).forGetter(Filters::inSpellPools),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_schools", List.of()).forGetter(Filters::inSpellSchools)
                ).apply(instance, Filters::new)
        );
    }

    public static final MapCodec<DealDamageAmountTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("amount").forGetter(DealDamageAmountTask::amount),
                    Filters.CODEC.optionalFieldOf("filters", Filters.EMPTY).forGetter(DealDamageAmountTask::filters),
                    Codec.INT.optionalFieldOf("task_order").forGetter(DealDamageAmountTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(DealDamageAmountTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(DealDamageAmountTask::textureOverrideId)
            ).apply(instance, DealDamageAmountTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("deal_damage_amount");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), getTargetAmount());

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "damage_amount", String.valueOf((int) amount),
                        "current_damage", String.valueOf(current),
                        "entity_name", getEntityDisplayName()
                )
        );
    }

    public String getEntityDisplayName() {
        if (!filters.entityIds().isEmpty()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(filters.entityIds().get(0));
            String firstName = type != null ? type.getDescription().getString() : filters.entityIds().get(0).toString();
            return filters.entityIds().size() == 1 ? firstName : firstName + " (+" + (filters.entityIds().size() - 1) + " others)";
        }
        if (!filters.entityTags().isEmpty()) {
            String firstTag = "#" + filters.entityTags().get(0).location();
            return filters.entityTags().size() == 1 ? firstTag : firstTag + " (+" + (filters.entityTags().size() - 1) + " others)";
        }
        return "any entity";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.deal_damage_amount";
    }

    @Override
    public int getTargetAmount() {
        return (int) Math.round(amount);
    }

    public boolean matches(LivingEntity target, DamageSource source, Level level, ServerPlayer player) {
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        if (!filters.entityIds().isEmpty() && filters.entityIds().stream().noneMatch(id -> targetId.equals(id))) {
            return false;
        }
        if (!filters.entityTags().isEmpty() && filters.entityTags().stream().noneMatch(tag -> target.getType().is(tag))) {
            return false;
        }

        if (!filters.damageTypes().isEmpty()) {
            ResourceLocation sourceTypeId = source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
            if (sourceTypeId == null || filters.damageTypes().stream().noneMatch(t -> t.equals(sourceTypeId))) {
                return false;
            }
        }

        if (!filters.inSpellIds().isEmpty() || !filters.inSpellPools().isEmpty() || !filters.inSpellSchools().isEmpty()) {
            if (!SpellEngineCompat.isLoaded()) {
                return false;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return false;
            }
            long currentTick = level.getGameTime();
            Optional<ResourceLocation> recentSpell = SpellEngineCompat.recentCastSpellId(player.getUUID(), currentTick);
            if (recentSpell.isEmpty()) {
                return false;
            }
            if (!SpellEngineCompat.matchesSelector(serverLevel, recentSpell.get(), filters.inSpellIds(), filters.inSpellPools(), filters.inSpellSchools())) {
                return false;
            }
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ResourceLocation> entityIds = List.of();
        private List<TagKey<EntityType<?>>> entityTags = List.of();
        private List<ResourceLocation> damageTypes = List.of();
        private List<ResourceLocation> inSpellIds = List.of();
        private List<ResourceLocation> inSpellPools = List.of();
        private List<ResourceLocation> inSpellSchools = List.of();
        private double amount = 1.0;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

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

        public Builder inSpellPools(ResourceLocation... spellPools) {
            this.inSpellPools = List.of(spellPools);
            return this;
        }

        public Builder inSpellSchool(ResourceLocation spellSchool) {
            return inSpellSchools(spellSchool);
        }

        public Builder inSpellSchools(ResourceLocation... spellSchools) {
            this.inSpellSchools = List.of(spellSchools);
            return this;
        }

        public Builder amount(double amount) {
            this.amount = amount;
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

        public DealDamageAmountTask build() {
            Filters filters = new Filters(entityIds, entityTags, damageTypes, inSpellIds, inSpellPools, inSpellSchools);
            return new DealDamageAmountTask(amount, filters, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

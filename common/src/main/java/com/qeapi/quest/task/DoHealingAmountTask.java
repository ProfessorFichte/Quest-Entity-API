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
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// vanilla LivingEntity.heal(float) has no source param, so healing OTHERS can only be credited via the Spell Engine cast heuristic - no vanilla mechanic heals a separate target anyway
public record DoHealingAmountTask(
        double amount,
        Filters filters,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_tasks/do_healing_amount_default.png");

    public enum HealTarget implements StringRepresentable {
        SELF("self"),
        OTHERS("others"),
        EITHER("either");

        public static final Codec<HealTarget> CODEC = StringRepresentable.fromEnum(HealTarget::values);

        private final String name;

        HealTarget(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Filters(
            List<ResourceLocation> entityIds,
            List<TagKey<EntityType<?>>> entityTags,
            List<ResourceLocation> inSpellIds,
            List<ResourceLocation> inSpellPools,
            List<ResourceLocation> inSpellSchools,
            HealTarget healTarget
    ) {
        public static final Filters EMPTY = new Filters(List.of(), List.of(), List.of(), List.of(), List.of(), HealTarget.EITHER);

        public static final Codec<Filters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("entity_ids", List.of()).forGetter(Filters::entityIds),
                        FlexibleListCodec.listOrSingle(TagKey.codec(Registries.ENTITY_TYPE)).optionalFieldOf("entity_tags", List.of()).forGetter(Filters::entityTags),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_ids", List.of()).forGetter(Filters::inSpellIds),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_pools", List.of()).forGetter(Filters::inSpellPools),
                        FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("in_spell_schools", List.of()).forGetter(Filters::inSpellSchools),
                        HealTarget.CODEC.optionalFieldOf("heal_target", HealTarget.EITHER).forGetter(Filters::healTarget)
                ).apply(instance, Filters::new)
        );
    }

    public static final MapCodec<DoHealingAmountTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("amount").forGetter(DoHealingAmountTask::amount),
                    Filters.CODEC.optionalFieldOf("filters", Filters.EMPTY).forGetter(DoHealingAmountTask::filters),
                    Codec.INT.optionalFieldOf("task_order").forGetter(DoHealingAmountTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(DoHealingAmountTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(DoHealingAmountTask::textureOverrideId)
            ).apply(instance, DoHealingAmountTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("do_healing_amount");
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
                        "heal_amount", String.valueOf((int) amount),
                        "current_healing", String.valueOf(current)
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.do_healing_amount";
    }

    @Override
    public int getTargetAmount() {
        return (int) Math.round(amount);
    }

    // castSpellId only matters for the others-branch; self-healing needs no spell attribution
    public boolean matches(LivingEntity healed, boolean isSelf, Optional<ResourceLocation> castSpellId, ServerLevel level) {
        switch (filters.healTarget()) {
            case SELF -> {
                if (!isSelf) return false;
            }
            case OTHERS -> {
                if (isSelf) return false;
            }
            case EITHER -> {
            }
        }

        if (isSelf) {
            return true;
        }

        ResourceLocation healedId = BuiltInRegistries.ENTITY_TYPE.getKey(healed.getType());
        if (!filters.entityIds().isEmpty() && filters.entityIds().stream().noneMatch(id -> healedId.equals(id))) return false;
        if (!filters.entityTags().isEmpty() && filters.entityTags().stream().noneMatch(tag -> healed.getType().is(tag))) return false;

        if (!filters.inSpellIds().isEmpty() || !filters.inSpellPools().isEmpty() || !filters.inSpellSchools().isEmpty()) {
            if (castSpellId.isEmpty()) return false;
            if (!SpellEngineCompat.matchesSelector(level, castSpellId.get(), filters.inSpellIds(), filters.inSpellPools(), filters.inSpellSchools())) return false;
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ResourceLocation> entityIds = List.of();
        private List<TagKey<EntityType<?>>> entityTags = List.of();
        private List<ResourceLocation> inSpellIds = List.of();
        private List<ResourceLocation> inSpellPools = List.of();
        private List<ResourceLocation> inSpellSchools = List.of();
        private HealTarget healTarget = HealTarget.EITHER;
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

        public Builder healTarget(HealTarget healTarget) {
            this.healTarget = healTarget;
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

        public DoHealingAmountTask build() {
            Filters filters = new Filters(entityIds, entityTags, inSpellIds, inSpellPools, inSpellSchools, healTarget);
            return new DoHealingAmountTask(amount, filters, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

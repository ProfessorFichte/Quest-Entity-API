package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.SpellEngineCompat;
import com.qeapi.quest.QuestProgress;
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

// self-healing is always attributable (vanilla LivingEntity.heal(float) has no source parameter at
// all, unlike damage) - healing of OTHERS can only ever be credited when Spell Engine is loaded and
// a nearby player recently cast a healing spell (same best-effort heuristic EntityKillTask's spell
// filters use); there is no vanilla mechanic that heals a separate target anyway (potions/
// regeneration/golden apples/beacons only ever heal the drinker/wearer)
public record DoHealingAmountTask(
        Optional<ResourceLocation> entityId,
        Optional<TagKey<EntityType<?>>> entityTag,
        List<ResourceLocation> entityIds,
        Optional<ResourceLocation> inSpellId,
        Optional<ResourceLocation> inSpellPool,
        Optional<ResourceLocation> inSpellSchool,
        HealTarget healTarget,
        double amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/do_healing_amount_default.png");

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

    public static final MapCodec<DoHealingAmountTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("entity_id").forGetter(DoHealingAmountTask::entityId),
                    TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("entity_tag").forGetter(DoHealingAmountTask::entityTag),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("entity_ids", List.of()).forGetter(DoHealingAmountTask::entityIds),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_id").forGetter(DoHealingAmountTask::inSpellId),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_pool").forGetter(DoHealingAmountTask::inSpellPool),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_school").forGetter(DoHealingAmountTask::inSpellSchool),
                    HealTarget.CODEC.optionalFieldOf("heal_target", HealTarget.EITHER).forGetter(DoHealingAmountTask::healTarget),
                    Codec.DOUBLE.fieldOf("amount").forGetter(DoHealingAmountTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(DoHealingAmountTask::textureOverrideId)
            ).apply(instance, DoHealingAmountTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("do_healing_amount");
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
        return "task.qe_api.do_healing_amount";
    }

    @Override
    public int getTargetAmount() {
        return (int) Math.round(amount);
    }

    // castSpellId is only relevant (and only ever present) for the others-branch - self-healing
    // works unconditionally, with no spell attribution required at all
    public boolean matches(LivingEntity healed, boolean isSelf, Optional<ResourceLocation> castSpellId, ServerLevel level) {
        switch (healTarget) {
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
        if (entityId.isPresent() && !healedId.equals(entityId.get())) return false;
        if (!entityIds.isEmpty() && entityIds.stream().noneMatch(id -> healedId.equals(id))) return false;
        if (entityTag.isPresent() && !healed.getType().is(entityTag.get())) return false;

        if (inSpellId.isPresent() || inSpellPool.isPresent() || inSpellSchool.isPresent()) {
            if (castSpellId.isEmpty()) return false;
            if (!SpellEngineCompat.matchesSelector(level, castSpellId.get(), inSpellId, inSpellPool, inSpellSchool)) return false;
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> entityId = Optional.empty();
        private Optional<TagKey<EntityType<?>>> entityTag = Optional.empty();
        private List<ResourceLocation> entityIds = List.of();
        private Optional<ResourceLocation> inSpellId = Optional.empty();
        private Optional<ResourceLocation> inSpellPool = Optional.empty();
        private Optional<ResourceLocation> inSpellSchool = Optional.empty();
        private HealTarget healTarget = HealTarget.EITHER;
        private double amount = 1.0;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

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

        public Builder inSpellSchool(ResourceLocation spellSchool) {
            this.inSpellSchool = Optional.of(spellSchool);
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

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public DoHealingAmountTask build() {
            return new DoHealingAmountTask(entityId, entityTag, entityIds, inSpellId, inSpellPool,
                    inSpellSchool, healTarget, amount, textureOverrideId);
        }
    }
}

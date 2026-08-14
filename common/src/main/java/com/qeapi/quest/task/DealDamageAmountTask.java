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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// no entity_id/entity_tag/entity_ids matches any living target. amount is a total-damage-dealt
// threshold, not a hit count - progress accumulates the real per-hit amount (see
// QuestEventHandler.onDamageDealt), not a flat +1 per hit
public record DealDamageAmountTask(
        Optional<ResourceLocation> entityId,
        Optional<TagKey<EntityType<?>>> entityTag,
        List<ResourceLocation> entityIds,
        List<ResourceLocation> damageTypes,
        Optional<ResourceLocation> inSpellId,
        Optional<ResourceLocation> inSpellPool,
        Optional<ResourceLocation> inSpellSchool,
        double amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/deal_damage_amount_default.png");

    public static final MapCodec<DealDamageAmountTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("entity_id").forGetter(DealDamageAmountTask::entityId),
                    TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("entity_tag").forGetter(DealDamageAmountTask::entityTag),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("entity_ids", List.of()).forGetter(DealDamageAmountTask::entityIds),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("damage_types", List.of()).forGetter(DealDamageAmountTask::damageTypes),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_id").forGetter(DealDamageAmountTask::inSpellId),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_pool").forGetter(DealDamageAmountTask::inSpellPool),
                    ResourceLocation.CODEC.optionalFieldOf("in_spell_school").forGetter(DealDamageAmountTask::inSpellSchool),
                    Codec.DOUBLE.fieldOf("amount").forGetter(DealDamageAmountTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(DealDamageAmountTask::textureOverrideId)
            ).apply(instance, DealDamageAmountTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("deal_damage_amount");
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
        if (entityId.isPresent()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId.get());
            return type != null ? type.getDescription().getString() : entityId.get().toString();
        }
        if (!entityIds.isEmpty()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityIds.get(0));
            String firstName = type != null ? type.getDescription().getString() : entityIds.get(0).toString();
            return entityIds.size() == 1 ? firstName : firstName + " (+" + (entityIds.size() - 1) + " others)";
        }
        if (entityTag.isPresent()) {
            return "#" + entityTag.get().location();
        }
        return "any entity";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.deal_damage_amount";
    }

    @Override
    public int getTargetAmount() {
        return (int) Math.round(amount);
    }

    public boolean matches(LivingEntity target, DamageSource source, Level level, ServerPlayer player) {
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        if (entityId.isPresent() && !targetId.equals(entityId.get())) {
            return false;
        }
        if (!entityIds.isEmpty() && entityIds.stream().noneMatch(id -> targetId.equals(id))) {
            return false;
        }
        if (entityTag.isPresent() && !target.getType().is(entityTag.get())) {
            return false;
        }

        if (!damageTypes.isEmpty()) {
            ResourceLocation sourceTypeId = source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
            if (sourceTypeId == null || damageTypes.stream().noneMatch(t -> t.equals(sourceTypeId))) {
                return false;
            }
        }

        if (inSpellId.isPresent() || inSpellPool.isPresent() || inSpellSchool.isPresent()) {
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
            if (!SpellEngineCompat.matchesSelector(serverLevel, recentSpell.get(), inSpellId, inSpellPool, inSpellSchool)) {
                return false;
            }
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
        private List<ResourceLocation> damageTypes = List.of();
        private Optional<ResourceLocation> inSpellId = Optional.empty();
        private Optional<ResourceLocation> inSpellPool = Optional.empty();
        private Optional<ResourceLocation> inSpellSchool = Optional.empty();
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

        public Builder entityIds(String... ids) {
            this.entityIds = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
            return this;
        }

        public Builder damageTypes(ResourceLocation... types) {
            this.damageTypes = List.of(types);
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

        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public DealDamageAmountTask build() {
            return new DealDamageAmountTask(entityId, entityTag, entityIds, damageTypes,
                    inSpellId, inSpellPool, inSpellSchool, amount, textureOverrideId);
        }
    }
}

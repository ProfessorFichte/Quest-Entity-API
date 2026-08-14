package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// no effect_id/effect_ids matches any applied effect. target filters whether the applier must be
// applying it to themselves (SELF), to someone else (OTHERS), or either (EITHER, default) - see
// LivingEntityAddEffectMixin/QuestEventHandler.onEffectApplied for how self/others is determined
public record ApplyStatusEffectTask(
        Optional<ResourceLocation> effectId,
        List<ResourceLocation> effectIds,
        int amount,
        Optional<Integer> minAmplifier,
        Target target,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public enum Target implements StringRepresentable {
        SELF("self"),
        OTHERS("others"),
        EITHER("either");

        public static final Codec<Target> CODEC = StringRepresentable.fromEnum(Target::values);

        private final String name;

        Target(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<ApplyStatusEffectTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("effect_id").forGetter(ApplyStatusEffectTask::effectId),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("effect_ids", List.of()).forGetter(ApplyStatusEffectTask::effectIds),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(ApplyStatusEffectTask::amount),
                    Codec.INT.optionalFieldOf("min_amplifier").forGetter(ApplyStatusEffectTask::minAmplifier),
                    Target.CODEC.optionalFieldOf("target", Target.EITHER).forGetter(ApplyStatusEffectTask::target),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(ApplyStatusEffectTask::textureOverrideId)
            ).apply(instance, ApplyStatusEffectTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("apply_status_effect");
    }

    // no bundled default texture for this task type - the quest GUI renders the real mob-effect
    // icon(s) instead (single icon for one effect, a rotating slideshow for effect_ids with 2+
    // entries), the same way StatusEffectReward already renders its effect_id - see QuestScreen

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "effect_amount", String.valueOf(amount),
                        "current_effects", String.valueOf(current),
                        "effect_name", getEffectDisplayName()
                )
        );
    }

    public String getEffectDisplayName() {
        if (effectId.isPresent()) {
            return effectDisplayName(effectId.get());
        }
        if (!effectIds.isEmpty()) {
            if (effectIds.size() == 1) {
                return effectDisplayName(effectIds.get(0));
            }
            return effectDisplayName(effectIds.get(0)) + " (+" + (effectIds.size() - 1) + " others)";
        }
        return "a status effect";
    }

    private static String effectDisplayName(ResourceLocation id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect != null ? effect.getDisplayName().getString() : id.toString();
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.apply_status_effect";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(MobEffectInstance instance, boolean isSelf) {
        ResourceLocation appliedId = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());

        if (effectId.isPresent() && !effectId.get().equals(appliedId)) {
            return false;
        }
        if (!effectIds.isEmpty() && effectIds.stream().noneMatch(id -> id.equals(appliedId))) {
            return false;
        }
        if (minAmplifier.isPresent() && instance.getAmplifier() < minAmplifier.get()) {
            return false;
        }

        return switch (target) {
            case SELF -> isSelf;
            case OTHERS -> !isSelf;
            case EITHER -> true;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> effectId = Optional.empty();
        private List<ResourceLocation> effectIds = List.of();
        private int amount = 1;
        private Optional<Integer> minAmplifier = Optional.empty();
        private Target target = Target.EITHER;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder effectId(ResourceLocation id) {
            this.effectId = Optional.of(id);
            return this;
        }

        public Builder effectId(String id) {
            return effectId(ResourceLocation.parse(id));
        }

        public Builder effectIds(ResourceLocation... ids) {
            this.effectIds = List.of(ids);
            return this;
        }

        public Builder effectIds(String... ids) {
            this.effectIds = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder minAmplifier(int minAmplifier) {
            this.minAmplifier = Optional.of(minAmplifier);
            return this;
        }

        public Builder target(Target target) {
            this.target = target;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public ApplyStatusEffectTask build() {
            return new ApplyStatusEffectTask(effectId, effectIds, amount, minAmplifier, target, textureOverrideId);
        }
    }
}

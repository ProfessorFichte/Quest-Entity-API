package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.FlexibleListCodec;
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

// self/others determination happens in LivingEntityAddEffectMixin/QuestEventHandler.onEffectApplied
public record ApplyStatusEffectTask(
        List<ResourceLocation> effectIds,
        int amount,
        Filters filters,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
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

    public record Filters(Optional<Integer> minAmplifier, Target target) {
        public static final Filters EMPTY = new Filters(Optional.empty(), Target.EITHER);

        public static final Codec<Filters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.optionalFieldOf("min_amplifier").forGetter(Filters::minAmplifier),
                        Target.CODEC.optionalFieldOf("target", Target.EITHER).forGetter(Filters::target)
                ).apply(instance, Filters::new)
        );
    }

    public static final MapCodec<ApplyStatusEffectTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("effect_ids", List.of()).forGetter(ApplyStatusEffectTask::effectIds),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(ApplyStatusEffectTask::amount),
                    Filters.CODEC.optionalFieldOf("filters", Filters.EMPTY).forGetter(ApplyStatusEffectTask::filters),
                    Codec.INT.optionalFieldOf("task_order").forGetter(ApplyStatusEffectTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(ApplyStatusEffectTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(ApplyStatusEffectTask::textureOverrideId)
            ).apply(instance, ApplyStatusEffectTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("apply_status_effect");
    }

    // no bundled default texture - QuestScreen renders the real mob-effect icon(s) instead, same as StatusEffectReward
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
        return "task.quest_api.apply_status_effect";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(MobEffectInstance instance, boolean isSelf) {
        ResourceLocation appliedId = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());

        if (!effectIds.isEmpty() && effectIds.stream().noneMatch(id -> id.equals(appliedId))) {
            return false;
        }
        if (filters.minAmplifier().isPresent() && instance.getAmplifier() < filters.minAmplifier().get()) {
            return false;
        }

        return switch (filters.target()) {
            case SELF -> isSelf;
            case OTHERS -> !isSelf;
            case EITHER -> true;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<ResourceLocation> effectIds = List.of();
        private int amount = 1;
        private Optional<Integer> minAmplifier = Optional.empty();
        private Target target = Target.EITHER;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder effectId(ResourceLocation id) {
            return effectIds(id);
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

        public ApplyStatusEffectTask build() {
            Filters filters = new Filters(minAmplifier, target);
            return new ApplyStatusEffectTask(effectIds, amount, filters, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

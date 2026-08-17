package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

// a pool can be completed by binding its last spell or by creating a pre-made book - Spell Engine fires a different trigger for each, so this listens to both (SpellBindingCriteriaMixin, SpellBookCreationCriteriaMixin)
public record SpellPoolCompleteTask(
        Optional<ResourceLocation> spellPool,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<SpellPoolCompleteTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("spell_pool").forGetter(SpellPoolCompleteTask::spellPool),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(SpellPoolCompleteTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(SpellPoolCompleteTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(SpellPoolCompleteTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SpellPoolCompleteTask::textureOverrideId)
            ).apply(instance, SpellPoolCompleteTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return ResourceLocation.fromNamespaceAndPath("spell_engine", "spell_pool_complete");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String poolName = spellPool.map(id -> "#" + id).orElse("a spell pool");

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "complete_amount", String.valueOf(amount),
                        "current_completions", String.valueOf(current),
                        "spell_pool_name", poolName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.spell_pool_complete";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ResourceLocation completedSpellPool) {
        if (completedSpellPool == null) return false;
        return spellPool.isEmpty() || spellPool.get().equals(completedSpellPool);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> spellPool = Optional.empty();
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder spellPool(ResourceLocation pool) {
            this.spellPool = Optional.of(pool);
            return this;
        }

        public Builder spellPool(String pool) {
            return spellPool(ResourceLocation.parse(pool));
        }

        public Builder amount(int amount) {
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

        public SpellPoolCompleteTask build() {
            return new SpellPoolCompleteTask(spellPool, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

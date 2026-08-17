package com.qeapi.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

// Advancement trigger fired when a player completes a quest.
// Advancement JSON: "trigger": "quest_api:quest_complete", "conditions": { "quest_id": "..." }
public class QuestCompleteTrigger extends SimpleCriterionTrigger<QuestCompleteTrigger.TriggerInstance> {

    public static final QuestCompleteTrigger INSTANCE = new QuestCompleteTrigger();
    public static final ResourceLocation ID = QuestAPI.id("quest_complete");

    private QuestCompleteTrigger() {}

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation questId) {
        QuestAPI.LOGGER.debug("Triggering quest_complete advancement criterion for {} completing {}",
                player.getName().getString(), questId);
        this.trigger(player, instance -> instance.matches(questId));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Optional<ResourceLocation> questId
    ) implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        ResourceLocation.CODEC.optionalFieldOf("quest_id").forGetter(TriggerInstance::questId)
                ).apply(instance, TriggerInstance::new)
        );

        public boolean matches(ResourceLocation completedQuestId) {
            // empty questId = match any quest
            if (questId.isEmpty()) {
                return true;
            }
            return questId.get().equals(completedQuestId);
        }

        public static Criterion<TriggerInstance> anyQuest() {
            return INSTANCE.createCriterion(new TriggerInstance(Optional.empty(), Optional.empty()));
        }

        public static Criterion<TriggerInstance> quest(ResourceLocation questId) {
            return INSTANCE.createCriterion(new TriggerInstance(Optional.empty(), Optional.of(questId)));
        }

        public static Criterion<TriggerInstance> quest(String questId) {
            return quest(ResourceLocation.parse(questId));
        }
    }
}

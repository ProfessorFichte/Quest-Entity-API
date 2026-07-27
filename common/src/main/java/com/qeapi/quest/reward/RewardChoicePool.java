package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A pool of reward options where the player must pick exactly {@code pick} of them
 * in the quest GUI before claiming - an alternative to the flat, unconditional
 * {@code rewards} list on a Quest. Not itself a QuestReward, since the selection is
 * per-player, per-claim state rather than a static property of the reward.
 */
public record RewardChoicePool(
        List<QuestReward> options,
        int pick
) {
    public static final Codec<RewardChoicePool> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    QuestReward.CODEC.listOf().fieldOf("options").forGetter(RewardChoicePool::options),
                    Codec.intRange(1, 64).optionalFieldOf("pick", 1).forGetter(RewardChoicePool::pick)
            ).apply(instance, RewardChoicePool::new)
    );

    // exactly `pick` entries, all in bounds, no duplicates.
    public boolean isValidChoice(List<Integer> chosenIndices) {
        if (chosenIndices.size() != pick) return false;
        if (new java.util.HashSet<>(chosenIndices).size() != chosenIndices.size()) return false;
        for (int index : chosenIndices) {
            if (index < 0 || index >= options.size()) return false;
        }
        return true;
    }
}

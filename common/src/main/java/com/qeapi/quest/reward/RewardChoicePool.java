package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.compat.ModCompatUtil;

import java.util.List;
import java.util.Optional;

/**
 * A pool of reward options where the player must pick exactly {@code pick} of them
 * in the quest GUI before claiming - an alternative to the flat, unconditional
 * {@code rewards} list on a Quest. Not itself a QuestReward, since the selection is
 * per-player, per-claim state rather than a static property of the reward.
 */
public record RewardChoicePool(
        List<Option> options,
        int pick
) {
    // requiredMod: shown/pickable only when that mod is loaded; absent means always available - lets a pool offer per-mod choices (e.g. one option per class mod) that hide themselves when missing.
    public record Option(QuestReward reward, Optional<String> requiredMod) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        QuestReward.CODEC.fieldOf("reward").forGetter(Option::reward),
                        Codec.STRING.optionalFieldOf("required_mod").forGetter(Option::requiredMod)
                ).apply(instance, Option::new)
        );

        public static Option of(QuestReward reward) {
            return new Option(reward, Optional.empty());
        }

        public boolean isAvailable() {
            return requiredMod.isEmpty() || ModCompatUtil.isModLoaded(requiredMod.get());
        }
    }

    public static final Codec<RewardChoicePool> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Option.CODEC.listOf().fieldOf("options").forGetter(RewardChoicePool::options),
                    Codec.intRange(1, 64).optionalFieldOf("pick", 1).forGetter(RewardChoicePool::pick)
            ).apply(instance, RewardChoicePool::new)
    );

    // exactly `pick` entries, all in bounds, no duplicates, and each one actually available
    // (its required_mod, if any, is loaded) - a client can never claim against a filtered-out option.
    public boolean isValidChoice(List<Integer> chosenIndices) {
        if (chosenIndices.size() != pick) return false;
        if (new java.util.HashSet<>(chosenIndices).size() != chosenIndices.size()) return false;
        for (int index : chosenIndices) {
            if (index < 0 || index >= options.size()) return false;
            if (!options.get(index).isAvailable()) return false;
        }
        return true;
    }
}

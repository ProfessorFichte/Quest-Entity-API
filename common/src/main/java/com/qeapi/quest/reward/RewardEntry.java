package com.qeapi.quest.reward;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

// One entry in a Quest's unified `rewards` list - a plain reward (All) or a pick-N-of-M block
// (Choice, wrapping RewardChoicePool). The two JSON shapes are structurally distinct ("reward" vs
// "options"), so Codec.either tells them apart without a discriminator field.
public sealed interface RewardEntry {
    record All(QuestReward reward) implements RewardEntry {}

    record Choice(RewardChoicePool pool) implements RewardEntry {}

    Codec<RewardEntry> CODEC = Codec.either(QuestReward.CODEC, RewardChoicePool.CODEC).xmap(
            either -> either.map(All::new, Choice::new),
            entry -> switch (entry) {
                case All all -> Either.<QuestReward, RewardChoicePool>left(all.reward());
                case Choice choice -> Either.<QuestReward, RewardChoicePool>right(choice.pool());
            }
    );
}

package com.qeapi.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// Only needed to raise a choice_group's requiredCount above its default of 1 -
// see QuestTask.choiceGroup and Quest.effectiveTaskChoiceGroups.
public record TaskChoiceGroup(
        String groupId,
        int requiredCount
) {
    public static final Codec<TaskChoiceGroup> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("group_id").forGetter(TaskChoiceGroup::groupId),
                    Codec.intRange(1, 64).optionalFieldOf("required_count", 1).forGetter(TaskChoiceGroup::requiredCount)
            ).apply(instance, TaskChoiceGroup::new)
    );
}

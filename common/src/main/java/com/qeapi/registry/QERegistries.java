package com.qeapi.registry;

import com.qeapi.QuestAPI;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.QuestReward;
import com.qeapi.quest.task.QuestTask;

// Central registration point for all Quest API registries.
public final class QERegistries {

    private QERegistries() {}

    public static void init() {
        QuestAPI.LOGGER.info("Registering Quest API types");

        QuestTask.registerBuiltInTypes();
        QuestRequirement.registerBuiltInTypes();
        QuestReward.registerBuiltInTypes();
    }
}

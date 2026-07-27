package com.qeapi.registry;

import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.requirement.QuestRequirement;
import com.qeapi.quest.reward.QuestReward;
import com.qeapi.quest.task.QuestTask;

// Central registration point for all Quest Entity API registries.
public final class QERegistries {

    private QERegistries() {}

    public static void init() {
        QuestEntityAPI.LOGGER.info("Registering Quest Entity API types");

        QuestTask.registerBuiltInTypes();
        QuestRequirement.registerBuiltInTypes();
        QuestReward.registerBuiltInTypes();
    }
}

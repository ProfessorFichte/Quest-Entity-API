package com.qeapi.registry;

import com.qeapi.QuestEntityAPI;
import com.qeapi.component.EntityQuestComponent;

// Registration for data component types. Actual registration is done in platform-specific modules.
public final class QEDataComponents {

    private QEDataComponents() {}

    public static void init() {
        QuestEntityAPI.LOGGER.info("Initializing Quest Entity API data components");
    }
}

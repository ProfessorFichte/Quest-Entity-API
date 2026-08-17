package com.qeapi.registry;

import com.qeapi.QuestAPI;
import com.qeapi.component.EntityQuestComponent;

// Registration for data component types. Actual registration is done in platform-specific modules.
public final class QEDataComponents {

    private QEDataComponents() {}

    public static void init() {
        QuestAPI.LOGGER.info("Initializing Quest API data components");
    }
}

package com.qeapi.fabric.client.compat;

import com.qeapi.config.QuestAPIConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;

public final class ModMenuCompatibility implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(QuestAPIConfig.class, parent).get();
    }
}

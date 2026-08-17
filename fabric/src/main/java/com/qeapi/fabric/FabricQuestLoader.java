package com.qeapi.fabric;

import com.qeapi.QuestAPI;
import com.qeapi.data.QuestLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public class FabricQuestLoader extends QuestLoader implements IdentifiableResourceReloadListener {

    @Override
    public ResourceLocation getFabricId() {
        return QuestAPI.id("quest_loader");
    }
}

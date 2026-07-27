package com.qeapi.fabric;

import com.qeapi.QuestEntityAPI;
import com.qeapi.data.EntityQuestTagLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

// Tags load after quests so tag validation can check for pool existence.
public class FabricEntityQuestTagLoader extends EntityQuestTagLoader implements IdentifiableResourceReloadListener {

    @Override
    public ResourceLocation getFabricId() {
        return QuestEntityAPI.id("entity_quest_tag_loader");
    }

    @Override
    public Collection<ResourceLocation> getFabricDependencies() {
        return List.of(QuestEntityAPI.id("quest_loader"));
    }
}

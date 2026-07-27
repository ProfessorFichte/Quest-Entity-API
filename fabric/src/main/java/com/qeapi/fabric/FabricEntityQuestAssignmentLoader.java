package com.qeapi.fabric;

import com.qeapi.QuestEntityAPI;
import com.qeapi.data.EntityQuestAssignmentLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

// Assignments load after quests and tags so references can be validated.
public class FabricEntityQuestAssignmentLoader extends EntityQuestAssignmentLoader
        implements IdentifiableResourceReloadListener {

    @Override
    public ResourceLocation getFabricId() {
        return QuestEntityAPI.id("entity_quest_assignment_loader");
    }

    @Override
    public Collection<ResourceLocation> getFabricDependencies() {
        return List.of(
                QuestEntityAPI.id("quest_loader"),
                QuestEntityAPI.id("entity_quest_tag_loader")
        );
    }
}

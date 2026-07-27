package com.qeapi.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

// Resource reload listener that loads entity quest assignments from
// data/[namespace]/entity_quest_assignment/[path].json. See EntityQuestAssignment for the JSON format.
public class EntityQuestAssignmentLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = "entity_quest_assignment";

    public EntityQuestAssignmentLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        QuestEntityAPI.LOGGER.info("[AssignmentLoader] Loading entity quest assignments...");
        QuestEntityAPI.LOGGER.info("[AssignmentLoader] Found {} JSON files", jsons.size());

        EntityQuestAssignmentManager.clear();

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            QuestEntityAPI.LOGGER.debug("[AssignmentLoader] Processing: {}", id);
            QuestEntityAPI.LOGGER.debug("[AssignmentLoader]   JSON: {}", json);

            try {
                var result = EntityQuestAssignment.CODEC.parse(JsonOps.INSTANCE, json);

                if (result.result().isPresent()) {
                    EntityQuestAssignment assignment = result.result().get();

                    QuestEntityAPI.LOGGER.debug("[AssignmentLoader]   Parsed: entity={}, pool={}, chance={}, villagerData={}",
                            assignment.entityId(), assignment.questPools(), assignment.questChance(), assignment.villagerData());

                    for (String questPoolRef : assignment.questPools()) {
                        if (questPoolRef == null || questPoolRef.isEmpty()) {
                            continue;
                        }

                        // strip legacy "tag:" prefix, e.g. tag:qe_api/farm -> qe_api:farm
                        String tagIdStr = questPoolRef;
                        if (tagIdStr.startsWith("tag:")) {
                            tagIdStr = tagIdStr.substring(4);
                            if (tagIdStr.contains("/") && !tagIdStr.contains(":")) {
                                int slashIndex = tagIdStr.indexOf('/');
                                tagIdStr = tagIdStr.substring(0, slashIndex) + ":" + tagIdStr.substring(slashIndex + 1);
                            }
                            QuestEntityAPI.LOGGER.debug("[AssignmentLoader]   Converted legacy tag format: {} -> {}", questPoolRef, tagIdStr);
                        }

                        ResourceLocation tagId = ResourceLocation.parse(tagIdStr);
                        boolean tagExists = QuestManager.hasEntityQuestTag(tagId);

                        QuestEntityAPI.LOGGER.debug("[AssignmentLoader]   Checking tag exists: {} = {}", tagId, tagExists);

                        if (!tagExists) {
                            QuestEntityAPI.LOGGER.warn("[AssignmentLoader] Assignment {} references non-existent tag: {}. " +
                                    "Expected: data/{}/tags/entity_quests/{}.json",
                                    id, tagId, tagId.getNamespace(), tagId.getPath());
                            QuestEntityAPI.LOGGER.warn("[AssignmentLoader] Available tags: {}", QuestManager.getAllEntityQuestTags().stream()
                                    .map(t -> t.getId().toString()).toList());
                        }
                    }

                    EntityQuestAssignmentManager.register(assignment);
                    successCount++;

                    QuestEntityAPI.LOGGER.info("[AssignmentLoader] Loaded assignment: {} -> entity={}, tag={}, chance={}",
                            id, assignment.entityId(), assignment.questPools(), assignment.questChance());
                } else {
                    String error = result.error().map(e -> e.message()).orElse("Unknown error");
                    QuestEntityAPI.LOGGER.error("[AssignmentLoader] Failed to parse {}: {}", id, error);
                    failCount++;
                }
            } catch (Exception e) {
                QuestEntityAPI.LOGGER.error("[AssignmentLoader] Exception loading {}: {}", id, e.getMessage());
                e.printStackTrace();
                failCount++;
            }
        }

        EntityQuestAssignmentManager.logSummary();
        QuestEntityAPI.LOGGER.info("[AssignmentLoader] Loaded {} entity quest assignments ({} failed)", successCount, failCount);

        QuestEntityAPI.LOGGER.info("[AssignmentLoader] === Registered Assignments ===");
        for (var a : EntityQuestAssignmentManager.getAssignmentsForEntity(ResourceLocation.parse("minecraft:villager"))) {
            QuestEntityAPI.LOGGER.info("[AssignmentLoader]   Villager: pool={}, chance={}, data={}",
                    a.questPools(), a.questChance(), a.villagerData());
        }
    }

    public static ResourceLocation getId() {
        return QuestEntityAPI.id("entity_quest_assignment_loader");
    }
}

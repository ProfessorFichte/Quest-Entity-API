package com.qeapi.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.qeapi.QuestAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

// see EntityQuestAssignment for the JSON format
public class EntityQuestAssignmentLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = "entity_quest_assignment";

    public EntityQuestAssignmentLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        QuestAPI.LOGGER.info("[AssignmentLoader] Loading entity quest assignments...");
        QuestAPI.LOGGER.info("[AssignmentLoader] Found {} JSON files", jsons.size());

        EntityQuestAssignmentManager.clear();

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            QuestAPI.LOGGER.debug("[AssignmentLoader] Processing: {}", id);
            QuestAPI.LOGGER.debug("[AssignmentLoader]   JSON: {}", json);

            try {
                var result = EntityQuestAssignment.CODEC.parse(JsonOps.INSTANCE, json);

                if (result.result().isPresent()) {
                    EntityQuestAssignment assignment = result.result().get();

                    QuestAPI.LOGGER.debug("[AssignmentLoader]   Parsed: entity={}, pool={}, chance={}, villagerData={}",
                            assignment.entityId(), assignment.questPools(), assignment.questChance(), assignment.villagerData());

                    for (String questPoolRef : assignment.questPools()) {
                        if (questPoolRef == null || questPoolRef.isEmpty()) {
                            continue;
                        }

                        // strip legacy "tag:" prefix, e.g. tag:quest_api/farm -> quest_api:farm
                        String tagIdStr = questPoolRef;
                        if (tagIdStr.startsWith("tag:")) {
                            tagIdStr = tagIdStr.substring(4);
                            if (tagIdStr.contains("/") && !tagIdStr.contains(":")) {
                                int slashIndex = tagIdStr.indexOf('/');
                                tagIdStr = tagIdStr.substring(0, slashIndex) + ":" + tagIdStr.substring(slashIndex + 1);
                            }
                            QuestAPI.LOGGER.debug("[AssignmentLoader]   Converted legacy tag format: {} -> {}", questPoolRef, tagIdStr);
                        }

                        ResourceLocation tagId = ResourceLocation.parse(tagIdStr);
                        boolean tagExists = QuestManager.hasEntityQuestTag(tagId);

                        QuestAPI.LOGGER.debug("[AssignmentLoader]   Checking tag exists: {} = {}", tagId, tagExists);

                        if (!tagExists) {
                            QuestAPI.LOGGER.warn("[AssignmentLoader] Assignment {} references non-existent tag: {}. " +
                                    "Expected: data/{}/tags/entity_quests/{}.json",
                                    id, tagId, tagId.getNamespace(), tagId.getPath());
                            QuestAPI.LOGGER.warn("[AssignmentLoader] Available tags: {}", QuestManager.getAllEntityQuestTags().stream()
                                    .map(t -> t.getId().toString()).toList());
                        }
                    }

                    EntityQuestAssignmentManager.register(assignment);
                    successCount++;

                    QuestAPI.LOGGER.info("[AssignmentLoader] Loaded assignment: {} -> entity={}, tag={}, chance={}",
                            id, assignment.entityId(), assignment.questPools(), assignment.questChance());
                } else {
                    String error = result.error().map(e -> e.message()).orElse("Unknown error");
                    QuestAPI.LOGGER.error("[AssignmentLoader] Failed to parse {}: {}", id, error);
                    failCount++;
                }
            } catch (Exception e) {
                QuestAPI.LOGGER.error("[AssignmentLoader] Exception loading {}: {}", id, e.getMessage());
                e.printStackTrace();
                failCount++;
            }
        }

        EntityQuestAssignmentManager.logSummary();
        QuestAPI.LOGGER.info("[AssignmentLoader] Loaded {} entity quest assignments ({} failed)", successCount, failCount);

        QuestAPI.LOGGER.info("[AssignmentLoader] === Registered Assignments ===");
        for (var a : EntityQuestAssignmentManager.getAssignmentsForEntity(ResourceLocation.parse("minecraft:villager"))) {
            QuestAPI.LOGGER.info("[AssignmentLoader]   Villager: pool={}, chance={}, data={}",
                    a.questPools(), a.questChance(), a.villagerData());
        }
    }

    public static ResourceLocation getId() {
        return QuestAPI.id("entity_quest_assignment_loader");
    }
}

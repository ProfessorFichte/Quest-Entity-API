package com.qeapi.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.qeapi.QuestAPI;
import com.qeapi.loot.ConditionalDropLootSupport;
import com.qeapi.quest.Quest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

// each file defines exactly one quest; to offer multiple from one entity, group them under a tags/entity_quests/ tag instead
public class QuestLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = "entity_quest";

    public QuestLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        QuestAPI.LOGGER.info("Loading quest definitions...");

        QuestManager.clear();
        // resolve()'s chest-targeting cache needs rebuilding against the quests this reload is about to register
        ConditionalDropLootSupport.invalidateCache();

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            try {
                if (json.isJsonObject()) {
                    loadSingleQuest(id, json.getAsJsonObject());
                    successCount++;
                } else {
                    QuestAPI.LOGGER.warn("Invalid quest JSON at {}: expected object", id);
                    failCount++;
                }
            } catch (Exception e) {
                QuestAPI.LOGGER.error("Failed to load quest from {}: {}", id, e.getMessage());
                failCount++;
            }
        }

        QuestAPI.LOGGER.info("Loaded {} quest files ({} failed)", successCount, failCount);
        QuestManager.logSummary();
    }

    private void loadSingleQuest(ResourceLocation fileId, JsonObject json) {
        if (!json.has("id")) {
            json.addProperty("id", fileId.toString());
        }

        Quest.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> QuestAPI.LOGGER.error("Failed to parse quest {}: {}", fileId, error))
                .ifPresent(quest -> {
                    Quest questWithId = quest.id().equals(fileId) ? quest : quest.withId(fileId);
                    QuestManager.registerQuest(questWithId);

                    QuestAPI.LOGGER.debug("Loaded quest: {} (tier {})", questWithId.id(), questWithId.tier());
                });
    }

    public static ResourceLocation getId() {
        return QuestAPI.id("quest_loader");
    }
}

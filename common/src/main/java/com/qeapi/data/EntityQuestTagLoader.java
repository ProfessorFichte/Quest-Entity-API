package com.qeapi.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Resource reload listener that loads entity quest tags from data/[namespace]/tags/entity_quests/[path].json.
// Format mirrors vanilla tags: a "values" list of quest IDs, optionally prefixed with "#" to
// reference another tag, plus an optional "replace" flag.
public class EntityQuestTagLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = "tags/entity_quests";

    public EntityQuestTagLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        QuestEntityAPI.LOGGER.info("[TagLoader] Loading entity quest tags...");
        QuestEntityAPI.LOGGER.info("[TagLoader] Found {} JSON files in tags/entity_quests/", jsons.size());

        QuestManager.clearTags();

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            QuestEntityAPI.LOGGER.debug("[TagLoader] Processing tag: {}", id);

            try {
                if (json.isJsonObject()) {
                    JsonObject obj = json.getAsJsonObject();
                    EntityQuestTag tag = parseTag(id, obj);
                    QuestManager.registerEntityQuestTag(tag);
                    successCount++;

                    QuestEntityAPI.LOGGER.info("[TagLoader] Loaded tag: {} -> quests={}, tagRefs={}",
                            id, tag.getQuestIds(), tag.getReferencedTags());
                } else {
                    QuestEntityAPI.LOGGER.warn("[TagLoader] Invalid tag JSON at {}: expected object", id);
                    failCount++;
                }
            } catch (Exception e) {
                QuestEntityAPI.LOGGER.error("[TagLoader] Failed to load tag {}: {}", id, e.getMessage());
                e.printStackTrace();
                failCount++;
            }
        }

        QuestEntityAPI.LOGGER.info("[TagLoader] Loaded {} entity quest tags ({} failed)", successCount, failCount);
    }

    private EntityQuestTag parseTag(ResourceLocation id, JsonObject json) {
        List<ResourceLocation> questIds = new ArrayList<>();
        List<ResourceLocation> referencedTags = new ArrayList<>();

        boolean replace = json.has("replace") && json.get("replace").getAsBoolean();

        if (json.has("values") && json.get("values").isJsonArray()) {
            JsonArray values = json.getAsJsonArray("values");

            for (JsonElement element : values) {
                String value;
                boolean required = true;

                // values can be plain strings or objects with an optional "required" field
                if (element.isJsonPrimitive()) {
                    value = element.getAsString();
                } else if (element.isJsonObject()) {
                    JsonObject valueObj = element.getAsJsonObject();
                    value = valueObj.get("id").getAsString();
                    required = !valueObj.has("required") || valueObj.get("required").getAsBoolean();
                } else {
                    QuestEntityAPI.LOGGER.warn("Invalid value in tag {}: {}", id, element);
                    continue;
                }

                if (value.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.parse(value.substring(1));
                    referencedTags.add(tagId);
                } else {
                    ResourceLocation questId = ResourceLocation.parse(value);
                    questIds.add(questId);
                }
            }
        }

        return new EntityQuestTag(id, questIds, referencedTags);
    }

    public static ResourceLocation getId() {
        return QuestEntityAPI.id("entity_quest_tag_loader");
    }
}

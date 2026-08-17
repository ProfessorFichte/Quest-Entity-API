package com.qeapi.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qeapi.item.QuestItemDefinition;
import com.qeapi.item.QuestItems;
import com.qeapi.quest.Quest;
import com.qeapi.quest.task.BringItemTask;
import com.qeapi.quest.task.ConditionalDropTask;
import com.qeapi.quest.task.QuestTask;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

// re-runs collectQuests() itself, same as LangProvider, so this doesn't depend on unspecified ordering between separately registered DataProviders
public class QuestItemModelProvider implements DataProvider {

    private final PackOutput output;
    private final String modId;
    private final List<QuestProvider> questProviders;

    public QuestItemModelProvider(PackOutput output, String modId, QuestProvider... questProviders) {
        this.output = output;
        this.modId = modId;
        this.questProviders = List.of(questProviders);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        // TreeMap keeps this sorted ascending, required for vanilla's threshold-based override resolution to pick the right entry
        Map<Integer, QuestItemDefinition> definitions = new TreeMap<>();

        for (QuestProvider questProvider : questProviders) {
            questProvider.collectQuests();
            for (Quest quest : questProvider.getAllGeneratedQuests()) {
                for (QuestTask task : quest.tasks()) {
                    collect(task, definitions);
                }
            }
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        JsonArray overrides = new JsonArray();

        for (Map.Entry<Integer, QuestItemDefinition> entry : definitions.entrySet()) {
            int customModelData = entry.getKey();
            QuestItemDefinition definition = entry.getValue();

            JsonObject itemModel = new JsonObject();
            itemModel.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", definition.texture().toString());
            itemModel.add("textures", textures);

            Path modelPath = output.getOutputFolder()
                    .resolve("assets").resolve(modId)
                    .resolve("models/item/quest_item")
                    .resolve(customModelData + ".json");
            futures.add(DataProvider.saveStable(cache, itemModel, modelPath));

            JsonObject override = new JsonObject();
            JsonObject predicate = new JsonObject();
            predicate.addProperty("custom_model_data", customModelData);
            override.add("predicate", predicate);
            override.addProperty("model", modId + ":item/quest_item/" + customModelData);
            overrides.add(override);
        }

        JsonObject baseModel = new JsonObject();
        baseModel.addProperty("parent", "minecraft:item/generated");
        JsonObject baseTextures = new JsonObject();
        baseTextures.addProperty("layer0", modId + ":item/quest_item");
        baseModel.add("textures", baseTextures);
        if (!overrides.isEmpty()) {
            baseModel.add("overrides", overrides);
        }

        Path baseModelPath = output.getOutputFolder()
                .resolve("assets").resolve(modId)
                .resolve("models/item")
                .resolve(QuestItems.QUEST_ITEM_ID.getPath() + ".json");
        futures.add(DataProvider.saveStable(cache, baseModel, baseModelPath));

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void collect(QuestTask task, Map<Integer, QuestItemDefinition> definitions) {
        QuestItemDefinition def = null;
        if (task instanceof BringItemTask bringTask) {
            def = bringTask.questItem().orElse(null);
        } else if (task instanceof ConditionalDropTask dropTask) {
            def = dropTask.questItem().orElse(null);
        }
        if (def == null) return;

        // two different quest items sharing a custom_model_data is a real collision, since the predicate can't tell them apart - fail loudly instead of silently dropping one
        QuestItemDefinition existing = definitions.get(def.customModelData());
        if (existing != null && !existing.texture().equals(def.texture())) {
            throw new IllegalStateException("Two different quest items share custom_model_data "
                    + def.customModelData() + " (" + existing.texture() + " vs " + def.texture()
                    + "). Give each quest item a unique custom_model_data - see the README.");
        }
        definitions.putIfAbsent(def.customModelData(), def);
    }

    @Override
    public String getName() {
        return "Quest API Quest Item Models: " + modId;
    }
}

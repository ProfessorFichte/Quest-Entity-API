package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextFormatting;
import com.qeapi.util.TextMutator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

public record FindStructureTask(
        ResourceLocation structureId,
        Optional<ResourceLocation> textureId,
        boolean providesMap
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/find_structure_default.png");

    public static final MapCodec<FindStructureTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("structure_id").forGetter(FindStructureTask::structureId),
                    ResourceLocation.CODEC.optionalFieldOf("texture_id").forGetter(FindStructureTask::textureId),
                    Codec.BOOL.optionalFieldOf("provides_map", false).forGetter(FindStructureTask::providesMap)
            ).apply(instance, FindStructureTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("find_structure");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        boolean found = isComplete(progress, taskIndex);
        String structureName = getFormattedStructureName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "structure_name", structureName,
                        "status", found ? "Found" : "Not Found"
                )
        );
    }

    // tries the structure.<namespace>.<path> translation key first, falls back to a formatted name
    private String getFormattedStructureName() {
        String translationKey = "structure." + structureId.getNamespace() + "." + structureId.getPath();
        String translated = Component.translatable(translationKey).getString();

        if (!translated.equals(translationKey)) {
            return addModCredit(translated);
        }

        String formattedName = TextFormatting.titleCaseWords(structureId.getPath());
        return addModCredit(formattedName);
    }

    private String addModCredit(String structureName) {
        String namespace = structureId.getNamespace();
        if (namespace.equals("minecraft")) {
            return structureName;
        }
        String modName = TextFormatting.titleCaseWords(namespace);
        return structureName + " (" + modName + ")";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.find_structure";
    }

    @Override
    public int getTargetAmount() {
        return 1;
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureId.orElse(DEFAULT_TEXTURE));
    }

    public static FindStructureTask of(ResourceLocation structureId) {
        return new FindStructureTask(structureId, Optional.empty(), false);
    }

    public static FindStructureTask of(String structureId) {
        return new FindStructureTask(ResourceLocation.parse(structureId), Optional.empty(), false);
    }

    public static FindStructureTask of(ResourceLocation structureId, ResourceLocation textureId) {
        return new FindStructureTask(structureId, Optional.of(textureId), false);
    }

    // grants a one-time treasure map to this structure on quest accept - see PlayerQuestData.hasMapBeenGranted
    public static FindStructureTask ofWithMap(ResourceLocation structureId) {
        return new FindStructureTask(structureId, Optional.empty(), true);
    }

    public static FindStructureTask ofWithMap(String structureId) {
        return new FindStructureTask(ResourceLocation.parse(structureId), Optional.empty(), true);
    }
}

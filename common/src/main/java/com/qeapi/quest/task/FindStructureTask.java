package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.StructureDistanceUtil;
import com.qeapi.util.TextFormatting;
import com.qeapi.util.TextMutator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;

public record FindStructureTask(
        ResourceLocation structureId,
        Filters filters,
        boolean providesMap,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_tasks/find_structure_default.png");

    public record Filters(int maxDistance, Optional<Integer> minPowerLevel) {
        public static final Filters DEFAULT = new Filters(StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());

        public static final Codec<Filters> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.intRange(1, Integer.MAX_VALUE)
                                .optionalFieldOf("max_distance", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                                .forGetter(Filters::maxDistance),
                        Codec.INT.optionalFieldOf("min_power_level").forGetter(Filters::minPowerLevel)
                ).apply(instance, Filters::new)
        );
    }

    public static final MapCodec<FindStructureTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("structure_id").forGetter(FindStructureTask::structureId),
                    Filters.CODEC.optionalFieldOf("filters", Filters.DEFAULT).forGetter(FindStructureTask::filters),
                    Codec.BOOL.optionalFieldOf("provides_map", false).forGetter(FindStructureTask::providesMap),
                    Codec.INT.optionalFieldOf("task_order").forGetter(FindStructureTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(FindStructureTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(FindStructureTask::textureOverrideId)
            ).apply(instance, FindStructureTask::new)
    );

    // empty result gates whether a quest offering this task is selectable for a given quest-giver
    public Optional<BlockPos> resolveNearestStructure(ServerLevel level, BlockPos origin) {
        return StructureDistanceUtil.findNearestStructure(level, origin, structureId, filters.maxDistance());
    }

    // checked against the structure's actual found position, not the origin
    public boolean matchesPowerLevel(ServerLevel level, BlockPos structurePos) {
        if (filters.minPowerLevel().isEmpty()) return true;
        if (!DungeonDifficultyCompat.isLoaded()) return false;
        return DungeonDifficultyCompat.getLocationPowerLevel(level, structurePos) >= filters.minPowerLevel().get();
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("find_structure");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        boolean found = isComplete(progress, taskIndex);
        String structureName = getFormattedStructureName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "structure_name", structureName,
                        "status", found ? "Found" : "Not Found",
                        "max_distance", String.valueOf(filters.maxDistance())
                )
        );
    }

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
        return "task.quest_api.find_structure";
    }

    @Override
    public int getTargetAmount() {
        return 1;
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    public static FindStructureTask of(ResourceLocation structureId) {
        return new FindStructureTask(structureId, Filters.DEFAULT, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static FindStructureTask of(String structureId) {
        return new FindStructureTask(ResourceLocation.parse(structureId), Filters.DEFAULT, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static FindStructureTask of(ResourceLocation structureId, ResourceLocation textureOverrideId) {
        return new FindStructureTask(structureId, Filters.DEFAULT, false, Optional.empty(), Optional.empty(), Optional.of(textureOverrideId));
    }

    // grants a one-time treasure map to this structure on quest accept - see PlayerQuestData.hasMapBeenGranted
    public static FindStructureTask ofWithMap(ResourceLocation structureId) {
        return new FindStructureTask(structureId, Filters.DEFAULT, true, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static FindStructureTask ofWithMap(String structureId) {
        return new FindStructureTask(ResourceLocation.parse(structureId), Filters.DEFAULT, true, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public FindStructureTask withMaxDistance(int maxDistance) {
        return new FindStructureTask(structureId, new Filters(maxDistance, filters.minPowerLevel()), providesMap, taskOrder, choiceGroup, textureOverrideId);
    }

    public FindStructureTask withMinPowerLevel(int minPowerLevel) {
        return new FindStructureTask(structureId, new Filters(filters.maxDistance(), Optional.of(minPowerLevel)), providesMap, taskOrder, choiceGroup, textureOverrideId);
    }

    public FindStructureTask withTaskOrder(int order) {
        return new FindStructureTask(structureId, filters, providesMap, Optional.of(order), choiceGroup, textureOverrideId);
    }

    public FindStructureTask withChoiceGroup(String groupId) {
        return new FindStructureTask(structureId, filters, providesMap, taskOrder, Optional.of(groupId), textureOverrideId);
    }
}

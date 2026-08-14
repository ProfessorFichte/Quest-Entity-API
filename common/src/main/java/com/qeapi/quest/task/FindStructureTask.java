package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
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
        Optional<ResourceLocation> textureOverrideId,
        boolean providesMap,
        int maxDistance,
        Optional<Integer> minPowerLevel
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/find_structure_default.png");

    public static final MapCodec<FindStructureTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("structure_id").forGetter(FindStructureTask::structureId),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(FindStructureTask::textureOverrideId),
                    Codec.BOOL.optionalFieldOf("provides_map", false).forGetter(FindStructureTask::providesMap),
                    Codec.intRange(1, Integer.MAX_VALUE)
                            .optionalFieldOf("max_distance", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                            .forGetter(FindStructureTask::maxDistance),
                    Codec.INT.optionalFieldOf("min_power_level").forGetter(FindStructureTask::minPowerLevel)
            ).apply(instance, FindStructureTask::new)
    );

    // empty if no instance of structureId exists within maxDistance blocks of origin - used to
    // gate whether a quest offering this task should be selectable for a given quest-giver
    public Optional<BlockPos> resolveNearestStructure(ServerLevel level, BlockPos origin) {
        return StructureDistanceUtil.findNearestStructure(level, origin, structureId, maxDistance);
    }

    // true if minPowerLevel is unset, or Dungeon Difficulty is loaded and the found structure's
    // location meets it - checked against the structure's actual found position, not the origin
    public boolean matchesPowerLevel(ServerLevel level, BlockPos structurePos) {
        if (minPowerLevel.isEmpty()) return true;
        if (!DungeonDifficultyCompat.isLoaded()) return false;
        return DungeonDifficultyCompat.getLocationPowerLevel(level, structurePos) >= minPowerLevel.get();
    }

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
                        "status", found ? "Found" : "Not Found",
                        "max_distance", String.valueOf(maxDistance)
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
        return "task.qe_api.find_structure";
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
        return new FindStructureTask(structureId, Optional.empty(), false, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }

    public static FindStructureTask of(String structureId) {
        return new FindStructureTask(ResourceLocation.parse(structureId), Optional.empty(), false, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }

    public static FindStructureTask of(ResourceLocation structureId, ResourceLocation textureOverrideId) {
        return new FindStructureTask(structureId, Optional.of(textureOverrideId), false, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }

    // grants a one-time treasure map to this structure on quest accept - see PlayerQuestData.hasMapBeenGranted
    public static FindStructureTask ofWithMap(ResourceLocation structureId) {
        return new FindStructureTask(structureId, Optional.empty(), true, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }

    public static FindStructureTask ofWithMap(String structureId) {
        return new FindStructureTask(ResourceLocation.parse(structureId), Optional.empty(), true, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }

    public FindStructureTask withMaxDistance(int maxDistance) {
        return new FindStructureTask(structureId, textureOverrideId, providesMap, maxDistance, minPowerLevel);
    }

    // requires Dungeon Difficulty to be loaded and the found structure's location to meet this
    // power level - see matchesPowerLevel
    public FindStructureTask withMinPowerLevel(int minPowerLevel) {
        return new FindStructureTask(structureId, textureOverrideId, providesMap, maxDistance, Optional.of(minPowerLevel));
    }
}

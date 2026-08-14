package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

public record BlocksTraveledTask(
        int distance,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/travel.png");

    public static final MapCodec<BlocksTraveledTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("distance").forGetter(BlocksTraveledTask::distance),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(BlocksTraveledTask::textureOverrideId)
            ).apply(instance, BlocksTraveledTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("blocks_traveled");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), distance);
        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "target_distance", String.valueOf(distance),
                        "current_distance", String.valueOf(current)
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.blocks_traveled";
    }

    @Override
    public int getTargetAmount() {
        return distance;
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    public static BlocksTraveledTask of(int distance) {
        return new BlocksTraveledTask(distance, Optional.empty());
    }

    public static BlocksTraveledTask of(int distance, ResourceLocation textureOverrideId) {
        return new BlocksTraveledTask(distance, Optional.of(textureOverrideId));
    }
}

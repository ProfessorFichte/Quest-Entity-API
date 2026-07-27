package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// A task defines what the player must do to complete a quest.
public sealed interface QuestTask permits
        EntityKillTask,
        FindStructureTask,
        BringItemTask,
        BlocksTraveledTask,
        ItemUsedTask,
        BrewPotionTask,
        SpellCastTask,
        MineBlockTask {

    Map<ResourceLocation, TaskType<?>> TASK_TYPES = new HashMap<>();

    Codec<QuestTask> CODEC = Codec.lazyInitialized(() ->
            ResourceLocation.CODEC.dispatch(
                    "task",
                    QuestTask::getTypeId,
                    id -> {
                        TaskType<?> type = TASK_TYPES.get(id);
                        if (type == null) {
                            throw new IllegalArgumentException("Unknown task type: " + id);
                        }
                        return type.mapCodec();
                    }
            )
    );

    ResourceLocation getTypeId();

    Component getDisplayText(QuestProgress progress, int taskIndex);

    String getDefaultTranslationKey();

    // every built-in task is complete once progress reaches its target amount
    default boolean isComplete(QuestProgress progress, int taskIndex) {
        return progress.getTaskProgress(taskIndex) >= getTargetAmount();
    }

    int getTargetAmount();

    default Optional<ResourceLocation> getDisplayTexture() {
        return Optional.empty();
    }

    static <T extends QuestTask> void registerType(ResourceLocation id, MapCodec<T> codec) {
        TASK_TYPES.put(id, new TaskType<>(id, codec));
    }

    static void registerBuiltInTypes() {
        registerType(QuestEntityAPI.id("entity_kill"), EntityKillTask.CODEC);
        registerType(QuestEntityAPI.id("find_structure"), FindStructureTask.CODEC);
        registerType(QuestEntityAPI.id("bring_item"), BringItemTask.CODEC);
        registerType(QuestEntityAPI.id("blocks_traveled"), BlocksTraveledTask.CODEC);
        registerType(QuestEntityAPI.id("item_used"), ItemUsedTask.CODEC);
        registerType(QuestEntityAPI.id("brew_potion"), BrewPotionTask.CODEC);
        registerType(QuestEntityAPI.id("spell_cast"), SpellCastTask.CODEC);
        registerType(QuestEntityAPI.id("mine_block"), MineBlockTask.CODEC);
    }

    record TaskType<T extends QuestTask>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestTask> mapCodec() {
            return (MapCodec<QuestTask>) (MapCodec<?>) codec;
        }
    }
}

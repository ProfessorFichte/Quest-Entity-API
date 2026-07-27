package com.qeapi.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class QuestProgress {
    private final ResourceLocation questId;
    private final Map<Integer, Integer> taskProgress; // task index -> progress value

    // task_progress map uses string keys since NBT/JSON maps require them
    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("quest_id").forGetter(QuestProgress::getQuestId),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("task_progress")
                            .xmap(
                                    stringMap -> {
                                        Map<Integer, Integer> intMap = new HashMap<>();
                                        stringMap.forEach((k, v) -> intMap.put(Integer.parseInt(k), v));
                                        return intMap;
                                    },
                                    intMap -> {
                                        Map<String, Integer> stringMap = new HashMap<>();
                                        intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                                        return stringMap;
                                    }
                            )
                            .forGetter(p -> p.taskProgress)
            ).apply(instance, QuestProgress::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestProgress> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public QuestProgress decode(RegistryFriendlyByteBuf buf) {
                    ResourceLocation questId = buf.readResourceLocation();
                    int size = buf.readVarInt();
                    Map<Integer, Integer> progress = new HashMap<>();
                    for (int i = 0; i < size; i++) {
                        int taskIndex = buf.readVarInt();
                        int taskProgress = buf.readVarInt();
                        progress.put(taskIndex, taskProgress);
                    }
                    return new QuestProgress(questId, progress);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, QuestProgress value) {
                    buf.writeResourceLocation(value.questId);
                    buf.writeVarInt(value.taskProgress.size());
                    value.taskProgress.forEach((k, v) -> {
                        buf.writeVarInt(k);
                        buf.writeVarInt(v);
                    });
                }
            };

    public QuestProgress(ResourceLocation questId) {
        this.questId = questId;
        this.taskProgress = new HashMap<>();
    }

    public QuestProgress(ResourceLocation questId, Map<Integer, Integer> taskProgress) {
        this.questId = questId;
        this.taskProgress = new HashMap<>(taskProgress);
    }

    public ResourceLocation getQuestId() {
        return questId;
    }

    public int getTaskProgress(int taskIndex) {
        return taskProgress.getOrDefault(taskIndex, 0);
    }

    public void setTaskProgress(int taskIndex, int progress) {
        taskProgress.put(taskIndex, progress);
    }

    public void incrementTaskProgress(int taskIndex) {
        taskProgress.merge(taskIndex, 1, Integer::sum);
    }

    public void addTaskProgress(int taskIndex, int amount) {
        taskProgress.merge(taskIndex, amount, Integer::sum);
    }

    public Map<Integer, Integer> getAllTaskProgress() {
        return new HashMap<>(taskProgress);
    }

    public void reset() {
        taskProgress.clear();
    }

    public QuestProgress copy() {
        return new QuestProgress(questId, new HashMap<>(taskProgress));
    }

    @Override
    public String toString() {
        return "QuestProgress{questId=" + questId + ", progress=" + taskProgress + "}";
    }
}

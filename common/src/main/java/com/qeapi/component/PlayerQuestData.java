package com.qeapi.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.quest.QuestProgress;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

// Stores quest progress data for a player across all entities; attached to the player and persists across sessions.
public class PlayerQuestData {
    // Entity UUID -> Quest Progress
    private final Map<UUID, QuestProgress> entityProgress;

    // Keys of the form "<questId>#<taskIndex>" for structure-map-providing tasks that have
    // already granted their one-time map to this player - deliberately NOT cleared alongside
    // entityProgress (accept/decline/re-accept, or even quest completion) so a player can't farm
    // duplicate maps by repeatedly accepting and dismissing the same quest.
    private final Set<String> mapsGranted;

    public static final Codec<PlayerQuestData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, QuestProgress.CODEC)
                            .optionalFieldOf("entity_progress", Map.of())
                            .forGetter(d -> d.entityProgress),
                    Codec.STRING.listOf().optionalFieldOf("maps_granted", List.of())
                            .forGetter(d -> new ArrayList<>(d.mapsGranted))
            ).apply(instance, PlayerQuestData::new)
    );

    public PlayerQuestData() {
        this.entityProgress = new HashMap<>();
        this.mapsGranted = new HashSet<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress, List<String> mapsGranted) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>(mapsGranted);
    }

    public boolean hasMapBeenGranted(String taskKey) {
        return mapsGranted.contains(taskKey);
    }

    public void markMapGranted(String taskKey) {
        mapsGranted.add(taskKey);
    }

    public Optional<QuestProgress> getProgressForEntity(UUID entityUuid) {
        return Optional.ofNullable(entityProgress.get(entityUuid));
    }

    public void setProgressForEntity(UUID entityUuid, QuestProgress progress) {
        entityProgress.put(entityUuid, progress);
    }

    public QuestProgress startQuest(UUID entityUuid, ResourceLocation questId) {
        QuestProgress progress = new QuestProgress(questId);
        entityProgress.put(entityUuid, progress);
        return progress;
    }

    public void clearEntityProgress(UUID entityUuid) {
        entityProgress.remove(entityUuid);
    }

    public Map<UUID, QuestProgress> getAllProgress() {
        return Collections.unmodifiableMap(entityProgress);
    }

    public boolean hasActiveQuestForEntity(UUID entityUuid) {
        return entityProgress.containsKey(entityUuid);
    }

    public Optional<ResourceLocation> getActiveQuestId(UUID entityUuid) {
        QuestProgress progress = entityProgress.get(entityUuid);
        return progress != null ? Optional.of(progress.getQuestId()) : Optional.empty();
    }

    public PlayerQuestData copy() {
        Map<UUID, QuestProgress> newProgress = new HashMap<>();
        entityProgress.forEach((k, v) -> newProgress.put(k, v.copy()));
        return new PlayerQuestData(newProgress, new ArrayList<>(mapsGranted));
    }

    public boolean isEmpty() {
        return entityProgress.isEmpty();
    }
}

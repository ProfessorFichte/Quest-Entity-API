package com.qeapi.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.EntityNameResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.*;

// attached to the player and persists across sessions
public class PlayerQuestData {
    private final Map<UUID, QuestProgress> entityProgress;

    // deliberately NOT cleared alongside entityProgress, so a player can't farm duplicate maps by repeatedly accepting and dismissing the same quest
    private final Set<String> mapsGranted;

    // last known giver position for the Active Quest screen; deliberately not a live lookup so opening the screen never has to scan for the entity
    private final Map<UUID, QuestGiverLocation> entityLocations;

    // resolved once at accept time and fixed for that quest's lifetime; cleared alongside entityProgress since it's meaningless once inactive
    private final Map<UUID, UUID> deliveryTargets;

    // QuestProgress only stores the distinct-so-far count per task, not which biomes were already counted, so the actual id set lives here instead
    private final Map<UUID, Map<Integer, Set<ResourceLocation>>> visitedBiomes;

    // NOT cleared alongside entityProgress - resolvedLines must survive a line quest's completion/dismissal so the root still knows which lines are done
    private final Map<UUID, LineSelectionState> lineSelections;

    // claimedRoots guards against double-claiming: a root becomes claimable (every line resolved) before the claim action has actually run
    // acceptedRoots gates the line picker interactive, mirroring the accept step every other quest requires
    public record LineSelectionState(Optional<String> activeLine, Set<String> resolvedLines,
                                      Set<ResourceLocation> claimedRoots, Set<ResourceLocation> acceptedRoots) {
        public static final Codec<LineSelectionState> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf("active_line").forGetter(LineSelectionState::activeLine),
                        Codec.STRING.listOf().optionalFieldOf("resolved_lines", List.of())
                                .forGetter(state -> new ArrayList<>(state.resolvedLines)),
                        ResourceLocation.CODEC.listOf().optionalFieldOf("claimed_roots", List.of())
                                .forGetter(state -> new ArrayList<>(state.claimedRoots)),
                        ResourceLocation.CODEC.listOf().optionalFieldOf("accepted_roots", List.of())
                                .forGetter(state -> new ArrayList<>(state.acceptedRoots))
                ).apply(instance, (activeLine, resolvedLines, claimedRoots, acceptedRoots) ->
                        new LineSelectionState(activeLine, new HashSet<>(resolvedLines), new HashSet<>(claimedRoots),
                                new HashSet<>(acceptedRoots)))
        );

        public static LineSelectionState empty() {
            return new LineSelectionState(Optional.empty(), Set.of(), Set.of(), Set.of());
        }

        public LineSelectionState withActiveLine(String lineId) {
            return new LineSelectionState(Optional.of(lineId), resolvedLines, claimedRoots, acceptedRoots);
        }

        public LineSelectionState withoutActiveLine() {
            return new LineSelectionState(Optional.empty(), resolvedLines, claimedRoots, acceptedRoots);
        }

        public LineSelectionState withResolvedLine(String lineId) {
            Set<String> newResolved = new HashSet<>(resolvedLines);
            newResolved.add(lineId);
            return new LineSelectionState(Optional.empty(), newResolved, claimedRoots, acceptedRoots);
        }

        public LineSelectionState withClaimedRoot(ResourceLocation rootQuestId) {
            Set<ResourceLocation> newClaimedRoots = new HashSet<>(claimedRoots);
            newClaimedRoots.add(rootQuestId);
            return new LineSelectionState(activeLine, resolvedLines, newClaimedRoots, acceptedRoots);
        }

        public LineSelectionState withAcceptedRoot(ResourceLocation rootQuestId) {
            Set<ResourceLocation> newAcceptedRoots = new HashSet<>(acceptedRoots);
            newAcceptedRoots.add(rootQuestId);
            return new LineSelectionState(activeLine, resolvedLines, claimedRoots, newAcceptedRoots);
        }

        public LineSelectionState withoutAcceptedRoot(ResourceLocation rootQuestId) {
            Set<ResourceLocation> newAcceptedRoots = new HashSet<>(acceptedRoots);
            newAcceptedRoots.remove(rootQuestId);
            return new LineSelectionState(activeLine, resolvedLines, claimedRoots, newAcceptedRoots);
        }

        public LineSelectionState copy() {
            return new LineSelectionState(activeLine, new HashSet<>(resolvedLines), new HashSet<>(claimedRoots),
                    new HashSet<>(acceptedRoots));
        }
    }

    public record QuestGiverLocation(ResourceLocation entityType, ResourceLocation dimension, BlockPos pos, String displayName) {
        public static final Codec<QuestGiverLocation> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("entity_type").forGetter(QuestGiverLocation::entityType),
                        ResourceLocation.CODEC.fieldOf("dimension").forGetter(QuestGiverLocation::dimension),
                        BlockPos.CODEC.fieldOf("pos").forGetter(QuestGiverLocation::pos),
                        Codec.STRING.optionalFieldOf("display_name", "").forGetter(QuestGiverLocation::displayName)
                ).apply(instance, QuestGiverLocation::new)
        );

        // cached rather than resolved live, since the giving entity may not be loaded when the Active Quests screen is opened
        public static QuestGiverLocation of(Entity entity) {
            return new QuestGiverLocation(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    entity.level().dimension().location(),
                    entity.blockPosition(),
                    EntityNameResolver.resolve(entity)
            );
        }
    }

    private static final Codec<Set<ResourceLocation>> BIOME_SET_CODEC = ResourceLocation.CODEC.listOf()
            .xmap(HashSet::new, ArrayList::new);

    private static final Codec<Map<Integer, Set<ResourceLocation>>> TASK_VISITED_BIOMES_CODEC =
            Codec.unboundedMap(Codec.STRING, BIOME_SET_CODEC).xmap(
                    stringMap -> {
                        Map<Integer, Set<ResourceLocation>> intMap = new HashMap<>();
                        stringMap.forEach((k, v) -> intMap.put(Integer.parseInt(k), v));
                        return intMap;
                    },
                    intMap -> {
                        Map<String, Set<ResourceLocation>> stringMap = new HashMap<>();
                        intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                        return stringMap;
                    }
            );

    public static final Codec<PlayerQuestData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, QuestProgress.CODEC)
                            .optionalFieldOf("entity_progress", Map.of())
                            .forGetter(d -> d.entityProgress),
                    Codec.STRING.listOf().optionalFieldOf("maps_granted", List.of())
                            .forGetter(d -> new ArrayList<>(d.mapsGranted)),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, QuestGiverLocation.CODEC)
                            .optionalFieldOf("entity_locations", Map.of())
                            .forGetter(d -> d.entityLocations),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC)
                            .optionalFieldOf("delivery_targets", Map.of())
                            .forGetter(d -> d.deliveryTargets),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, TASK_VISITED_BIOMES_CODEC)
                            .optionalFieldOf("visited_biomes", Map.of())
                            .forGetter(d -> d.visitedBiomes),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, LineSelectionState.CODEC)
                            .optionalFieldOf("line_selections", Map.of())
                            .forGetter(d -> d.lineSelections)
            ).apply(instance, PlayerQuestData::new)
    );

    public PlayerQuestData() {
        this.entityProgress = new HashMap<>();
        this.mapsGranted = new HashSet<>();
        this.entityLocations = new HashMap<>();
        this.deliveryTargets = new HashMap<>();
        this.visitedBiomes = new HashMap<>();
        this.lineSelections = new HashMap<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>();
        this.entityLocations = new HashMap<>();
        this.deliveryTargets = new HashMap<>();
        this.visitedBiomes = new HashMap<>();
        this.lineSelections = new HashMap<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress, List<String> mapsGranted) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>(mapsGranted);
        this.entityLocations = new HashMap<>();
        this.deliveryTargets = new HashMap<>();
        this.visitedBiomes = new HashMap<>();
        this.lineSelections = new HashMap<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress, List<String> mapsGranted,
                            Map<UUID, QuestGiverLocation> entityLocations) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>(mapsGranted);
        this.entityLocations = new HashMap<>(entityLocations);
        this.deliveryTargets = new HashMap<>();
        this.visitedBiomes = new HashMap<>();
        this.lineSelections = new HashMap<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress, List<String> mapsGranted,
                            Map<UUID, QuestGiverLocation> entityLocations, Map<UUID, UUID> deliveryTargets) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>(mapsGranted);
        this.entityLocations = new HashMap<>(entityLocations);
        this.deliveryTargets = new HashMap<>(deliveryTargets);
        this.visitedBiomes = new HashMap<>();
        this.lineSelections = new HashMap<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress, List<String> mapsGranted,
                            Map<UUID, QuestGiverLocation> entityLocations, Map<UUID, UUID> deliveryTargets,
                            Map<UUID, Map<Integer, Set<ResourceLocation>>> visitedBiomes) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>(mapsGranted);
        this.entityLocations = new HashMap<>(entityLocations);
        this.deliveryTargets = new HashMap<>(deliveryTargets);
        this.visitedBiomes = new HashMap<>(visitedBiomes);
        this.lineSelections = new HashMap<>();
    }

    public PlayerQuestData(Map<UUID, QuestProgress> entityProgress, List<String> mapsGranted,
                            Map<UUID, QuestGiverLocation> entityLocations, Map<UUID, UUID> deliveryTargets,
                            Map<UUID, Map<Integer, Set<ResourceLocation>>> visitedBiomes,
                            Map<UUID, LineSelectionState> lineSelections) {
        this.entityProgress = new HashMap<>(entityProgress);
        this.mapsGranted = new HashSet<>(mapsGranted);
        this.entityLocations = new HashMap<>(entityLocations);
        this.deliveryTargets = new HashMap<>(deliveryTargets);
        this.visitedBiomes = new HashMap<>(visitedBiomes);
        this.lineSelections = new HashMap<>(lineSelections);
    }

    public void recordEntityLocation(UUID entityUuid, QuestGiverLocation location) {
        entityLocations.put(entityUuid, location);
    }

    public void recordEntityLocation(Entity entity) {
        entityLocations.put(entity.getUUID(), QuestGiverLocation.of(entity));
    }

    public Optional<QuestGiverLocation> getEntityLocation(UUID entityUuid) {
        return Optional.ofNullable(entityLocations.get(entityUuid));
    }

    public void recordDeliveryTarget(UUID giverUuid, UUID targetUuid) {
        deliveryTargets.put(giverUuid, targetUuid);
    }

    public Optional<UUID> getDeliveryTarget(UUID giverUuid) {
        return Optional.ofNullable(deliveryTargets.get(giverUuid));
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
        entityLocations.remove(entityUuid);
        deliveryTargets.remove(entityUuid);
        visitedBiomes.remove(entityUuid);
    }

    public Set<ResourceLocation> getVisitedBiomes(UUID giverUuid, int taskIndex) {
        return visitedBiomes
                .getOrDefault(giverUuid, Map.of())
                .getOrDefault(taskIndex, Set.of());
    }

    // returns true if biomeId wasn't already counted for this (giver, task) pair
    public boolean recordVisitedBiome(UUID giverUuid, int taskIndex, ResourceLocation biomeId) {
        Set<ResourceLocation> visited = visitedBiomes
                .computeIfAbsent(giverUuid, k -> new HashMap<>())
                .computeIfAbsent(taskIndex, k -> new HashSet<>());
        return visited.add(biomeId);
    }

    public LineSelectionState getLineSelection(UUID giverUuid) {
        return lineSelections.getOrDefault(giverUuid, LineSelectionState.empty());
    }

    public void setLineSelection(UUID giverUuid, LineSelectionState state) {
        lineSelections.put(giverUuid, state);
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

        Map<UUID, Map<Integer, Set<ResourceLocation>>> newVisitedBiomes = new HashMap<>();
        visitedBiomes.forEach((giver, byTask) -> {
            Map<Integer, Set<ResourceLocation>> copiedByTask = new HashMap<>();
            byTask.forEach((taskIndex, biomes) -> copiedByTask.put(taskIndex, new HashSet<>(biomes)));
            newVisitedBiomes.put(giver, copiedByTask);
        });

        Map<UUID, LineSelectionState> newLineSelections = new HashMap<>();
        lineSelections.forEach((k, v) -> newLineSelections.put(k, v.copy()));

        return new PlayerQuestData(newProgress, new ArrayList<>(mapsGranted), new HashMap<>(entityLocations),
                new HashMap<>(deliveryTargets), newVisitedBiomes, newLineSelections);
    }

    public boolean isEmpty() {
        return entityProgress.isEmpty();
    }
}

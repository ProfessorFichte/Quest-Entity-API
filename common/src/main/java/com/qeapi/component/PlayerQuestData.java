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

    // Keys of the form "<questId>#<taskIndex>" for structure-map-providing tasks that have
    // already granted their one-time map to this player - deliberately NOT cleared alongside
    // entityProgress (accept/decline/re-accept, or even quest completion) so a player can't farm
    // duplicate maps by repeatedly accepting and dismissing the same quest.
    private final Set<String> mapsGranted;

    // Entity UUID -> last known position of a quest giver this player has an active quest with -
    // used by the Active Quest screen (item 2) to show where to go back to. Recorded at accept
    // time and refreshed opportunistically whenever the entity is next seen nearby (see
    // checkNearbyQuestEntities); deliberately not a live lookup so opening the screen never has to
    // scan for the entity itself. Cleared alongside entityProgress (unlike mapsGranted above), since
    // there's nothing left to navigate back to once the quest isn't active anymore.
    private final Map<UUID, QuestGiverLocation> entityLocations;

    // Giver entity UUID -> resolved deliver_item target's entity UUID, for whichever active quest
    // that giver granted - resolved once at accept time (see
    // QuestEventHandler.resolveDeliveryTargetIfNeeded) and fixed for that quest's lifetime, same
    // shape as entityLocations above. Cleared alongside entityProgress since it's meaningless once
    // the quest isn't active anymore.
    private final Map<UUID, UUID> deliveryTargets;

    // Giver entity UUID -> task index -> set of distinct biome ids already visited toward that
    // visit_biome task - QuestProgress only stores an int per task index, which can hold the
    // distinct-so-far *count* but not which biomes were already counted, so the actual id set lives
    // here instead, same per-giver keying convention as the maps above. Cleared alongside
    // entityProgress, same reasoning as entityLocations/deliveryTargets.
    private final Map<UUID, Map<Integer, Set<ResourceLocation>>> visitedBiomes;

    // Giver entity UUID -> this player's quest-line selection state for that giver's
    // quest_line_choice root: which line (if any) is currently active, and which line ids have
    // already been fully resolved (every questLine-tagged quest sharing that id completed - see
    // the resolve-hook in QuestEntityAccess.completeQuest/the platform handleClaimRewards). NOT
    // cleared alongside entityProgress: a line-tagged quest's own progress lives in entityProgress
    // like any other quest, but resolvedLines must survive that quest's completion/dismissal so the
    // root knows which lines are already done. See DismissQuestPacket's handler for how canceling
    // the active line-tagged quest clears just activeLine here.
    private final Map<UUID, LineSelectionState> lineSelections;

    // claimedRoots: quest-line root ids (Quest.id()) whose root-level reward has already been
    // granted (see QuestScreen's root-claim button/ClaimQuestLineRootPacket) - separate from
    // resolvedLines because a root becomes claimable (every line resolved) before the claim action
    // itself has actually run; this guards against claiming it twice.
    // acceptedRoots: quest-line root ids the player has accepted via the normal checkbox flow -
    // the line picker in QuestScreen only becomes interactive once its root is in this set,
    // mirroring the accept step every other quest requires before it can be interacted with.
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

        // display_name is cached here (rather than resolved live from the Active Quests screen)
        // because the giving entity may not be loaded when that screen is opened - same "last
        // known" reasoning as pos itself.
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

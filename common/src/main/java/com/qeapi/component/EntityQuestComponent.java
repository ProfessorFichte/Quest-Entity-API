package com.qeapi.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.data.QuestManager;
import com.qeapi.quest.QuestPool;
import com.qeapi.quest.QuestProgress;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

// questPoolId always references a tag under tags/entity_quests/; "quest_api:no_quest" is a special marker meaning the entity was checked but has no quests
public record EntityQuestComponent(
        ResourceLocation questPoolId,
        Map<UUID, ActiveQuestData> activeQuests,
        Map<UUID, Set<ResourceLocation>> completedQuests,
        Map<UUID, Map<ResourceLocation, Long>> questCompletionTimes,  // player -> quest -> in-game day-time last completed, for Quest.repeatAfterDays
        Map<UUID, String> chosenQuestGroups,  // player -> quest_group chosen for this pool, via SetQuestGroupReward
        Map<UUID, Long> questCooldowns,  // Player UUID -> cooldown end time (System.currentTimeMillis)
        Optional<ResourceLocation> acceptQuestSoundOverride,  // from the EntityQuestAssignment that created this entity's quests, if any
        Optional<ResourceLocation> finishQuestSoundOverride
) {
    public static final Codec<EntityQuestComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("quest_pool").forGetter(EntityQuestComponent::questPoolId),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, ActiveQuestData.CODEC)
                            .optionalFieldOf("active_quests", Map.of())
                            .forGetter(EntityQuestComponent::activeQuests),
                    Codec.unboundedMap(
                            UUIDUtil.STRING_CODEC,
                            ResourceLocation.CODEC.listOf().<Set<ResourceLocation>>xmap(HashSet::new, ArrayList::new)
                    ).optionalFieldOf("completed_quests", Map.of()).forGetter(EntityQuestComponent::completedQuests),
                    Codec.unboundedMap(
                            UUIDUtil.STRING_CODEC,
                            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG)
                    ).optionalFieldOf("quest_completion_times", Map.of())
                            .forGetter(EntityQuestComponent::questCompletionTimes),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                            .optionalFieldOf("chosen_quest_groups", Map.of())
                            .forGetter(EntityQuestComponent::chosenQuestGroups),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                            .optionalFieldOf("quest_cooldowns", Map.of())
                            .forGetter(EntityQuestComponent::questCooldowns),
                    ResourceLocation.CODEC.optionalFieldOf("accept_quest_sound_override")
                            .forGetter(EntityQuestComponent::acceptQuestSoundOverride),
                    ResourceLocation.CODEC.optionalFieldOf("finish_quest_sound_override")
                            .forGetter(EntityQuestComponent::finishQuestSoundOverride)
            ).apply(instance, EntityQuestComponent::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityQuestComponent> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    // Marks an entity as checked-but-no-quest, so we don't retry the quest_chance roll on every interaction.
    private static final ResourceLocation NO_QUEST_MARKER = ResourceLocation.fromNamespaceAndPath("quest_api", "no_quest");

    public static EntityQuestComponent create(ResourceLocation tagId) {
        return create(tagId, Optional.empty(), Optional.empty());
    }

    // sound overrides come from the EntityQuestAssignment that resolved this entity's quest pool, if any
    public static EntityQuestComponent create(ResourceLocation tagId, Optional<ResourceLocation> acceptQuestSoundOverride,
                                               Optional<ResourceLocation> finishQuestSoundOverride) {
        QuestAPI.LOGGER.debug("[EntityQuestComponent] Creating component with tag ID: {}", tagId);
        return new EntityQuestComponent(tagId, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public static EntityQuestComponent createNoQuest() {
        QuestAPI.LOGGER.debug("[EntityQuestComponent] Creating NO_QUEST marker component");
        return new EntityQuestComponent(NO_QUEST_MARKER, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                Optional.empty(), Optional.empty());
    }

    public boolean isNoQuestMarker() {
        return NO_QUEST_MARKER.equals(questPoolId);
    }

    public boolean hasAnyPlayerInteraction() {
        boolean hasInteraction = !activeQuests.isEmpty() || !completedQuests.isEmpty();
        QuestAPI.LOGGER.debug("[EntityQuestComponent] hasAnyPlayerInteraction: {} (activeQuests={}, completedQuests={})",
                hasInteraction, activeQuests.size(), completedQuests.size());
        return hasInteraction;
    }

    // Resolves questPoolId's tag to its (single, synthetic) pool.
    public List<QuestPool> getAllQuestPools() {
        if (isNoQuestMarker()) {
            QuestAPI.LOGGER.debug("[EntityQuestComponent] getAllQuestPools called on NO_QUEST marker, returning empty");
            return List.of();
        }

        QuestAPI.LOGGER.debug("[EntityQuestComponent] getAllQuestPools for tag: {}", questPoolId);
        Optional<QuestPool> pool = QuestManager.getQuestPoolFromTag(questPoolId);

        if (pool.isEmpty()) {
            QuestAPI.LOGGER.warn("[EntityQuestComponent] No quest pool found for tag: {}. " +
                    "Make sure the tag exists at data/{}/tags/entity_quests/{}.json",
                    questPoolId, questPoolId.getNamespace(), questPoolId.getPath());
            return List.of();
        }

        QuestAPI.LOGGER.debug("[EntityQuestComponent] Resolved tag {} to a pool with {} tiers", questPoolId, pool.get().getQuestsByTier().size());
        return List.of(pool.get());
    }

    public Optional<ActiveQuestData> getActiveQuest(UUID playerId) {
        return Optional.ofNullable(activeQuests.get(playerId));
    }

    public boolean hasActiveQuest(UUID playerId) {
        return activeQuests.containsKey(playerId);
    }

    public Set<ResourceLocation> getCompletedQuests(UUID playerId) {
        return completedQuests.getOrDefault(playerId, Set.of());
    }

    public boolean hasCompletedQuest(UUID playerId, ResourceLocation questId) {
        Set<ResourceLocation> completed = completedQuests.get(playerId);
        return completed != null && completed.contains(questId);
    }

    // in-game day-time this quest was last completed, or -1 if never recorded - only meaningful for a quest with Quest.repeatAfterDays set
    public long getCompletionDayTime(UUID playerId, ResourceLocation questId) {
        Map<ResourceLocation, Long> times = questCompletionTimes.get(playerId);
        if (times == null) {
            return -1;
        }
        return times.getOrDefault(questId, -1L);
    }

    // The quest_group this player chose for this pool (see SetQuestGroupReward), if any.
    public Optional<String> getChosenQuestGroup(UUID playerId) {
        return Optional.ofNullable(chosenQuestGroups.get(playerId));
    }

    public EntityQuestComponent withChosenQuestGroup(UUID playerId, String group) {
        Map<UUID, String> newChosenQuestGroups = new HashMap<>(chosenQuestGroups);
        newChosenQuestGroups.put(playerId, group);
        return new EntityQuestComponent(questPoolId, activeQuests, completedQuests, questCompletionTimes,
                newChosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public EntityQuestComponent withoutChosenQuestGroup(UUID playerId) {
        Map<UUID, String> newChosenQuestGroups = new HashMap<>(chosenQuestGroups);
        newChosenQuestGroups.remove(playerId);
        return new EntityQuestComponent(questPoolId, activeQuests, completedQuests, questCompletionTimes,
                newChosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public int getHighestCompletedTier(UUID playerId, Map<ResourceLocation, Integer> questTiers) {
        Set<ResourceLocation> completed = completedQuests.get(playerId);
        if (completed == null || completed.isEmpty()) {
            return 0;
        }

        int maxTier = 0;
        for (ResourceLocation questId : completed) {
            Integer tier = questTiers.get(questId);
            if (tier != null && tier > maxTier) {
                maxTier = tier;
            }
        }
        return maxTier;
    }

    public EntityQuestComponent withActiveQuest(UUID playerId, ActiveQuestData questData) {
        Map<UUID, ActiveQuestData> newActiveQuests = new HashMap<>(activeQuests);
        newActiveQuests.put(playerId, questData);
        return new EntityQuestComponent(questPoolId, newActiveQuests, completedQuests, questCompletionTimes, chosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public EntityQuestComponent withoutActiveQuest(UUID playerId) {
        Map<UUID, ActiveQuestData> newActiveQuests = new HashMap<>(activeQuests);
        newActiveQuests.remove(playerId);
        return new EntityQuestComponent(questPoolId, newActiveQuests, completedQuests, questCompletionTimes, chosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    // completionDayTime is recorded regardless of whether the quest is repeatable - only consulted for one that is
    public EntityQuestComponent withCompletedQuest(UUID playerId, ResourceLocation questId, long completionDayTime) {
        Map<UUID, Set<ResourceLocation>> newCompletedQuests = new HashMap<>();
        completedQuests.forEach((k, v) -> newCompletedQuests.put(k, new HashSet<>(v)));

        newCompletedQuests.computeIfAbsent(playerId, k -> new HashSet<>()).add(questId);

        Map<UUID, Map<ResourceLocation, Long>> newCompletionTimes = new HashMap<>();
        questCompletionTimes.forEach((k, v) -> newCompletionTimes.put(k, new HashMap<>(v)));
        newCompletionTimes.computeIfAbsent(playerId, k -> new HashMap<>()).put(questId, completionDayTime);

        // completing a quest also clears its active-quest entry
        Map<UUID, ActiveQuestData> newActiveQuests = new HashMap<>(activeQuests);
        newActiveQuests.remove(playerId);

        return new EntityQuestComponent(questPoolId, newActiveQuests, newCompletedQuests, newCompletionTimes, chosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    // lets every completed quest be re-offered and re-completed, e.g. for testing; doesn't touch activeQuests or questCooldowns
    public EntityQuestComponent withoutCompletedQuests(UUID playerId) {
        Map<UUID, Set<ResourceLocation>> newCompletedQuests = new HashMap<>();
        completedQuests.forEach((k, v) -> newCompletedQuests.put(k, new HashSet<>(v)));
        newCompletedQuests.remove(playerId);

        Map<UUID, Map<ResourceLocation, Long>> newCompletionTimes = new HashMap<>();
        questCompletionTimes.forEach((k, v) -> newCompletionTimes.put(k, new HashMap<>(v)));
        newCompletionTimes.remove(playerId);

        return new EntityQuestComponent(questPoolId, activeQuests, newCompletedQuests, newCompletionTimes, chosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public EntityQuestComponent withUpdatedProgress(UUID playerId, QuestProgress progress) {
        ActiveQuestData currentData = activeQuests.get(playerId);
        if (currentData == null) {
            return this;
        }

        Map<UUID, ActiveQuestData> newActiveQuests = new HashMap<>(activeQuests);
        newActiveQuests.put(playerId, new ActiveQuestData(currentData.questId(), progress));
        return new EntityQuestComponent(questPoolId, newActiveQuests, completedQuests, questCompletionTimes, chosenQuestGroups, questCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public boolean isOnCooldown(UUID playerId) {
        Long cooldownEnd = questCooldowns.get(playerId);
        if (cooldownEnd == null) {
            return false;
        }
        return System.currentTimeMillis() < cooldownEnd;
    }

    public long getRemainingCooldownMs(UUID playerId) {
        Long cooldownEnd = questCooldowns.get(playerId);
        if (cooldownEnd == null) {
            return 0;
        }
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public EntityQuestComponent withCooldown(UUID playerId, long durationMs) {
        Map<UUID, Long> newCooldowns = new HashMap<>(questCooldowns);
        newCooldowns.put(playerId, System.currentTimeMillis() + durationMs);

        // cooldown replaces active quest
        Map<UUID, ActiveQuestData> newActiveQuests = new HashMap<>(activeQuests);
        newActiveQuests.remove(playerId);

        return new EntityQuestComponent(questPoolId, newActiveQuests, completedQuests, questCompletionTimes, chosenQuestGroups, newCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public EntityQuestComponent withoutCooldown(UUID playerId) {
        Map<UUID, Long> newCooldowns = new HashMap<>(questCooldowns);
        newCooldowns.remove(playerId);
        return new EntityQuestComponent(questPoolId, activeQuests, completedQuests, questCompletionTimes, chosenQuestGroups, newCooldowns, acceptQuestSoundOverride, finishQuestSoundOverride);
    }

    public record ActiveQuestData(
            ResourceLocation questId,
            QuestProgress progress
    ) {
        public static final Codec<ActiveQuestData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("quest_id").forGetter(ActiveQuestData::questId),
                        QuestProgress.CODEC.fieldOf("progress").forGetter(ActiveQuestData::progress)
                ).apply(instance, ActiveQuestData::new)
        );

        public static ActiveQuestData create(ResourceLocation questId) {
            return new ActiveQuestData(questId, new QuestProgress(questId));
        }
    }
}

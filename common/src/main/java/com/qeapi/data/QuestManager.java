package com.qeapi.data;

import com.qeapi.QuestAPI;
import com.qeapi.quest.Quest;
import com.qeapi.quest.QuestPool;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

// Central manager for all loaded quest data; quests are loaded from datapacks via QuestLoader.
public final class QuestManager {

    private static final Map<ResourceLocation, Quest> QUESTS = new HashMap<>();
    private static final Map<ResourceLocation, EntityQuestTag> ENTITY_QUEST_TAGS = new HashMap<>();

    private QuestManager() {}

    public static void clear() {
        QUESTS.clear();
        // tags are cleared separately via clearTags()
        QuestAPI.LOGGER.info("Cleared quest data");
    }

    public static void clearTags() {
        ENTITY_QUEST_TAGS.clear();
        QuestAPI.LOGGER.debug("Cleared entity quest tags");
    }

    public static void registerQuest(Quest quest) {
        QUESTS.put(quest.id(), quest);
        QuestAPI.LOGGER.debug("Registered quest: {}", quest.id());
    }

    public static Optional<Quest> getQuest(ResourceLocation id) {
        return Optional.ofNullable(QUESTS.get(id));
    }

    public static Collection<Quest> getAllQuests() {
        return Collections.unmodifiableCollection(QUESTS.values());
    }

    public static int getQuestCount() {
        return QUESTS.size();
    }

    public static boolean hasQuest(ResourceLocation id) {
        return QUESTS.containsKey(id);
    }

    public static void logSummary() {
        QuestAPI.LOGGER.info("Loaded {} quests, {} entity quest tags",
                QUESTS.size(), ENTITY_QUEST_TAGS.size());
    }

    // ==================== Tag Management ====================

    public static void registerEntityQuestTag(EntityQuestTag tag) {
        ENTITY_QUEST_TAGS.put(tag.getId(), tag);
        QuestAPI.LOGGER.debug("Registered entity quest tag: {}", tag.getId());
    }

    public static Optional<EntityQuestTag> getEntityQuestTag(ResourceLocation id) {
        return Optional.ofNullable(ENTITY_QUEST_TAGS.get(id));
    }

    public static boolean hasEntityQuestTag(ResourceLocation id) {
        return ENTITY_QUEST_TAGS.containsKey(id);
    }

    public static Collection<EntityQuestTag> getAllEntityQuestTags() {
        return Collections.unmodifiableCollection(ENTITY_QUEST_TAGS.values());
    }

    public static int getEntityQuestTagCount() {
        return ENTITY_QUEST_TAGS.size();
    }

    // builds a synthetic QuestPool from every quest the tag references, directly or through nested tags
    public static Optional<QuestPool> getQuestPoolFromTag(ResourceLocation tagId) {
        List<Quest> quests = resolveTagToQuests(tagId, new HashSet<>());
        if (quests.isEmpty()) {
            QuestAPI.LOGGER.warn("[QuestManager] Tag {} resolved to no quests!", tagId);
            return Optional.empty();
        }
        return Optional.of(QuestPool.fromQuests(tagId, quests));
    }

    private static List<Quest> resolveTagToQuests(ResourceLocation tagId, Set<ResourceLocation> visited) {
        if (!visited.add(tagId)) {
            QuestAPI.LOGGER.warn("[QuestManager] Circular tag reference detected involving {}", tagId);
            return List.of();
        }

        Optional<EntityQuestTag> tagOpt = getEntityQuestTag(tagId);
        if (tagOpt.isEmpty()) {
            QuestAPI.LOGGER.warn("[QuestManager] Tag {} not found! Available tags: {}",
                    tagId, ENTITY_QUEST_TAGS.keySet());
            return List.of();
        }

        EntityQuestTag tag = tagOpt.get();
        List<Quest> result = new ArrayList<>();

        for (ResourceLocation questId : tag.getQuestIds()) {
            getQuest(questId).ifPresentOrElse(result::add,
                    () -> QuestAPI.LOGGER.warn("[QuestManager] Quest {} referenced in tag {} does not exist!",
                            questId, tagId));
        }

        for (ResourceLocation nestedTagId : tag.getReferencedTags()) {
            result.addAll(resolveTagToQuests(nestedTagId, visited));
        }

        return result;
    }

    public static void debugPrintAll() {
        QuestAPI.LOGGER.info("=== QuestManager Debug Info ===");
        QuestAPI.LOGGER.info("Registered quests ({}):", QUESTS.size());
        for (ResourceLocation questId : QUESTS.keySet()) {
            QuestAPI.LOGGER.info("  - {} (tier {})", questId, QUESTS.get(questId).tier());
        }
        QuestAPI.LOGGER.info("Registered entity quest tags ({}):", ENTITY_QUEST_TAGS.size());
        for (ResourceLocation tagId : ENTITY_QUEST_TAGS.keySet()) {
            EntityQuestTag tag = ENTITY_QUEST_TAGS.get(tagId);
            QuestAPI.LOGGER.info("  - {} -> quests: {}", tagId, tag.getQuestIds());
        }
        QuestAPI.LOGGER.info("=== End Debug Info ===");
    }
}

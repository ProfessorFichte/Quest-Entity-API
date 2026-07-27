package com.qeapi.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.*;

// A pool of quests for an entity type, grouped by tier with random selection within each tier.
// Built at runtime from the quests a tag resolves to (see QuestManager.getQuestPoolFromTag) -
// not loaded directly from JSON.
public class QuestPool {
    private final ResourceLocation id;
    private final boolean followQuestOrder;
    private final Map<Integer, List<Quest>> questsByTier;

    public QuestPool(ResourceLocation id, boolean followQuestOrder, Map<Integer, List<Quest>> questsByTier) {
        this.id = id;
        this.followQuestOrder = followQuestOrder;
        this.questsByTier = new HashMap<>(questsByTier);
    }

    public static QuestPool fromQuests(ResourceLocation id, List<Quest> quests) {
        Map<Integer, List<Quest>> questsByTier = new HashMap<>();
        for (Quest quest : quests) {
            questsByTier.computeIfAbsent(quest.tier(), k -> new ArrayList<>()).add(quest);
        }
        return new QuestPool(id, true, questsByTier);
    }

    public ResourceLocation getId() {
        return id;
    }

    public boolean isFollowQuestOrder() {
        return followQuestOrder;
    }

    public Map<Integer, List<Quest>> getQuestsByTier() {
        return Collections.unmodifiableMap(questsByTier);
    }

    public Set<Integer> getAvailableTiers() {
        return questsByTier.keySet();
    }

    // Filtered by mod availability.
    public List<Quest> getQuestsForTier(int tier) {
        return questsByTier.getOrDefault(tier, List.of()).stream()
                .filter(Quest::isModLoaded)
                .toList();
    }

    // Unfiltered, for serialization.
    public List<Quest> getAllQuestsForTier(int tier) {
        return questsByTier.getOrDefault(tier, List.of());
    }

    // Unfiltered by mod-loaded status, for admin/debug tooling.
    public List<Quest> getAllQuests() {
        List<Quest> all = new ArrayList<>();
        for (List<Quest> tierQuests : questsByTier.values()) {
            all.addAll(tierQuests);
        }
        return all;
    }

    // Weighted random selection; only considers quests whose required mods are loaded.
    public Optional<Quest> selectQuestForTier(int tier, RandomSource random) {
        List<Quest> quests = getQuestsForTier(tier);
        if (quests.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = quests.stream().mapToInt(Quest::weight).sum();
        if (totalWeight <= 0) {
            return Optional.of(quests.get(random.nextInt(quests.size())));
        }

        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (Quest quest : quests) {
            currentWeight += quest.weight();
            if (randomValue < currentWeight) {
                return Optional.of(quest);
            }
        }

        // shouldn't happen
        return Optional.of(quests.get(quests.size() - 1));
    }

    public int getMaxTier() {
        return questsByTier.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public int getMinTier() {
        return questsByTier.keySet().stream().mapToInt(Integer::intValue).min().orElse(1);
    }

    public boolean requiresPreviousTiers(int tier) {
        if (!followQuestOrder) return false;
        return tier > getMinTier();
    }

}

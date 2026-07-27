package com.qeapi.data;

import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.*;

// Manages entity quest assignments loaded from data files, indexed by entity type.
public final class EntityQuestAssignmentManager {

    private static final List<EntityQuestAssignment> ASSIGNMENTS = new ArrayList<>();
    private static final Map<ResourceLocation, List<EntityQuestAssignment>> BY_ENTITY_TYPE = new HashMap<>();

    private EntityQuestAssignmentManager() {}

    public static void clear() {
        ASSIGNMENTS.clear();
        BY_ENTITY_TYPE.clear();
        QuestEntityAPI.LOGGER.info("Cleared entity quest assignments");
    }

    public static void register(EntityQuestAssignment assignment) {
        ASSIGNMENTS.add(assignment);
        BY_ENTITY_TYPE.computeIfAbsent(assignment.entityId(), k -> new ArrayList<>()).add(assignment);
        QuestEntityAPI.LOGGER.debug("Registered entity quest assignment for {} -> {}",
                assignment.entityId(), assignment.questPools());
    }

    public static List<EntityQuestAssignment> getAssignmentsForEntity(ResourceLocation entityTypeId) {
        return BY_ENTITY_TYPE.getOrDefault(entityTypeId, List.of());
    }

    // If multiple assignments match, randomly selects one of the most specific (after applying
    // that assignment's own quest chance).
    public static Optional<EntityQuestAssignment> findVillagerAssignment(
            String biomeType, String profession, RandomSource random) {

        QuestEntityAPI.LOGGER.debug("[AssignmentManager] findVillagerAssignment called: biome={}, profession={}", biomeType, profession);

        ResourceLocation villagerId = ResourceLocation.parse("minecraft:villager");
        List<EntityQuestAssignment> assignments = BY_ENTITY_TYPE.get(villagerId);

        if (assignments == null || assignments.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("[AssignmentManager] No villager assignments registered!");
            return Optional.empty();
        }

        QuestEntityAPI.LOGGER.debug("[AssignmentManager] Total villager assignments: {}", assignments.size());
        for (int i = 0; i < assignments.size(); i++) {
            var a = assignments.get(i);
            QuestEntityAPI.LOGGER.debug("[AssignmentManager]   [{}] pool={}, chance={}, villagerData={}",
                    i, a.questPools(), a.questChance(), a.villagerData());
        }

        List<EntityQuestAssignment> matching = new ArrayList<>();
        for (EntityQuestAssignment a : assignments) {
            boolean matches;
            if (a.villagerData().isPresent()) {
                var matcher = a.villagerData().get();
                matches = matcher.matches(biomeType, profession);
                QuestEntityAPI.LOGGER.debug("[AssignmentManager] Checking assignment {} against biome={}, profession={}: matches={}",
                        a.questPools(), biomeType, profession, matches);
                QuestEntityAPI.LOGGER.debug("[AssignmentManager]   Matcher: biomeType={}, profession={}",
                        matcher.biomeType(), matcher.profession());
            } else {
                matches = true; // no villager_data specified, matches all villagers
                QuestEntityAPI.LOGGER.debug("[AssignmentManager] Assignment {} has no villager_data, matches all", a.questPools());
            }
            if (matches) {
                matching.add(a);
            }
        }

        QuestEntityAPI.LOGGER.debug("[AssignmentManager] Found {} matching assignments", matching.size());

        if (matching.isEmpty()) {
            QuestEntityAPI.LOGGER.debug("[AssignmentManager] No matching assignments found!");
            return Optional.empty();
        }

        // sort by specificity, most specific first
        List<EntityQuestAssignment> sorted = new ArrayList<>(matching);
        sorted.sort((a, b) -> {
            int aSpecificity = getSpecificity(a);
            int bSpecificity = getSpecificity(b);
            return Integer.compare(bSpecificity, aSpecificity);
        });

        int highestSpecificity = getSpecificity(sorted.get(0));

        List<EntityQuestAssignment> mostSpecific = sorted.stream()
                .filter(a -> getSpecificity(a) == highestSpecificity)
                .toList();

        QuestEntityAPI.LOGGER.debug("[AssignmentManager] Found {} most specific assignments (specificity={})",
                mostSpecific.size(), highestSpecificity);
        for (var a : mostSpecific) {
            QuestEntityAPI.LOGGER.debug("[AssignmentManager]   Most specific: pool={}, chance={}", a.questPools(), a.questChance());
        }

        EntityQuestAssignment selected;
        if (mostSpecific.size() > 1) {
            int index = random.nextInt(mostSpecific.size());
            selected = mostSpecific.get(index);
            QuestEntityAPI.LOGGER.debug("[AssignmentManager] Randomly selected assignment index {}: {} (from {} options)",
                    index, selected.questPools(), mostSpecific.size());
        } else {
            selected = mostSpecific.get(0);
            QuestEntityAPI.LOGGER.debug("[AssignmentManager] Only one most specific assignment: {}", selected.questPools());
        }

        double roll = random.nextDouble();
        QuestEntityAPI.LOGGER.debug("[AssignmentManager] Chance roll: {} vs threshold: {} ({}%)",
                roll, selected.questChance(), selected.questChance() * 100);

        if (roll < selected.questChance()) {
            QuestEntityAPI.LOGGER.info("[AssignmentManager] Assignment {} PASSED chance check (roll={} < {})",
                    selected.questPools(), roll, selected.questChance());
            return Optional.of(selected);
        }

        QuestEntityAPI.LOGGER.debug("[AssignmentManager] Assignment {} FAILED chance check (roll={} >= {})",
                selected.questPools(), roll, selected.questChance());
        return Optional.empty();
    }

    // If multiple assignments match, randomly selects one, then applies its quest chance.
    public static Optional<EntityQuestAssignment> findEntityAssignment(
            ResourceLocation entityTypeId, RandomSource random) {

        List<EntityQuestAssignment> assignments = BY_ENTITY_TYPE.get(entityTypeId);

        if (assignments == null || assignments.isEmpty()) {
            return Optional.empty();
        }

        List<EntityQuestAssignment> nonVillagerAssignments = assignments.stream()
                .filter(a -> a.villagerData().isEmpty())
                .toList();

        if (nonVillagerAssignments.isEmpty()) {
            return Optional.empty();
        }

        EntityQuestAssignment selected;
        if (nonVillagerAssignments.size() > 1) {
            selected = nonVillagerAssignments.get(random.nextInt(nonVillagerAssignments.size()));
        } else {
            selected = nonVillagerAssignments.get(0);
        }

        if (random.nextDouble() < selected.questChance()) {
            return Optional.of(selected);
        }

        return Optional.empty();
    }

    private static int getSpecificity(EntityQuestAssignment assignment) {
        if (assignment.villagerData().isEmpty()) {
            return 0;
        }

        EntityQuestAssignment.VillagerMatcher matcher = assignment.villagerData().get();
        int specificity = 0;
        if (matcher.biomeType().isPresent()) specificity++;
        if (matcher.profession().isPresent()) specificity++;
        return specificity;
    }

    public static int getAssignmentCount() {
        return ASSIGNMENTS.size();
    }

    public static void logSummary() {
        QuestEntityAPI.LOGGER.info("Loaded {} entity quest assignments for {} entity types",
                ASSIGNMENTS.size(), BY_ENTITY_TYPE.size());
    }
}

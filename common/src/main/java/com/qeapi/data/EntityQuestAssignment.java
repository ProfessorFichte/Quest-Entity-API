package com.qeapi.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

// Assigns a quest pool to an entity type (e.g. vanilla villagers) via data files.
//
// Example JSON:
// {
//   "entity_id": "minecraft:villager",
//   "quest_pool": "qe_api:test_quests",
//   "quest_chance": 0.25,
//   "chunk_restriction_radius": 0,
//   "villager_data": {
//     "biome_type": "plains",
//     "profession": "farmer"
//   }
// }
//
// "quest_pool" also accepts a list of tags, one of which is picked uniformly at random per
// assignment. "chunk_restriction_radius" is optional; when present, only one entity within
// that many chunks of another can receive this same assignment at a time (freed on death).
// Modded professions use a full resource location, e.g. "wizards:wizard_merchant".
public record EntityQuestAssignment(
        ResourceLocation entityId,
        List<String> questPools,
        double questChance,
        Optional<Integer> chunkRestrictionRadius,
        Optional<VillagerMatcher> villagerData
) {
    private static final Codec<List<String>> QUEST_POOLS_CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf())
            .xmap(
                    either -> either.map(List::of, list -> list),
                    list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list)
            );

    public static final Codec<EntityQuestAssignment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("entity_id").forGetter(EntityQuestAssignment::entityId),
                    QUEST_POOLS_CODEC.fieldOf("quest_pool").forGetter(EntityQuestAssignment::questPools),
                    Codec.DOUBLE.optionalFieldOf("quest_chance", 1.0).forGetter(EntityQuestAssignment::questChance),
                    Codec.INT.optionalFieldOf("chunk_restriction_radius").forGetter(EntityQuestAssignment::chunkRestrictionRadius),
                    VillagerMatcher.CODEC.optionalFieldOf("villager_data").forGetter(EntityQuestAssignment::villagerData)
            ).apply(instance, EntityQuestAssignment::new)
    );

    // Uniform random when there are several.
    public String pickQuestPool(RandomSource random) {
        if (questPools.size() == 1) {
            return questPools.get(0);
        }
        return questPools.get(random.nextInt(questPools.size()));
    }

    // Stable key for chunk-restriction tracking, derived from content rather than an authored id.
    public String restrictionKey() {
        return entityId + "|" + questPools + "|" + villagerData;
    }

    public boolean isVillagerAssignment() {
        return entityId.toString().equals("minecraft:villager") && villagerData.isPresent();
    }

    public record VillagerMatcher(
            Optional<String> biomeType,
            Optional<String> profession
    ) {
        public static final Codec<VillagerMatcher> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf("biome_type").forGetter(VillagerMatcher::biomeType),
                        Codec.STRING.optionalFieldOf("profession").forGetter(VillagerMatcher::profession)
                ).apply(instance, VillagerMatcher::new)
        );

        public boolean matches(String villagerBiomeType, String villagerProfession) {
            QuestEntityAPI.LOGGER.debug("[VillagerMatcher] Checking match: villagerBiome={}, villagerProfession={}",
                    villagerBiomeType, villagerProfession);
            QuestEntityAPI.LOGGER.debug("[VillagerMatcher]   Against: requiredBiome={}, requiredProfession={}",
                    biomeType, profession);

            if (biomeType.isPresent()) {
                boolean biomeMatches = biomeType.get().equalsIgnoreCase(villagerBiomeType);
                QuestEntityAPI.LOGGER.debug("[VillagerMatcher]   Biome check: '{}' vs '{}' = {}",
                        biomeType.get(), villagerBiomeType, biomeMatches);
                if (!biomeMatches) {
                    QuestEntityAPI.LOGGER.debug("[VillagerMatcher]   FAILED biome check");
                    return false;
                }
            }

            // support both short and full resource location format
            if (profession.isPresent()) {
                String requiredProfession = profession.get();
                String actualProfession = villagerProfession;

                if (!requiredProfession.contains(":")) {
                    requiredProfession = "minecraft:" + requiredProfession;
                }
                if (!actualProfession.contains(":")) {
                    actualProfession = "minecraft:" + actualProfession;
                }

                boolean professionMatches = requiredProfession.equalsIgnoreCase(actualProfession);
                QuestEntityAPI.LOGGER.debug("[VillagerMatcher]   Profession check: '{}' vs '{}' = {}",
                        requiredProfession, actualProfession, professionMatches);
                if (!professionMatches) {
                    QuestEntityAPI.LOGGER.debug("[VillagerMatcher]   FAILED profession check");
                    return false;
                }
            }

            QuestEntityAPI.LOGGER.debug("[VillagerMatcher]   MATCHED!");
            return true;
        }
    }
}

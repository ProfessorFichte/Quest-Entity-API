package com.qeapi.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.qeapi.QuestEntityAPI;
import com.qeapi.data.EntityQuestAssignment;
import com.qeapi.data.EntityQuestAssignment.VillagerMatcher;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class AssignmentProvider implements DataProvider {

    protected final PackOutput output;
    protected final String modId;
    private final Map<String, EntityQuestAssignment> assignments = new LinkedHashMap<>();

    protected AssignmentProvider(PackOutput output, String modId) {
        this.output = output;
        this.modId = modId;
    }

    protected abstract void addAssignments();

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        assignments.clear();
        addAssignments();

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Map.Entry<String, EntityQuestAssignment> entry : assignments.entrySet()) {
            Path path = output.getOutputFolder()
                    .resolve("data").resolve(modId).resolve("entity_quest_assignment")
                    .resolve(entry.getKey() + ".json");

            JsonElement json = EntityQuestAssignment.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error -> QuestEntityAPI.LOGGER.error("Failed to encode assignment {}: {}", entry.getKey(), error))
                    .orElse(null);

            if (json != null) {
                futures.add(DataProvider.saveStable(cache, json, path));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Quest Entity API Assignments: " + modId;
    }

    protected AssignmentBuilder assign(String fileName, String entityId) {
        return new AssignmentBuilder(fileName, ResourceLocation.parse(entityId));
    }

    public class AssignmentBuilder {
        private final String fileName;
        private final ResourceLocation entityId;
        private final List<String> questPools = new ArrayList<>();
        private double questChance = 1.0;
        private Optional<Integer> chunkRestrictionRadius = Optional.empty();
        private Optional<VillagerMatcher> villagerData = Optional.empty();

        AssignmentBuilder(String fileName, ResourceLocation entityId) {
            this.fileName = fileName;
            this.entityId = entityId;
        }

        public AssignmentBuilder questPool(String... pools) {
            questPools.addAll(Arrays.asList(pools));
            return this;
        }

        public AssignmentBuilder questChance(double chance) {
            this.questChance = chance;
            return this;
        }

        public AssignmentBuilder chunkRestrictionRadius(int radius) {
            this.chunkRestrictionRadius = Optional.of(radius);
            return this;
        }

        public AssignmentBuilder villager(String biomeType, String profession) {
            this.villagerData = Optional.of(new VillagerMatcher(
                    Optional.ofNullable(biomeType), Optional.ofNullable(profession)));
            return this;
        }

        public AssignmentBuilder villagerProfession(String profession) {
            return villager(null, profession);
        }

        public AssignmentBuilder villagerBiome(String biomeType) {
            return villager(biomeType, null);
        }

        public void add() {
            assignments.put(fileName, new EntityQuestAssignment(
                    entityId, List.copyOf(questPools), questChance, chunkRestrictionRadius, villagerData));
        }
    }
}

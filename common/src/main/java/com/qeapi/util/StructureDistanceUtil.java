package com.qeapi.util;

import com.mojang.datafixers.util.Pair;
import com.qeapi.QuestEntityAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Wraps ChunkGenerator.findNearestMapStructure with a blocks-based max-distance cutoff, shared by
// FindStructureTask's distance gating and the teleport/map structure rewards. Structure instances
// never move once world-gen places them, so a result is cached forever per (dimension, structure,
// origin chunk, radius) - repeat lookups for the same stationary quest-giver (offering the quest
// menu again, a repeat map roll, etc.) then cost a single map read instead of re-running placement
// math, which matters because that math is invoked on every quest-menu open/refresh.
public final class StructureDistanceUtil {

    public static final int DEFAULT_MAX_DISTANCE = 10000;

    private static final Map<String, Optional<BlockPos>> CACHE = new ConcurrentHashMap<>();

    private StructureDistanceUtil() {}

    @SuppressWarnings("unchecked")
    public static Optional<BlockPos> findNearestStructure(ServerLevel level, BlockPos origin,
                                                            ResourceLocation structureId, int maxDistanceBlocks) {
        int radiusChunks = Math.max(1, maxDistanceBlocks / 16 + 1);
        String cacheKey = level.dimension().location() + "|" + structureId + "|"
                + (origin.getX() >> 4) + "," + (origin.getZ() >> 4) + "|" + radiusChunks;

        Optional<BlockPos> cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Holder<Structure> structureHolder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(structureId)
                .map(h -> (Holder<Structure>) h)
                .orElse(null);

        if (structureHolder == null) {
            QuestEntityAPI.LOGGER.warn("[StructureDistance] Structure {} not found in registry", structureId);
            CACHE.put(cacheKey, Optional.empty());
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(structureHolder), origin, radiusChunks, false);

        Optional<BlockPos> result;
        if (found == null) {
            result = Optional.empty();
        } else {
            double distance = Math.sqrt(origin.distSqr(found.getFirst()));
            result = distance <= maxDistanceBlocks ? Optional.of(found.getFirst()) : Optional.empty();
        }

        CACHE.put(cacheKey, result);
        return result;
    }

    // Clear on server stop / world change - the cache is keyed by chunk coordinates and structure
    // ID only, not by world seed, so it would otherwise leak stale results into a newly loaded world.
    public static void clearCache() {
        CACHE.clear();
    }
}

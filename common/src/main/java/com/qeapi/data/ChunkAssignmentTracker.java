package com.qeapi.data;

import com.mojang.serialization.Codec;
import com.qeapi.QuestAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// tracks which entity "occupies" a chunk-restricted assignment within a radius, so only one entity in range can hold it at a time
public class ChunkAssignmentTracker extends SavedData {

    private static final String DATA_NAME = QuestAPI.MOD_ID + "_chunk_assignments";
    private static final Codec<Map<String, String>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    // "restrictionKey|chunkX,chunkZ" -> entity UUID
    private final Map<String, UUID> assignments = new HashMap<>();
    // entity UUID -> the composite key above, for O(1) release lookups
    private final Map<UUID, String> byEntity = new HashMap<>();

    public static ChunkAssignmentTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ChunkAssignmentTracker::new, ChunkAssignmentTracker::load, null),
                DATA_NAME
        );
    }

    private static String compositeKey(String restrictionKey, ChunkPos pos) {
        return restrictionKey + "|" + pos.x + "," + pos.z;
    }

    // scans from the candidate's own position so the radius stays symmetric regardless of which chunk got marked first
    public boolean isRestricted(String restrictionKey, ChunkPos candidate, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                String key = compositeKey(restrictionKey, new ChunkPos(candidate.x + dx, candidate.z + dz));
                if (assignments.containsKey(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void markAssigned(String restrictionKey, ChunkPos pos, UUID entityUuid) {
        String key = compositeKey(restrictionKey, pos);
        assignments.put(key, entityUuid);
        byEntity.put(entityUuid, key);
        setDirty();
        QuestAPI.LOGGER.debug("[ChunkAssignmentTracker] Marked {} at {} for entity {}", restrictionKey, pos, entityUuid);
    }

    public void release(UUID entityUuid) {
        String key = byEntity.remove(entityUuid);
        if (key != null) {
            assignments.remove(key);
            setDirty();
            QuestAPI.LOGGER.debug("[ChunkAssignmentTracker] Released assignment for entity {}", entityUuid);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Map<String, String> serializable = new HashMap<>();
        assignments.forEach((key, uuid) -> serializable.put(key, uuid.toString()));
        Tag encoded = MAP_CODEC.encodeStart(NbtOps.INSTANCE, serializable)
                .resultOrPartial(error -> QuestAPI.LOGGER.error("[ChunkAssignmentTracker] Failed to encode: {}", error))
                .orElse(null);
        if (encoded != null) {
            tag.put("assignments", encoded);
        }
        return tag;
    }

    private static ChunkAssignmentTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        ChunkAssignmentTracker tracker = new ChunkAssignmentTracker();
        if (tag.contains("assignments")) {
            MAP_CODEC.parse(NbtOps.INSTANCE, tag.get("assignments"))
                    .resultOrPartial(error -> QuestAPI.LOGGER.error("[ChunkAssignmentTracker] Failed to load: {}", error))
                    .ifPresent(map -> map.forEach((key, uuidStr) -> {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            tracker.assignments.put(key, uuid);
                            tracker.byEntity.put(uuid, key);
                        } catch (IllegalArgumentException e) {
                            QuestAPI.LOGGER.warn("[ChunkAssignmentTracker] Invalid UUID in saved data: {}", uuidStr);
                        }
                    }));
        }
        return tracker;
    }
}

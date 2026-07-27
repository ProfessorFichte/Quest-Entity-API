package com.qeapi.util;

import com.mojang.datafixers.util.Pair;
import com.qeapi.QuestEntityAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

// Creates vanilla treasure-map-style ItemStacks pointing at the nearest instance of one specific
// structure - mirrors vanilla's own ExplorationMapFunction (used for buried treasure, ocean ruin
// loot, etc.), but targets a single structure by ID rather than a whole structure tag, since quest
// tasks reference structures individually.
public final class StructureMapUtil {

    // Same zoom level vanilla treasure maps use
    private static final byte MAP_ZOOM = 2;
    // Same default search radius (in chunks) as vanilla's ExplorationMapFunction
    private static final int SEARCH_RADIUS = 100;

    private StructureMapUtil() {}

    // Finds the nearest instance of structureId and builds a filled map pointing at it, with a
    // red-X target decoration (the same generic marker vanilla uses for buried treasure - distinct
    // from structure-specific icons like the woodland mansion one, since this needs to represent
    // an arbitrary structure). Returns empty if the structure isn't registered or no instance was
    // found within SEARCH_RADIUS chunks.
    @SuppressWarnings("unchecked")
    public static Optional<ItemStack> createMapToStructure(ServerLevel level, BlockPos origin, ResourceLocation structureId) {
        Holder<Structure> structureHolder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(structureId)
                .map(h -> (Holder<Structure>) h)
                .orElse(null);

        if (structureHolder == null) {
            QuestEntityAPI.LOGGER.warn("[StructureMap] Structure {} not found in registry", structureId);
            return Optional.empty();
        }

        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(structureHolder), origin, SEARCH_RADIUS, false);

        if (found == null) {
            QuestEntityAPI.LOGGER.info("[StructureMap] No instance of structure {} found within {} chunks of {}",
                    structureId, SEARCH_RADIUS, origin);
            return Optional.empty();
        }

        BlockPos targetPos = found.getFirst();
        ItemStack mapStack = MapItem.create(level, targetPos.getX(), targetPos.getZ(), MAP_ZOOM, true, true);
        MapItem.renderBiomePreviewMap(level, mapStack);
        MapItemSavedData.addTargetDecoration(mapStack, targetPos, "+", MapDecorationTypes.RED_X);
        return Optional.of(mapStack);
    }
}

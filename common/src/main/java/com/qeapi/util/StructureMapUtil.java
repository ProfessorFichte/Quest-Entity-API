package com.qeapi.util;

import com.qeapi.QuestEntityAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
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

    private StructureMapUtil() {}

    // Finds the nearest instance of structureId within maxDistanceBlocks and builds a filled map
    // pointing at it, with a red-X target decoration (the same generic marker vanilla uses for
    // buried treasure - distinct from structure-specific icons like the woodland mansion one, since
    // this needs to represent an arbitrary structure). Returns empty if the structure isn't
    // registered or no instance was found within range - the same range a FindStructureTask's own
    // distance gate uses, so a quest never hands out a map to a structure it wouldn't have offered.
    public static Optional<ItemStack> createMapToStructure(ServerLevel level, BlockPos origin,
                                                            ResourceLocation structureId, int maxDistanceBlocks) {
        Optional<BlockPos> targetPos = StructureDistanceUtil.findNearestStructure(level, origin, structureId, maxDistanceBlocks);
        if (targetPos.isEmpty()) {
            QuestEntityAPI.LOGGER.info("[StructureMap] No instance of structure {} found within {} blocks of {}",
                    structureId, maxDistanceBlocks, origin);
            return Optional.empty();
        }

        return Optional.of(buildMapStack(level, targetPos.get()));
    }

    public static ItemStack buildMapStack(ServerLevel level, BlockPos targetPos) {
        ItemStack mapStack = MapItem.create(level, targetPos.getX(), targetPos.getZ(), MAP_ZOOM, true, true);
        MapItem.renderBiomePreviewMap(level, mapStack);
        MapItemSavedData.addTargetDecoration(mapStack, targetPos, "+", MapDecorationTypes.RED_X);
        return mapStack;
    }
}

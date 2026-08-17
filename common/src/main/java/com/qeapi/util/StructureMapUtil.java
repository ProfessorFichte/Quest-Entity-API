package com.qeapi.util;

import com.qeapi.QuestAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

// mirrors vanilla's ExplorationMapFunction, but targets a single structure by ID rather than a whole tag, since quest tasks reference structures individually
public final class StructureMapUtil {

    // Same zoom level vanilla treasure maps use
    private static final byte MAP_ZOOM = 2;

    private StructureMapUtil() {}

    // uses the generic red-X marker since this can represent an arbitrary structure, not a specific icon like the woodland mansion one
    public static Optional<ItemStack> createMapToStructure(ServerLevel level, BlockPos origin,
                                                            ResourceLocation structureId, int maxDistanceBlocks) {
        Optional<BlockPos> targetPos = StructureDistanceUtil.findNearestStructure(level, origin, structureId, maxDistanceBlocks);
        if (targetPos.isEmpty()) {
            QuestAPI.LOGGER.info("[StructureMap] No instance of structure {} found within {} blocks of {}",
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

package com.qeapi.util;

import com.qeapi.QuestEntityAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

// Finds a surface spot near a located structure that's safe to teleport a player to: outside the
// structure's own footprint (so not inside a sealed treasure room or "main goal" chamber) and not
// inside a solid block or standing in lava. There's no vanilla concept of "which room is the
// treasure room" to query directly - the closest real signal is the structure's overall bounding
// box (StructureStart.getBoundingBox()), which every one of its pieces/rooms is generated within.
// Teleporting just outside that box and then snapping to the surface heightmap keeps the player
// near the structure without ever placing them inside one of its interior spaces.
public final class StructureTeleportUtil {

    private static final int[] MARGINS = {8, 16, 32, 64};

    private StructureTeleportUtil() {}

    public static Optional<BlockPos> findSafeSpotNearStructure(ServerLevel level, BlockPos structurePos, Holder<Structure> structureHolder) {
        ChunkPos chunkPos = new ChunkPos(structurePos);
        // Forces only enough generation for structure start/piece data to exist (cheap relative to
        // full terrain/feature/mob generation) - the reward is usually granted for a structure well
        // outside anyone's loaded view distance, so without this the bounding box below is unavailable.
        level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS, true);

        StructureStart start = level.structureManager().getStructureAt(structurePos, structureHolder.value());
        BoundingBox box = (start != null && start.isValid()) ? start.getBoundingBox() : null;

        if (box != null) {
            for (int margin : MARGINS) {
                Optional<BlockPos> spot = trySpotOutsideBox(level, box, margin);
                if (spot.isPresent()) return spot;
            }
        }

        QuestEntityAPI.LOGGER.debug("[StructureTeleport] No usable bounding box for structure at {} - falling back to an offset surface spot", structurePos);
        for (int margin : MARGINS) {
            Optional<BlockPos> spot = trySurfaceSpot(level, structurePos.getX() + margin, structurePos.getZ());
            if (spot.isPresent()) return spot;
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> trySpotOutsideBox(ServerLevel level, BoundingBox box, int margin) {
        int centerZ = (box.minZ() + box.maxZ()) / 2;
        int centerX = (box.minX() + box.maxX()) / 2;

        int[][] candidates = {
                {box.maxX() + margin, centerZ},
                {box.minX() - margin, centerZ},
                {centerX, box.maxZ() + margin},
                {centerX, box.minZ() - margin}
        };

        for (int[] candidate : candidates) {
            Optional<BlockPos> spot = trySurfaceSpot(level, candidate[0], candidate[1]);
            if (spot.isPresent()) return spot;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> trySurfaceSpot(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 1) {
            return Optional.empty();
        }

        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.above();
        BlockPos ground = feet.below();

        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return Optional.empty();
        if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) return Optional.empty();

        FluidState groundFluid = level.getFluidState(ground);
        if (!groundFluid.isEmpty() && groundFluid.is(FluidTags.LAVA)) return Optional.empty();

        return Optional.of(feet);
    }
}

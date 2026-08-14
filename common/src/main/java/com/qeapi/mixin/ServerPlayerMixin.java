package com.qeapi.mixin;

import com.qeapi.QuestEntityAPI;
import com.qeapi.event.QuestEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Tracks player movement and structure entry for quest tasks.
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Unique
    private Vec3 qe_api$lastPosition = null;

    @Unique
    private double qe_api$accumulatedDistance = 0.0;

    @Unique
    private int qe_api$structureCheckCooldown = 0;

    @Unique
    private Set<ResourceLocation> qe_api$currentStructures = new HashSet<>();

    @Unique
    private ResourceLocation qe_api$currentBiome = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void qe_api$onTick(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Vec3 currentPos = self.position();

        if (qe_api$lastPosition == null) {
            qe_api$lastPosition = currentPos;
            return;
        }

        double distance = qe_api$lastPosition.distanceTo(currentPos);
        if (distance > 0.01 && distance < 100.0) { // ignore teleports (>100 blocks)
            qe_api$accumulatedDistance += distance;

            // batch into >=1 block increments to reduce event spam
            if (qe_api$accumulatedDistance >= 1.0) {
                QuestEntityAPI.LOGGER.debug("Player {} moved {} blocks (accumulated), firing event",
                        self.getName().getString(), qe_api$accumulatedDistance);
                QuestEventHandler.onPlayerMove(self, qe_api$lastPosition, currentPos, qe_api$accumulatedDistance);
                qe_api$accumulatedDistance = 0.0;
            }
        }

        qe_api$lastPosition = currentPos;

        // cooldown: every 20 ticks = 1 second
        qe_api$structureCheckCooldown--;
        if (qe_api$structureCheckCooldown <= 0) {
            qe_api$structureCheckCooldown = 20;
            qe_api$checkStructures(self);
            qe_api$checkBiome(self);
        }
    }

    @Unique
    private void qe_api$checkBiome(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        level.getBiome(playerPos).unwrapKey().ifPresent(key -> {
            ResourceLocation biomeId = key.location();
            if (!biomeId.equals(qe_api$currentBiome)) {
                qe_api$currentBiome = biomeId;
                QuestEventHandler.onBiomeEntered(player, biomeId);
            }
        });
    }

    @Unique
    private void qe_api$checkStructures(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos);

        Set<ResourceLocation> newStructures = new HashSet<>();

        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_REFERENCES, false);
        if (chunk == null) {
            return;
        }

        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();

            if (start.isValid() && start.getBoundingBox().isInside(playerPos)) {
                structureRegistry.getResourceKey(structure)
                        .ifPresent(key -> {
                            ResourceLocation structureId = key.location();
                            newStructures.add(structureId);

                            if (!qe_api$currentStructures.contains(structureId)) {
                                QuestEventHandler.onStructureEntered(player, structureId);
                            }
                        });
            }
        }

        qe_api$currentStructures = newStructures;
    }
}

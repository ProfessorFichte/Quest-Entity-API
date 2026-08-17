package com.qeapi.mixin;

import com.qeapi.QuestAPI;
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

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Unique
    private Vec3 quest_api$lastPosition = null;

    @Unique
    private double quest_api$accumulatedDistance = 0.0;

    @Unique
    private int quest_api$structureCheckCooldown = 0;

    @Unique
    private Set<ResourceLocation> quest_api$currentStructures = new HashSet<>();

    @Unique
    private ResourceLocation quest_api$currentBiome = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void quest_api$onTick(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Vec3 currentPos = self.position();

        if (quest_api$lastPosition == null) {
            quest_api$lastPosition = currentPos;
            return;
        }

        double distance = quest_api$lastPosition.distanceTo(currentPos);
        if (distance > 0.01 && distance < 100.0) { // ignore teleports (>100 blocks)
            quest_api$accumulatedDistance += distance;

            // batch into >=1 block increments to reduce event spam
            if (quest_api$accumulatedDistance >= 1.0) {
                QuestAPI.LOGGER.debug("Player {} moved {} blocks (accumulated), firing event",
                        self.getName().getString(), quest_api$accumulatedDistance);
                QuestEventHandler.onPlayerMove(self, quest_api$lastPosition, currentPos, quest_api$accumulatedDistance);
                quest_api$accumulatedDistance = 0.0;
            }
        }

        quest_api$lastPosition = currentPos;

        // cooldown: every 20 ticks = 1 second
        quest_api$structureCheckCooldown--;
        if (quest_api$structureCheckCooldown <= 0) {
            quest_api$structureCheckCooldown = 20;
            quest_api$checkStructures(self);
            quest_api$checkBiome(self);
        }
    }

    @Unique
    private void quest_api$checkBiome(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        level.getBiome(playerPos).unwrapKey().ifPresent(key -> {
            ResourceLocation biomeId = key.location();
            if (!biomeId.equals(quest_api$currentBiome)) {
                quest_api$currentBiome = biomeId;
                QuestEventHandler.onBiomeEntered(player, biomeId);
            }
        });
    }

    @Unique
    private void quest_api$checkStructures(ServerPlayer player) {
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

                            if (!quest_api$currentStructures.contains(structureId)) {
                                QuestEventHandler.onStructureEntered(player, structureId);
                            }
                        });
            }
        }

        quest_api$currentStructures = newStructures;
    }
}

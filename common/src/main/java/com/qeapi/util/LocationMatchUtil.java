package com.qeapi.util;

import com.qeapi.QuestAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Optional;

// shared biome/structure location-matching helpers, used by both EntityKillTask and ConditionalDropTask's mob-drop targeting
public final class LocationMatchUtil {

    private LocationMatchUtil() {}

    public static boolean isInBiome(ServerLevel level, BlockPos pos, ResourceLocation biomeId) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();

        if (biomeKey.isEmpty()) {
            return false;
        }

        return biomeKey.get().location().equals(biomeId);
    }

    public static boolean isInBiomeTag(ServerLevel level, BlockPos pos, TagKey<Biome> biomeTag) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        return biomeHolder.is(biomeTag);
    }

    public static boolean isInStructure(ServerLevel level, BlockPos pos, ResourceLocation structureId) {
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        Structure targetStructure = structureRegistry.get(structureId);
        if (targetStructure == null) {
            QuestAPI.LOGGER.warn("Structure {} not found in registry", structureId);
            return false;
        }

        StructureStart structureStart = level.structureManager().getStructureWithPieceAt(pos, targetStructure);
        return structureStart.isValid();
    }
}

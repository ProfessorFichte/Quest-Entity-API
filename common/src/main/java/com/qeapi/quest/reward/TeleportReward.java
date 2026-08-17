package com.qeapi.quest.reward;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.util.StructureDistanceUtil;
import com.qeapi.util.StructureTeleportUtil;
import com.qeapi.util.TextFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;
import java.util.Set;

public record TeleportReward(
        TeleportTarget target,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward, EntityAwareReward {

    public static final MapCodec<TeleportReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    TeleportTarget.CODEC.fieldOf("target").forGetter(TeleportReward::target),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(TeleportReward::textureOverrideId)
            ).apply(instance, TeleportReward::new)
    );

    public static TeleportReward toStructure(ResourceLocation structureId) {
        return new TeleportReward(new ToStructure(structureId, StructureDistanceUtil.DEFAULT_MAX_DISTANCE), Optional.empty());
    }

    public static TeleportReward toStructure(ResourceLocation structureId, int maxDistanceRange) {
        return new TeleportReward(new ToStructure(structureId, maxDistanceRange), Optional.empty());
    }

    public static TeleportReward toCoordinates(double x, double y, double z) {
        return new TeleportReward(new ToCoordinates(x, y, z, Optional.empty()), Optional.empty());
    }

    public static TeleportReward toCoordinates(double x, double y, double z, ResourceLocation dimension) {
        return new TeleportReward(new ToCoordinates(x, y, z, Optional.of(dimension)), Optional.empty());
    }

    public static TeleportReward toBiome(ResourceLocation biomeId) {
        return new TeleportReward(new ToBiome(biomeId, StructureDistanceUtil.DEFAULT_MAX_DISTANCE), Optional.empty());
    }

    public static TeleportReward toBiome(ResourceLocation biomeId, int maxDistanceRange) {
        return new TeleportReward(new ToBiome(biomeId, maxDistanceRange), Optional.empty());
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("teleport");
    }

    @Override
    public void grant(ServerPlayer player) {
        target.grantFrom(player, player.serverLevel(), player.blockPosition());
    }

    @Override
    public void grantWithEntity(ServerPlayer player, Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            target.grantFrom(player, serverLevel, entity.blockPosition());
        } else {
            grant(player);
        }
    }

    @Override
    public Component getDisplayText() {
        return target.displayText();
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.ENDER_PEARL));
    }

    // teleport_type dispatch mirrors QuestReward.CODEC's own "reward" dispatch one level down,
    // scoped to this fixed, closed set of destination kinds instead of the open reward registry.
    public sealed interface TeleportTarget permits ToStructure, ToCoordinates, ToBiome {
        Codec<TeleportTarget> CODEC = Codec.lazyInitialized(() ->
                Codec.STRING.dispatch(
                        "teleport_type",
                        TeleportTarget::kind,
                        kind -> switch (kind) {
                            case "structure" -> ToStructure.CODEC;
                            case "coordinates" -> ToCoordinates.CODEC;
                            case "biome" -> ToBiome.CODEC;
                            default -> throw new IllegalArgumentException("Unknown teleport_type: " + kind);
                        }
                )
        );

        String kind();

        void grantFrom(ServerPlayer player, ServerLevel level, BlockPos origin);

        Component displayText();
    }

    // Falls back to the player's own position without entity context, same as MapToStructureReward.
    // "safe" means outside the structure's interior, not suffocating, not in lava - see StructureTeleportUtil.
    public record ToStructure(ResourceLocation structureId, int maxDistanceRange) implements TeleportTarget {
        public static final MapCodec<ToStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("structure_id").forGetter(ToStructure::structureId),
                        Codec.intRange(1, Integer.MAX_VALUE)
                                .optionalFieldOf("max_distance_range", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                                .forGetter(ToStructure::maxDistanceRange)
                ).apply(instance, ToStructure::new)
        );

        @Override
        public String kind() {
            return "structure";
        }

        @Override
        @SuppressWarnings("unchecked")
        public void grantFrom(ServerPlayer player, ServerLevel level, BlockPos origin) {
            Optional<Holder<Structure>> structureHolder = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getHolder(structureId)
                    .map(h -> (Holder<Structure>) h);

            if (structureHolder.isEmpty()) {
                QuestAPI.LOGGER.warn("[TeleportReward] Structure {} not found in registry", structureId);
                return;
            }

            Optional<BlockPos> structurePos = StructureDistanceUtil.findNearestStructure(level, origin, structureId, maxDistanceRange);
            if (structurePos.isEmpty()) {
                QuestAPI.LOGGER.warn("[TeleportReward] No instance of {} found within {} blocks of {} - reward not granted",
                        structureId, maxDistanceRange, origin);
                return;
            }

            Optional<BlockPos> safeSpot = StructureTeleportUtil.findSafeSpotNearStructure(level, structurePos.get(), structureHolder.get());
            if (safeSpot.isEmpty()) {
                QuestAPI.LOGGER.warn("[TeleportReward] Could not find a safe teleport spot near {} for structure {} - reward not granted",
                        structurePos.get(), structureId);
                return;
            }

            BlockPos target = safeSpot.get();
            player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot());
        }

        @Override
        public Component displayText() {
            return Component.translatable("reward.quest_api.teleport_to_structure", TextFormatting.titleCaseWords(structureId.getPath()));
        }
    }

    public record ToCoordinates(double x, double y, double z, Optional<ResourceLocation> dimension) implements TeleportTarget {
        public static final MapCodec<ToCoordinates> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.DOUBLE.fieldOf("x").forGetter(ToCoordinates::x),
                        Codec.DOUBLE.fieldOf("y").forGetter(ToCoordinates::y),
                        Codec.DOUBLE.fieldOf("z").forGetter(ToCoordinates::z),
                        ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(ToCoordinates::dimension)
                ).apply(instance, ToCoordinates::new)
        );

        @Override
        public String kind() {
            return "coordinates";
        }

        // level/origin are unused - fixed coordinates are resolved from the player's own dimension
        // (or the explicit override below), never from entity/quest-giver context.
        @Override
        public void grantFrom(ServerPlayer player, ServerLevel level, BlockPos origin) {
            ServerLevel resolvedLevel = player.serverLevel();
            if (dimension.isPresent()) {
                ServerLevel dimLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension.get()));
                if (dimLevel != null) {
                    resolvedLevel = dimLevel;
                } else {
                    QuestAPI.LOGGER.warn("[TeleportReward] Dimension {} not found - staying in current dimension", dimension.get());
                }
            }
            player.teleportTo(resolvedLevel, x, y, z, Set.of(), player.getYRot(), player.getXRot());
        }

        @Override
        public Component displayText() {
            return Component.translatable("reward.quest_api.teleport_to_coordinates", (int) x, (int) y, (int) z);
        }
    }

    // Same range-gated shape as ToStructure; no-op with a warning if no instance of the biome is found in range.
    public record ToBiome(ResourceLocation biomeId, int maxDistanceRange) implements TeleportTarget {
        public static final MapCodec<ToBiome> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        ResourceLocation.CODEC.fieldOf("biome_id").forGetter(ToBiome::biomeId),
                        Codec.intRange(1, Integer.MAX_VALUE)
                                .optionalFieldOf("max_distance_range", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                                .forGetter(ToBiome::maxDistanceRange)
                ).apply(instance, ToBiome::new)
        );

        @Override
        public String kind() {
            return "biome";
        }

        @Override
        public void grantFrom(ServerPlayer player, ServerLevel level, BlockPos origin) {
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                    holder -> holder.unwrapKey().map(key -> key.location().equals(biomeId)).orElse(false),
                    origin, maxDistanceRange, 32, 64);

            if (found == null) {
                QuestAPI.LOGGER.warn("[TeleportReward] No instance of biome {} found within {} blocks of {} - reward not granted",
                        biomeId, maxDistanceRange, origin);
                return;
            }

            BlockPos biomePos = found.getFirst();
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, biomePos.getX(), biomePos.getZ());
            player.teleportTo(level, biomePos.getX() + 0.5, surfaceY, biomePos.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot());
        }

        @Override
        public Component displayText() {
            return Component.translatable("reward.quest_api.teleport_to_biome", TextFormatting.titleCaseWords(biomeId.getPath()));
        }
    }
}

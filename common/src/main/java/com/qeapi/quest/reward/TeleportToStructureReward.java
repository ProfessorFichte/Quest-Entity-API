package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.util.StructureDistanceUtil;
import com.qeapi.util.StructureTeleportUtil;
import com.qeapi.util.TextFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;
import java.util.Set;

// falls back to the player's own position without entity context, same as MapToStructureReward.
// "safe" here means outside the structure's interior, not suffocating, not in lava - see
// StructureTeleportUtil.findSafeSpotNearStructure.
public record TeleportToStructureReward(
        ResourceLocation structureId,
        int maxDistanceRange,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward, EntityAwareReward {

    public TeleportToStructureReward(ResourceLocation structureId, int maxDistanceRange) {
        this(structureId, maxDistanceRange, Optional.empty());
    }

    public static final MapCodec<TeleportToStructureReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("structure_id").forGetter(TeleportToStructureReward::structureId),
                    Codec.intRange(1, Integer.MAX_VALUE)
                            .optionalFieldOf("max_distance_range", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                            .forGetter(TeleportToStructureReward::maxDistanceRange),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(TeleportToStructureReward::textureOverrideId)
            ).apply(instance, TeleportToStructureReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("teleport_to_structure");
    }

    @Override
    public void grant(ServerPlayer player) {
        grantFrom(player, player.serverLevel(), player.blockPosition());
    }

    @Override
    public void grantWithEntity(ServerPlayer player, Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            grantFrom(player, serverLevel, entity.blockPosition());
        } else {
            grant(player);
        }
    }

    @SuppressWarnings("unchecked")
    private void grantFrom(ServerPlayer player, ServerLevel level, BlockPos origin) {
        Optional<Holder<Structure>> structureHolder = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(structureId)
                .map(h -> (Holder<Structure>) h);

        if (structureHolder.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[TeleportToStructureReward] Structure {} not found in registry", structureId);
            return;
        }

        Optional<BlockPos> structurePos = StructureDistanceUtil.findNearestStructure(level, origin, structureId, maxDistanceRange);
        if (structurePos.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[TeleportToStructureReward] No instance of {} found within {} blocks of {} - reward not granted",
                    structureId, maxDistanceRange, origin);
            return;
        }

        Optional<BlockPos> safeSpot = StructureTeleportUtil.findSafeSpotNearStructure(level, structurePos.get(), structureHolder.get());
        if (safeSpot.isEmpty()) {
            QuestEntityAPI.LOGGER.warn("[TeleportToStructureReward] Could not find a safe teleport spot near {} for structure {} - reward not granted",
                    structurePos.get(), structureId);
            return;
        }

        BlockPos target = safeSpot.get();
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.teleport_to_structure", TextFormatting.titleCaseWords(structureId.getPath()));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.ENDER_PEARL));
    }

    public static TeleportToStructureReward of(ResourceLocation structureId) {
        return new TeleportToStructureReward(structureId, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }
}

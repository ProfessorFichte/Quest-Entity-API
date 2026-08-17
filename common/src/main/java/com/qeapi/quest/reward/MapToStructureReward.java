package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.util.StructureDistanceUtil;
import com.qeapi.util.StructureMapUtil;
import com.qeapi.util.TextFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

// falls back to the player's own position when granted without entity context (e.g. via
// QuestEntityAccess's direct-completion API), instead of failing outright
public record MapToStructureReward(
        ResourceLocation structureId,
        int maxDistanceRange,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward, EntityAwareReward {

    public MapToStructureReward(ResourceLocation structureId, int maxDistanceRange) {
        this(structureId, maxDistanceRange, Optional.empty());
    }

    public static final MapCodec<MapToStructureReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("structure_id").forGetter(MapToStructureReward::structureId),
                    Codec.intRange(1, Integer.MAX_VALUE)
                            .optionalFieldOf("max_distance_range", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                            .forGetter(MapToStructureReward::maxDistanceRange),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(MapToStructureReward::textureOverrideId)
            ).apply(instance, MapToStructureReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("map_to_structure");
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

    private void grantFrom(ServerPlayer player, ServerLevel level, BlockPos origin) {
        Optional<ItemStack> mapStack = StructureMapUtil.createMapToStructure(level, origin, structureId, maxDistanceRange);
        if (mapStack.isEmpty()) {
            QuestAPI.LOGGER.warn("[MapToStructureReward] No instance of {} found within {} blocks of {} - reward not granted",
                    structureId, maxDistanceRange, origin);
            return;
        }
        if (!player.getInventory().add(mapStack.get())) {
            player.drop(mapStack.get(), false);
        }
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.map_to_structure", TextFormatting.titleCaseWords(structureId.getPath()));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.MAP));
    }

    public static MapToStructureReward of(ResourceLocation structureId) {
        return new MapToStructureReward(structureId, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }
}

package com.qeapi.quest.reward;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.util.StructureDistanceUtil;
import com.qeapi.util.TextFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.Set;

// Finds the nearest instance of a biome (searched from the player, or the quest-giver when there's
// entity context) and teleports the player to a surface spot there. No-op with a warning if none is
// found in range - same range-gated shape as TeleportToStructureReward.
public record TeleportToBiomeReward(
        ResourceLocation biomeId,
        int maxDistanceRange,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward, EntityAwareReward {

    public TeleportToBiomeReward(ResourceLocation biomeId, int maxDistanceRange) {
        this(biomeId, maxDistanceRange, Optional.empty());
    }

    public static final MapCodec<TeleportToBiomeReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("biome_id").forGetter(TeleportToBiomeReward::biomeId),
                    Codec.intRange(1, Integer.MAX_VALUE)
                            .optionalFieldOf("max_distance_range", StructureDistanceUtil.DEFAULT_MAX_DISTANCE)
                            .forGetter(TeleportToBiomeReward::maxDistanceRange),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(TeleportToBiomeReward::textureOverrideId)
            ).apply(instance, TeleportToBiomeReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("teleport_to_biome");
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
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                holder -> holder.unwrapKey().map(key -> key.location().equals(biomeId)).orElse(false),
                origin, maxDistanceRange, 32, 64);

        if (found == null) {
            QuestEntityAPI.LOGGER.warn("[TeleportToBiomeReward] No instance of biome {} found within {} blocks of {} - reward not granted",
                    biomeId, maxDistanceRange, origin);
            return;
        }

        BlockPos biomePos = found.getFirst();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, biomePos.getX(), biomePos.getZ());
        player.teleportTo(level, biomePos.getX() + 0.5, surfaceY, biomePos.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.teleport_to_biome", TextFormatting.titleCaseWords(biomeId.getPath()));
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.ENDER_PEARL));
    }

    public static TeleportToBiomeReward of(ResourceLocation biomeId) {
        return new TeleportToBiomeReward(biomeId, StructureDistanceUtil.DEFAULT_MAX_DISTANCE, Optional.empty());
    }
}

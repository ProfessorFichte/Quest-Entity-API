package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

public record TeleportToCoordinatesReward(
        double x,
        double y,
        double z,
        Optional<ResourceLocation> dimension,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<TeleportToCoordinatesReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("x").forGetter(TeleportToCoordinatesReward::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(TeleportToCoordinatesReward::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(TeleportToCoordinatesReward::z),
                    ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(TeleportToCoordinatesReward::dimension),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(TeleportToCoordinatesReward::textureOverrideId)
            ).apply(instance, TeleportToCoordinatesReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("teleport_to_coordinates");
    }

    @Override
    public void grant(ServerPlayer player) {
        ServerLevel target = player.serverLevel();
        if (dimension.isPresent()) {
            ServerLevel dimLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension.get()));
            if (dimLevel != null) {
                target = dimLevel;
            } else {
                QuestEntityAPI.LOGGER.warn("[TeleportToCoordinatesReward] Dimension {} not found - staying in current dimension", dimension.get());
            }
        }
        player.teleportTo(target, x, y, z, Set.of(), player.getYRot(), player.getXRot());
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.teleport_to_coordinates", (int) x, (int) y, (int) z);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.ENDER_PEARL));
    }

    public static TeleportToCoordinatesReward of(double x, double y, double z) {
        return new TeleportToCoordinatesReward(x, y, z, Optional.empty(), Optional.empty());
    }

    public static TeleportToCoordinatesReward of(double x, double y, double z, ResourceLocation dimension) {
        return new TeleportToCoordinatesReward(x, y, z, Optional.of(dimension), Optional.empty());
    }
}

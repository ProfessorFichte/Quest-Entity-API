package com.qeapi.quest.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record RepairItemReward(Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward, EnhanceOperation {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_rewards/repair_item_default.png");

    public RepairItemReward() {
        this(Optional.empty());
    }

    public static final MapCodec<RepairItemReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(RepairItemReward::textureOverrideId)
            ).apply(instance, RepairItemReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("repair_item");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) && stack.isDamaged();
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        stack.set(DataComponents.DAMAGE, 0);
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestAPI.LOGGER.warn("[RepairItemReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.repair_item");
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

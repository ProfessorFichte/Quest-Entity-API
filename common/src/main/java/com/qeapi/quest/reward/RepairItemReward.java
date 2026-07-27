package com.qeapi.quest.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

// Fully repairs a player-chosen item's durability. Only offers items that actually have
// durability and are currently damaged.
public record RepairItemReward() implements QuestReward, TargetItemReward, EnhanceOperation {

    public static final MapCodec<RepairItemReward> CODEC = MapCodec.unit(RepairItemReward::new);

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("repair_item");
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
        QuestEntityAPI.LOGGER.warn("[RepairItemReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.repair_item");
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

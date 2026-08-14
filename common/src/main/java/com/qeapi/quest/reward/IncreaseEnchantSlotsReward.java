package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.EnchantLimiterCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record IncreaseEnchantSlotsReward(int amount, int cap, Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward, EnhanceOperation {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_rewards/increase_enchant_slots_default.png");

    public IncreaseEnchantSlotsReward(int amount, int cap) {
        this(amount, cap, Optional.empty());
    }

    public static final MapCodec<IncreaseEnchantSlotsReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(IncreaseEnchantSlotsReward::amount),
                    Codec.INT.optionalFieldOf("cap", Integer.MAX_VALUE).forGetter(IncreaseEnchantSlotsReward::cap),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(IncreaseEnchantSlotsReward::textureOverrideId)
            ).apply(instance, IncreaseEnchantSlotsReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("increase_enchant_slots");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        if (!EnchantLimiterCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[IncreaseEnchantSlotsReward] Enchant Limiter isn't loaded - skipping reward");
            return;
        }
        EnchantLimiterCompat.increaseEnchantLimit(stack, amount, cap);
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[IncreaseEnchantSlotsReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.increase_enchant_slots", amount);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

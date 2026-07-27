package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

// (Dungeon Difficulty compat) Raises a player-chosen item's power level by amount, up to cap -
// unlike set_power_level (an ItemFunction applied to a freshly granted stack), this reads and
// raises whatever level the item already has. No-op with a warning if Dungeon Difficulty isn't
// loaded.
public record IncreasePowerLevelReward(int amount, int cap) implements QuestReward, TargetItemReward, EnhanceOperation {

    public static final MapCodec<IncreasePowerLevelReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(IncreasePowerLevelReward::amount),
                    Codec.INT.optionalFieldOf("cap", Integer.MAX_VALUE).forGetter(IncreasePowerLevelReward::cap)
            ).apply(instance, IncreasePowerLevelReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("increase_power_level");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        if (!DungeonDifficultyCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[IncreasePowerLevelReward] Dungeon Difficulty isn't loaded - skipping reward");
            return;
        }
        DungeonDifficultyCompat.increasePowerLevel(stack, amount, cap);
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[IncreasePowerLevelReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.increase_power_level", amount);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

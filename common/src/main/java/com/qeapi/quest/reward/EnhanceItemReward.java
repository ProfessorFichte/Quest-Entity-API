package com.qeapi.quest.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

// Bundles several enhance operations onto one target item so the player only picks once instead
// of once per operation. applyToTarget re-checks each op against the chosen item and skips any
// that don't actually fit it.
public record EnhanceItemReward(List<EnhanceOperation> operations, Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward {

    public EnhanceItemReward(List<EnhanceOperation> operations) {
        this(operations, Optional.empty());
    }

    public static final MapCodec<EnhanceItemReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EnhanceOperation.CODEC.listOf().fieldOf("operations").forGetter(EnhanceItemReward::operations),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EnhanceItemReward::textureOverrideId)
            ).apply(instance, EnhanceItemReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("enhance_item");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return operations.stream().anyMatch(op -> op.isValidTarget(level, stack));
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        for (EnhanceOperation op : operations) {
            if (op.isValidTarget(player.level(), stack)) {
                op.applyToTarget(player, stack);
            } else {
                QuestEntityAPI.LOGGER.warn("[EnhanceItemReward] Skipping {} - not valid for {}",
                        op.getTypeId(), stack.getItem());
            }
        }
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestEntityAPI.LOGGER.warn("[EnhanceItemReward] grant(player) called without a target item - ignoring");
    }

    @Override
    public Component getDisplayText() {
        MutableComponent combined = Component.empty();
        for (int i = 0; i < operations.size(); i++) {
            if (i > 0) combined.append(", ");
            combined.append(operations.get(i).getDisplayText());
        }
        return combined;
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

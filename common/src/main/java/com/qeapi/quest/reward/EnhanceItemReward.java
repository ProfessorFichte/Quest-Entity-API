package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

// Bundles several enhance operations onto one target item so the player only picks once instead of per-operation.
public record EnhanceItemReward(List<EnhanceOperation> operations, Optional<TagKey<Item>> restrictionItemTag,
                                 Optional<ResourceLocation> textureOverrideId) implements QuestReward, TargetItemReward {

    public EnhanceItemReward(List<EnhanceOperation> operations) {
        this(operations, Optional.empty(), Optional.empty());
    }

    public EnhanceItemReward(List<EnhanceOperation> operations, Optional<ResourceLocation> textureOverrideId) {
        this(operations, Optional.empty(), textureOverrideId);
    }

    public static final MapCodec<EnhanceItemReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EnhanceOperation.CODEC.listOf().fieldOf("operations").forGetter(EnhanceItemReward::operations),
                    TagKey.codec(Registries.ITEM).optionalFieldOf("restriction_item_tag").forGetter(EnhanceItemReward::restrictionItemTag),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(EnhanceItemReward::textureOverrideId)
            ).apply(instance, EnhanceItemReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("enhance_item");
    }

    @Override
    public boolean isValidTarget(Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (restrictionItemTag.isPresent() && !stack.is(restrictionItemTag.get())) return false;
        return operations.stream().anyMatch(op -> op.isValidTarget(level, stack));
    }

    @Override
    public void applyToTarget(ServerPlayer player, ItemStack stack) {
        for (EnhanceOperation op : operations) {
            if (op.isValidTarget(player.level(), stack)) {
                op.applyToTarget(player, stack);
            } else {
                QuestAPI.LOGGER.warn("[EnhanceItemReward] Skipping {} - not valid for {}",
                        op.getTypeId(), stack.getItem());
            }
        }
    }

    @Override
    public void grant(ServerPlayer player) {
        QuestAPI.LOGGER.warn("[EnhanceItemReward] grant(player) called without a target item - ignoring");
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

package com.qeapi.quest.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

public record AdvancementReward(
        ResourceLocation advancementId,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<AdvancementReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("advancement_id").forGetter(AdvancementReward::advancementId),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(AdvancementReward::textureOverrideId)
            ).apply(instance, AdvancementReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("advancement");
    }

    @Override
    public void grant(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(advancementId);

        if (advancement == null) {
            QuestAPI.LOGGER.warn("AdvancementReward: Advancement {} not found", advancementId);
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }

        QuestAPI.LOGGER.debug("Granted advancement {} to player {}",
                advancementId, player.getName().getString());
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.advancement", advancementId.toString());
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.KNOWLEDGE_BOOK));
    }

    public static AdvancementReward of(ResourceLocation advancementId) {
        return new AdvancementReward(advancementId, Optional.empty());
    }

    public static AdvancementReward of(String advancementId) {
        return new AdvancementReward(ResourceLocation.parse(advancementId), Optional.empty());
    }
}

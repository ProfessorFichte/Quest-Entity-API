package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

// Reward that grants experience points to the player.
public record ExperienceReward(
        int amount
) implements QuestReward {

    public static final MapCodec<ExperienceReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("amount").forGetter(ExperienceReward::amount)
            ).apply(instance, ExperienceReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("experience");
    }

    @Override
    public void grant(ServerPlayer player) {
        player.giveExperiencePoints(amount);
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.experience", amount);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.of(new ItemStack(Items.EXPERIENCE_BOTTLE));
    }

    public static ExperienceReward of(int amount) {
        return new ExperienceReward(amount);
    }
}

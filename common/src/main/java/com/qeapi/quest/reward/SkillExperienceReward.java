package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.compat.PufferfishSkillsCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// Pufferfish's Skills doesn't expose a category's icon through its API (synced lazily per-category, per-player) -
// texture_override_id lets a quest author point directly at that skill tree's own icon.
public record SkillExperienceReward(
        ResourceLocation skillTreeId,
        int amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public static final MapCodec<SkillExperienceReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("skill_tree_id").forGetter(SkillExperienceReward::skillTreeId),
                    Codec.INT.fieldOf("amount").forGetter(SkillExperienceReward::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(SkillExperienceReward::textureOverrideId)
            ).apply(instance, SkillExperienceReward::new)
    );

    public SkillExperienceReward(ResourceLocation skillTreeId, int amount) {
        this(skillTreeId, amount, Optional.empty());
    }

    @Override
    public ResourceLocation getTypeId() {
        return ResourceLocation.fromNamespaceAndPath("puffish_skills", "skill_experience");
    }

    @Override
    public void grant(ServerPlayer player) {
        if (!PufferfishSkillsCompat.isLoaded()) {
            QuestAPI.LOGGER.warn("[SkillExperienceReward] Pufferfish's Skills isn't loaded - skipping reward for {}", skillTreeId);
            return;
        }
        PufferfishSkillsCompat.addExperience(player, skillTreeId, amount);
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.quest_api.skill_experience", amount, skillTreeId.toString());
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.PufferfishSkillsCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// Reward that grants experience in a specific Pufferfish's Skills skill tree. No-op with a
// warning if Pufferfish's Skills isn't loaded.
//
// Pufferfish's Skills doesn't expose a category's icon through its stable cross-mod API (the
// client-side icon data is internal and only synced lazily per-category, per-player), so `icon`
// lets a quest author reference that skill tree's own icon texture directly (from its
// category.json, e.g. "skill_tree_rpgs:textures/gui/icon.png"). Falls back to a generic
// experience-bottle icon if omitted.
public record SkillExperienceReward(
        ResourceLocation skillTreeId,
        int amount,
        Optional<ResourceLocation> icon
) implements QuestReward {

    public static final MapCodec<SkillExperienceReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("skill_tree_id").forGetter(SkillExperienceReward::skillTreeId),
                    Codec.INT.fieldOf("amount").forGetter(SkillExperienceReward::amount),
                    ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(SkillExperienceReward::icon)
            ).apply(instance, SkillExperienceReward::new)
    );

    public SkillExperienceReward(ResourceLocation skillTreeId, int amount) {
        this(skillTreeId, amount, Optional.empty());
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("skill_experience");
    }

    @Override
    public void grant(ServerPlayer player) {
        if (!PufferfishSkillsCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[SkillExperienceReward] Pufferfish's Skills isn't loaded - skipping reward for {}", skillTreeId);
            return;
        }
        PufferfishSkillsCompat.addExperience(player, skillTreeId, amount);
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.skill_experience", amount, skillTreeId.toString());
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

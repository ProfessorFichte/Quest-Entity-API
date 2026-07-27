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

// Reward that grants whole levels in a specific Pufferfish's Skills skill tree. No-op with a
// warning if Pufferfish's Skills isn't loaded. See SkillExperienceReward's note on `icon` -
// same reasoning applies here.
public record SkillLevelReward(
        ResourceLocation skillTreeId,
        int levels,
        Optional<ResourceLocation> icon
) implements QuestReward {

    public static final MapCodec<SkillLevelReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("skill_tree_id").forGetter(SkillLevelReward::skillTreeId),
                    Codec.INT.fieldOf("levels").forGetter(SkillLevelReward::levels),
                    ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(SkillLevelReward::icon)
            ).apply(instance, SkillLevelReward::new)
    );

    public SkillLevelReward(ResourceLocation skillTreeId, int levels) {
        this(skillTreeId, levels, Optional.empty());
    }

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("skill_level");
    }

    @Override
    public void grant(ServerPlayer player) {
        if (!PufferfishSkillsCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[SkillLevelReward] Pufferfish's Skills isn't loaded - skipping reward for {}", skillTreeId);
            return;
        }
        PufferfishSkillsCompat.addLevels(player, skillTreeId, levels);
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.skill_level", levels, skillTreeId.toString());
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

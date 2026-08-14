package com.qeapi.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.LevelZCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

// LevelZ skills are level-only, no per-skill XP pool to grant partial progress into, so this is
// the only LevelZ reward type. LevelZ already gives each skill a standalone icon by convention
// (LevelZCompat.skillIcon), so there's no need for a quest author to set one manually.
public record LevelZSkillLevelReward(
        String skillId,
        int levels,
        Optional<ResourceLocation> textureOverrideId
) implements QuestReward {

    public LevelZSkillLevelReward(String skillId, int levels) {
        this(skillId, levels, Optional.empty());
    }

    public static final MapCodec<LevelZSkillLevelReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("skill_id").forGetter(LevelZSkillLevelReward::skillId),
                    Codec.INT.fieldOf("levels").forGetter(LevelZSkillLevelReward::levels),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(LevelZSkillLevelReward::textureOverrideId)
            ).apply(instance, LevelZSkillLevelReward::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("levelz_skill_level");
    }

    @Override
    public void grant(ServerPlayer player) {
        if (!LevelZCompat.isLoaded()) {
            QuestEntityAPI.LOGGER.warn("[LevelZSkillLevelReward] LevelZ isn't loaded - skipping reward for {}", skillId);
            return;
        }
        LevelZCompat.addSkillLevels(player, skillId, levels);
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("reward.qe_api.levelz_skill_level", levels, skillId);
    }

    @Override
    public Optional<ItemStack> getDisplayItem() {
        return Optional.empty();
    }
}

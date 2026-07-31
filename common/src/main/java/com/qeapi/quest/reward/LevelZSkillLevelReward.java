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

// Reward that grants whole levels in a specific LevelZ skill (e.g. "melee", "mining"). No-op with
// a warning if LevelZ isn't loaded. LevelZ skills are level-only (no per-skill XP pool to grant
// partial progress into), so this is the only LevelZ reward type. Unlike Pufferfish's Skills
// rewards, LevelZ ships one standalone icon per skill addressed by convention (see
// LevelZCompat.skillIcon), so there's no need for a quest author to specify one manually.
public record LevelZSkillLevelReward(
        String skillId,
        int levels
) implements QuestReward {

    public static final MapCodec<LevelZSkillLevelReward> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("skill_id").forGetter(LevelZSkillLevelReward::skillId),
                    Codec.INT.fieldOf("levels").forGetter(LevelZSkillLevelReward::levels)
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

package com.qeapi.quest.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.LevelZCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

// Requirement that the player has a minimum level in a specific LevelZ skill (e.g. "melee",
// "mining" - see LevelZ's data/levelz/skill/default.json for the full list). No-op (never met) if
// LevelZ isn't loaded.
public record HasLevelZSkillRequirement(
        String skillId,
        int level
) implements QuestRequirement {

    public static final MapCodec<HasLevelZSkillRequirement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("skill_id").forGetter(HasLevelZSkillRequirement::skillId),
                    Codec.INT.fieldOf("level").forGetter(HasLevelZSkillRequirement::level)
            ).apply(instance, HasLevelZSkillRequirement::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("has_levelz_skill");
    }

    @Override
    public boolean isMet(ServerPlayer player) {
        if (!LevelZCompat.isLoaded()) return false;
        return LevelZCompat.getSkillLevel(player, skillId) >= level;
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("requirement.qe_api.has_levelz_skill", level, skillId);
    }

    @Override
    public Component getFailureMessage() {
        return Component.translatable("requirement.qe_api.has_levelz_skill.failure", level, skillId);
    }

    @Override
    public boolean isMetClientSide(Player player) {
        if (!LevelZCompat.isLoaded()) return false;
        return LevelZCompat.getSkillLevel(player, skillId) >= level;
    }

    @Override
    public boolean canCheckClientSide() {
        return true;
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return LevelZCompat.isLoaded() ? Optional.of(LevelZCompat.skillIcon(skillId)) : Optional.empty();
    }

    public static HasLevelZSkillRequirement of(String skillId, int level) {
        return new HasLevelZSkillRequirement(skillId, level);
    }
}

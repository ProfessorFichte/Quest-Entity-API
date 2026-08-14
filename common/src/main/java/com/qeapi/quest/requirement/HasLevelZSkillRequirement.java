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

// e.g. "melee", "mining" - see LevelZ's data/levelz/skill/default.json for the full list. Never
// met if LevelZ isn't loaded, so a quest gated on this becomes permanently unavailable without it.
public record HasLevelZSkillRequirement(
        String skillId,
        int level,
        Optional<ResourceLocation> textureOverrideId
) implements QuestRequirement {

    public static final MapCodec<HasLevelZSkillRequirement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("skill_id").forGetter(HasLevelZSkillRequirement::skillId),
                    Codec.INT.fieldOf("level").forGetter(HasLevelZSkillRequirement::level),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(HasLevelZSkillRequirement::textureOverrideId)
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
        if (textureOverrideId.isPresent()) return textureOverrideId;
        return LevelZCompat.isLoaded() ? Optional.of(LevelZCompat.skillIcon(skillId)) : Optional.empty();
    }

    public static HasLevelZSkillRequirement of(String skillId, int level) {
        return new HasLevelZSkillRequirement(skillId, level, Optional.empty());
    }
}

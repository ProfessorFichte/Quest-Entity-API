package com.qeapi.quest.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public record HasLevelRequirement(
        int experienceLevel,
        Optional<ResourceLocation> textureOverrideId
) implements QuestRequirement {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_requirements/has_level.png");

    public static final MapCodec<HasLevelRequirement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("experience_level").forGetter(HasLevelRequirement::experienceLevel),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(HasLevelRequirement::textureOverrideId)
            ).apply(instance, HasLevelRequirement::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("has_level");
    }

    @Override
    public boolean isMet(ServerPlayer player) {
        return player.experienceLevel >= experienceLevel;
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("requirement.quest_api.has_level", experienceLevel);
    }

    @Override
    public Component getFailureMessage() {
        return Component.translatable("requirement.quest_api.has_level.failure", experienceLevel);
    }

    @Override
    public boolean isMetClientSide(Player player) {
        return player.experienceLevel >= experienceLevel;
    }

    @Override
    public boolean canCheckClientSide() {
        return true;
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    public static HasLevelRequirement of(int level) {
        return new HasLevelRequirement(level, Optional.empty());
    }
}

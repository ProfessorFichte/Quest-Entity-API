package com.qeapi.quest.requirement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.util.TextFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public record HasAdvancementRequirement(
        ResourceLocation advancementId,
        Optional<ResourceLocation> textureOverrideId
) implements QuestRequirement {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_requirements/has_advancement.png");

    public static final MapCodec<HasAdvancementRequirement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("advancement_id").forGetter(HasAdvancementRequirement::advancementId),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(HasAdvancementRequirement::textureOverrideId)
            ).apply(instance, HasAdvancementRequirement::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("has_advancement");
    }

    @Override
    public boolean isMet(ServerPlayer player) {
        ServerAdvancementManager manager = player.server.getAdvancements();
        AdvancementHolder advancement = manager.get(advancementId);
        if (advancement == null) {
            QuestEntityAPI.LOGGER.warn("Advancement not found: {}", advancementId);
            return false;
        }
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @Override
    public Component getDisplayText() {
        return Component.translatable("requirement.qe_api.has_advancement", getAdvancementText());
    }

    @Override
    public Component getFailureMessage() {
        return Component.translatable("requirement.qe_api.has_advancement.failure", getAdvancementText());
    }

    private String getAdvancementText() {
        String translationKey = "advancements." + advancementId.getNamespace() + "." +
                advancementId.getPath().replace("/", ".") + ".title";
        String advancementText = Component.translatable(translationKey).getString();

        if (advancementText.equals(translationKey)) {
            // e.g. "adventure/kill_a_mob" -> "Adventure Kill A Mob"
            advancementText = TextFormatting.titleCaseWords(advancementId.getPath().replace("/", "_"));
        }

        return advancementText;
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    public static HasAdvancementRequirement of(ResourceLocation advancementId) {
        return new HasAdvancementRequirement(advancementId, Optional.empty());
    }

    public static HasAdvancementRequirement of(String advancementId) {
        return new HasAdvancementRequirement(ResourceLocation.parse(advancementId), Optional.empty());
    }
}

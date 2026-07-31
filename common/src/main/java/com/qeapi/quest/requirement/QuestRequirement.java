package com.qeapi.quest.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.qeapi.QuestEntityAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// A requirement that must be met before a player can accept a quest.
public sealed interface QuestRequirement permits
        HasAdvancementRequirement,
        HasItemRequirement,
        HasLevelRequirement,
        HasLevelZSkillRequirement {

    Map<ResourceLocation, RequirementType<?>> REQUIREMENT_TYPES = new HashMap<>();

    Codec<QuestRequirement> CODEC = Codec.lazyInitialized(() ->
            ResourceLocation.CODEC.dispatch(
                    "requirement",
                    QuestRequirement::getTypeId,
                    id -> {
                        RequirementType<?> type = REQUIREMENT_TYPES.get(id);
                        if (type == null) {
                            throw new IllegalArgumentException("Unknown requirement type: " + id);
                        }
                        return type.mapCodec();
                    }
            )
    );

    ResourceLocation getTypeId();

    boolean isMet(ServerPlayer player);

    Component getDisplayText();

    Component getFailureMessage();

    // only meaningful when canCheckClientSide() is true; defaults to false (cannot determine)
    default boolean isMetClientSide(Player player) {
        return false;
    }

    default boolean canCheckClientSide() {
        return false;
    }

    default Optional<ResourceLocation> getDisplayTexture() {
        return Optional.empty();
    }

    static <T extends QuestRequirement> void registerType(ResourceLocation id, MapCodec<T> codec) {
        REQUIREMENT_TYPES.put(id, new RequirementType<>(id, codec));
    }

    static void registerBuiltInTypes() {
        registerType(QuestEntityAPI.id("has_advancement"), HasAdvancementRequirement.CODEC);
        registerType(QuestEntityAPI.id("has_item"), HasItemRequirement.CODEC);
        registerType(QuestEntityAPI.id("has_level"), HasLevelRequirement.CODEC);
        registerType(QuestEntityAPI.id("has_levelz_skill"), HasLevelZSkillRequirement.CODEC);
    }

    record RequirementType<T extends QuestRequirement>(ResourceLocation id, MapCodec<T> codec) {
        @SuppressWarnings("unchecked")
        public MapCodec<QuestRequirement> mapCodec() {
            return (MapCodec<QuestRequirement>) (MapCodec<?>) codec;
        }
    }
}

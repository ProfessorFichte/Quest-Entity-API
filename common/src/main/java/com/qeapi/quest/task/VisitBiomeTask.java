package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.compat.DungeonDifficultyCompat;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.LocationMatchUtil;
import com.qeapi.util.TextMutator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// no biome_id/biome_tag/biome_ids at all matches any biome - amount then counts distinct biomes
// visited overall, tracked via PlayerQuestData.visitedBiomes since QuestProgress's int-only map
// can't hold the visited-id set itself
public record VisitBiomeTask(
        Optional<ResourceLocation> biomeId,
        Optional<TagKey<Biome>> biomeTag,
        List<ResourceLocation> biomeIds,
        int amount,
        Optional<Integer> minPowerLevel,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/visit_biome_default.png");

    public static final MapCodec<VisitBiomeTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("biome_id").forGetter(VisitBiomeTask::biomeId),
                    TagKey.codec(Registries.BIOME).optionalFieldOf("biome_tag").forGetter(VisitBiomeTask::biomeTag),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("biome_ids", List.of()).forGetter(VisitBiomeTask::biomeIds),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(VisitBiomeTask::amount),
                    Codec.INT.optionalFieldOf("min_power_level").forGetter(VisitBiomeTask::minPowerLevel),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(VisitBiomeTask::textureOverrideId)
            ).apply(instance, VisitBiomeTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("visit_biome");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "biome_amount", String.valueOf(amount),
                        "current_biomes", String.valueOf(current),
                        "biome_name", getBiomeDisplayName()
                )
        );
    }

    public String getBiomeDisplayName() {
        if (biomeId.isPresent()) {
            return biomeDisplayName(biomeId.get());
        }
        if (!biomeIds.isEmpty()) {
            if (biomeIds.size() == 1) {
                return biomeDisplayName(biomeIds.get(0));
            }
            return biomeDisplayName(biomeIds.get(0)) + " (+" + (biomeIds.size() - 1) + " others)";
        }
        if (biomeTag.isPresent()) {
            return "#" + biomeTag.get().location();
        }
        return "a biome";
    }

    private static String biomeDisplayName(ResourceLocation id) {
        String translationKey = "biome." + id.getNamespace() + "." + id.getPath();
        String translated = Component.translatable(translationKey).getString();
        return translated.equals(translationKey) ? id.toString() : translated;
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.visit_biome";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    // resolves the biome at pos to a concrete id (for the distinct-visited-set), only if it also
    // matches this task's selector and, when set, minPowerLevel at that location
    public Optional<ResourceLocation> matchedBiomeAt(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) return Optional.empty();
        ResourceLocation currentBiome = biomeKey.get().location();

        boolean selectorMatches;
        if (biomeId.isPresent() || !biomeIds.isEmpty() || biomeTag.isPresent()) {
            selectorMatches = (biomeId.isPresent() && LocationMatchUtil.isInBiome(level, pos, biomeId.get()))
                    || biomeIds.stream().anyMatch(id -> id.equals(currentBiome))
                    || (biomeTag.isPresent() && LocationMatchUtil.isInBiomeTag(level, pos, biomeTag.get()));
        } else {
            selectorMatches = true;
        }
        if (!selectorMatches) return Optional.empty();

        if (minPowerLevel.isPresent()) {
            if (!DungeonDifficultyCompat.isLoaded()) return Optional.empty();
            if (DungeonDifficultyCompat.getLocationPowerLevel(level, pos) < minPowerLevel.get()) return Optional.empty();
        }

        return Optional.of(currentBiome);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> biomeId = Optional.empty();
        private Optional<TagKey<Biome>> biomeTag = Optional.empty();
        private List<ResourceLocation> biomeIds = List.of();
        private int amount = 1;
        private Optional<Integer> minPowerLevel = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder biomeId(ResourceLocation id) {
            this.biomeId = Optional.of(id);
            return this;
        }

        public Builder biomeId(String id) {
            return biomeId(ResourceLocation.parse(id));
        }

        public Builder biomeTag(TagKey<Biome> tag) {
            this.biomeTag = Optional.of(tag);
            return this;
        }

        public Builder biomeIds(ResourceLocation... ids) {
            this.biomeIds = List.of(ids);
            return this;
        }

        public Builder biomeIds(String... ids) {
            this.biomeIds = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder minPowerLevel(int minPowerLevel) {
            this.minPowerLevel = Optional.of(minPowerLevel);
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public VisitBiomeTask build() {
            return new VisitBiomeTask(biomeId, biomeTag, biomeIds, amount, minPowerLevel, textureOverrideId);
        }
    }
}

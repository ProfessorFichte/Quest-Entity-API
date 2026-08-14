package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

// no crop_id/crop_tag falls back to the vanilla #minecraft:crops tag. Only counts a break as a
// harvest once the crop reached its final growth stage - a fully grown block breaks down via the
// same destroyBlock hook mine_block uses, so this only needs the extra age check on top of that.
public record HarvestCropsTask(
        Optional<ResourceLocation> cropId,
        Optional<TagKey<Block>> cropTag,
        int amount,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestEntityAPI.id("textures/gui/quest_tasks/harvest_crops_default.png");

    public static final MapCodec<HarvestCropsTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("crop_id").forGetter(HarvestCropsTask::cropId),
                    TagKey.codec(Registries.BLOCK).optionalFieldOf("crop_tag").forGetter(HarvestCropsTask::cropTag),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(HarvestCropsTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(HarvestCropsTask::textureOverrideId)
            ).apply(instance, HarvestCropsTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("harvest_crops");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String cropName = getCropDisplayName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "harvest_amount", String.valueOf(amount),
                        "current_harvested", String.valueOf(current),
                        "crop_name", cropName
                )
        );
    }

    public String getCropDisplayName() {
        if (cropId.isPresent()) {
            Block block = BuiltInRegistries.BLOCK.get(cropId.get());
            return block != null ? block.getName().getString() : cropId.get().toString();
        }
        if (cropTag.isPresent()) {
            return "#" + cropTag.get().location();
        }
        return "crops";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.harvest_crops";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(BlockState minedState) {
        boolean typeMatches;
        if (cropId.isPresent()) {
            ResourceLocation minedId = BuiltInRegistries.BLOCK.getKey(minedState.getBlock());
            typeMatches = minedId.equals(cropId.get());
        } else if (cropTag.isPresent()) {
            typeMatches = minedState.is(cropTag.get());
        } else {
            typeMatches = minedState.is(BlockTags.CROPS);
        }

        return typeMatches && isFullyGrown(minedState);
    }

    // CropBlock (wheat, carrots, potatoes, beetroot, ...) exposes isMaxAge() directly. Other
    // age-based plants (nether wart, stems) still use a plain "age" IntegerProperty, so falling
    // back to checking that property's own maximum covers those without hardcoding every block.
    // Anything without an age concept at all is treated as always harvestable.
    private static boolean isFullyGrown(BlockState state) {
        if (state.getBlock() instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }

        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && integerProperty.getName().equals("age")) {
                int current = state.getValue(integerProperty);
                int max = Collections.max(integerProperty.getPossibleValues());
                return current >= max;
            }
        }

        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> cropId = Optional.empty();
        private Optional<TagKey<Block>> cropTag = Optional.empty();
        private int amount = 1;
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder cropId(ResourceLocation id) {
            this.cropId = Optional.of(id);
            return this;
        }

        public Builder cropId(String id) {
            return cropId(ResourceLocation.parse(id));
        }

        public Builder crop(Block block) {
            return cropId(BuiltInRegistries.BLOCK.getKey(block));
        }

        public Builder cropTag(TagKey<Block> tag) {
            this.cropTag = Optional.of(tag);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public HarvestCropsTask build() {
            return new HarvestCropsTask(cropId, cropTag, amount, textureOverrideId);
        }
    }
}

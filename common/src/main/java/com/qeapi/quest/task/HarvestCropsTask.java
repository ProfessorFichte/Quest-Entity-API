package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
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

// no crop_id/crop_tag falls back to the vanilla #minecraft:crops tag; only a fully-grown break counts, layered on the same destroyBlock hook mine_block uses
public record HarvestCropsTask(
        Optional<ResourceLocation> cropId,
        Optional<TagKey<Block>> cropTag,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_tasks/harvest_crops_default.png");

    public static final MapCodec<HarvestCropsTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("crop_id").forGetter(HarvestCropsTask::cropId),
                    TagKey.codec(Registries.BLOCK).optionalFieldOf("crop_tag").forGetter(HarvestCropsTask::cropTag),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(HarvestCropsTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(HarvestCropsTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(HarvestCropsTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(HarvestCropsTask::textureOverrideId)
            ).apply(instance, HarvestCropsTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("harvest_crops");
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
        return "task.quest_api.harvest_crops";
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

    // non-CropBlock age plants (nether wart, stems) fall back to their raw "age" property max instead of hardcoding each block
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
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
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

        public Builder taskOrder(int order) {
            this.taskOrder = Optional.of(order);
            return this;
        }

        public Builder choiceGroup(String groupId) {
            this.choiceGroup = Optional.of(groupId);
            return this;
        }

        public Builder textureOverrideId(ResourceLocation id) {
            this.textureOverrideId = Optional.of(id);
            return this;
        }

        public HarvestCropsTask build() {
            return new HarvestCropsTask(cropId, cropTag, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

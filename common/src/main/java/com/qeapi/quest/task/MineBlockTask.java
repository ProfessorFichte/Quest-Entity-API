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
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;

public record MineBlockTask(
        Optional<ResourceLocation> blockId,
        Optional<TagKey<Block>> blockTag,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<MineBlockTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("block_id").forGetter(MineBlockTask::blockId),
                    TagKey.codec(Registries.BLOCK).optionalFieldOf("block_tag").forGetter(MineBlockTask::blockTag),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(MineBlockTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(MineBlockTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(MineBlockTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(MineBlockTask::textureOverrideId)
            ).apply(instance, MineBlockTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("mine_block");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String blockName = blockId.map(id -> {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return block != null ? block.getName().getString() : id.toString();
        }).orElseGet(() -> blockTag.map(tag -> "#" + tag.location()).orElse("a block"));

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "mine_amount", String.valueOf(amount),
                        "current_mined", String.valueOf(current),
                        "block_name", blockName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.mine_block";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(BlockState minedState) {
        ResourceLocation minedId = BuiltInRegistries.BLOCK.getKey(minedState.getBlock());

        if (blockId.isPresent()) {
            return minedId.equals(blockId.get());
        }
        if (blockTag.isPresent()) {
            return minedState.is(blockTag.get());
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> blockId = Optional.empty();
        private Optional<TagKey<Block>> blockTag = Optional.empty();
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder blockId(ResourceLocation id) {
            this.blockId = Optional.of(id);
            return this;
        }

        public Builder blockId(String id) {
            return blockId(ResourceLocation.parse(id));
        }

        public Builder block(Block block) {
            return blockId(BuiltInRegistries.BLOCK.getKey(block));
        }

        public Builder blockTag(TagKey<Block> tag) {
            this.blockTag = Optional.of(tag);
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

        public MineBlockTask build() {
            if (blockId.isEmpty() && blockTag.isEmpty()) {
                throw new IllegalStateException("MineBlockTask requires blockId or blockTag");
            }
            return new MineBlockTask(blockId, blockTag, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

// covers crafting table, the 2x2 grid, and Crafter blocks alike, since all of them funnel through ServerPlayer.triggerRecipeCrafted
public record CraftingTask(
        Optional<ResourceLocation> resultItemId,
        Optional<TagKey<Item>> resultItemTag,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final ResourceLocation DEFAULT_TEXTURE = QuestAPI.id("textures/gui/quest_tasks/crafting_default.png");

    public static final MapCodec<CraftingTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("result_item_id").forGetter(CraftingTask::resultItemId),
                    TagKey.codec(Registries.ITEM).optionalFieldOf("result_item_tag").forGetter(CraftingTask::resultItemTag),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(CraftingTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(CraftingTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(CraftingTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(CraftingTask::textureOverrideId)
            ).apply(instance, CraftingTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("crafting");
    }

    @Override
    public Optional<ResourceLocation> getDisplayTexture() {
        return Optional.of(textureOverrideId.orElse(DEFAULT_TEXTURE));
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        String itemName = getResultDisplayName();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "craft_amount", String.valueOf(amount),
                        "current_crafted", String.valueOf(current),
                        "item_name", itemName
                )
        );
    }

    public String getResultDisplayName() {
        if (resultItemId.isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(resultItemId.get());
            return item != null ? item.getDescription().getString() : resultItemId.get().toString();
        }
        if (resultItemTag.isPresent()) {
            return "#" + resultItemTag.get().location();
        }
        return "an item";
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.crafting";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack result) {
        if (result.isEmpty()) return false;

        if (resultItemId.isPresent()) {
            ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
            return resultId.equals(resultItemId.get());
        }
        if (resultItemTag.isPresent()) {
            return result.is(resultItemTag.get());
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> resultItemId = Optional.empty();
        private Optional<TagKey<Item>> resultItemTag = Optional.empty();
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder resultItemId(ResourceLocation id) {
            this.resultItemId = Optional.of(id);
            return this;
        }

        public Builder resultItemId(String id) {
            return resultItemId(ResourceLocation.parse(id));
        }

        public Builder resultItem(Item item) {
            return resultItemId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder resultItemTag(TagKey<Item> tag) {
            this.resultItemTag = Optional.of(tag);
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

        public CraftingTask build() {
            return new CraftingTask(resultItemId, resultItemTag, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

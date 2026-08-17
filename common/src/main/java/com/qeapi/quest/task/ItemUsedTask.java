package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

// "use" = right-click action (shooting a bow, eating food, using a tool, ...)
public record ItemUsedTask(
        ResourceLocation itemId,
        int amount,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<ItemUsedTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("item_id").forGetter(ItemUsedTask::itemId),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(ItemUsedTask::amount),
                    Codec.INT.optionalFieldOf("task_order").forGetter(ItemUsedTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(ItemUsedTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(ItemUsedTask::textureOverrideId)
            ).apply(instance, ItemUsedTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("item_used");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        int current = Math.min(progress.getTaskProgress(taskIndex), amount);
        Item item = BuiltInRegistries.ITEM.get(itemId);
        String itemName = item != null ? item.getDescription().getString() : itemId.toString();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "use_amount", String.valueOf(amount),
                        "current_uses", String.valueOf(current),
                        "item_name", itemName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.item_used";
    }

    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack usedItem) {
        if (usedItem.isEmpty()) return false;
        ResourceLocation usedItemId = BuiltInRegistries.ITEM.getKey(usedItem.getItem());
        return usedItemId.equals(itemId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ResourceLocation itemId;
        private int amount = 1;
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder itemId(ResourceLocation id) {
            this.itemId = id;
            return this;
        }

        public Builder itemId(String id) {
            return itemId(ResourceLocation.parse(id));
        }

        public Builder item(Item item) {
            return itemId(BuiltInRegistries.ITEM.getKey(item));
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

        public ItemUsedTask build() {
            if (itemId == null) {
                throw new IllegalStateException("ItemUsedTask requires itemId");
            }
            return new ItemUsedTask(itemId, amount, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

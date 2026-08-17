package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.TextMutator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

// exactly one of itemId/questItem is set - itemId for any registered item, questItem for an inline quest-only item (see QuestItemDefinition)
public record BringItemTask(
        Optional<ResourceLocation> itemId,
        Optional<QuestItemDefinition> questItem,
        int amount,
        Optional<ResourceLocation> hasComponent,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<BringItemTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("item_id").forGetter(BringItemTask::itemId),
                    QuestItemDefinition.CODEC.optionalFieldOf("quest_item").forGetter(BringItemTask::questItem),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(BringItemTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("has_component").forGetter(BringItemTask::hasComponent),
                    Codec.INT.optionalFieldOf("task_order").forGetter(BringItemTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(BringItemTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(BringItemTask::textureOverrideId)
            ).apply(instance, BringItemTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("bring_item");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "item_amount", String.valueOf(amount),
                        "item_name", getItemDisplayName()
                )
        );
    }

    public String getItemDisplayName() {
        if (itemId.isPresent()) {
            Item item = BuiltInRegistries.ITEM.get(itemId.get());
            return item != null ? item.getDescription().getString() : itemId.get().toString();
        }
        return questItem.map(def -> def.name().getString()).orElse("item");
    }

    public ItemStack getDisplayStack() {
        if (questItem.isPresent()) {
            return questItem.get().createStack(1);
        }
        return itemId.map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id))).orElse(ItemStack.EMPTY);
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.quest_api.bring_item";
    }

    // unlike other tasks, progress is computed from inventory contents at claim time (see NeoForgeNetworking/FabricNetworking), not tracked incrementally
    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (questItem.isPresent()) {
            return questItem.get().matches(stack);
        }

        ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!stackItemId.equals(itemId.orElse(null))) {
            return false;
        }

        if (hasComponent.isPresent()) {
            DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(hasComponent.get());
            if (componentType == null || !stack.has(componentType)) {
                return false;
            }
        }

        return true;
    }

    public int countMatchingItems(Iterable<ItemStack> inventory) {
        int count = 0;
        for (ItemStack stack : inventory) {
            if (matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> itemId = Optional.empty();
        private Optional<QuestItemDefinition> questItem = Optional.empty();
        private int amount = 1;
        private Optional<ResourceLocation> hasComponent = Optional.empty();
        private Optional<Integer> taskOrder = Optional.empty();
        private Optional<String> choiceGroup = Optional.empty();
        private Optional<ResourceLocation> textureOverrideId = Optional.empty();

        public Builder itemId(ResourceLocation id) {
            this.itemId = Optional.of(id);
            return this;
        }

        public Builder itemId(String id) {
            return itemId(ResourceLocation.parse(id));
        }

        public Builder item(Item item) {
            return itemId(BuiltInRegistries.ITEM.getKey(item));
        }

        public Builder questItem(QuestItemDefinition definition) {
            this.questItem = Optional.of(definition);
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder hasComponent(ResourceLocation componentId) {
            this.hasComponent = Optional.of(componentId);
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

        public BringItemTask build() {
            if (itemId.isEmpty() == questItem.isEmpty()) {
                throw new IllegalStateException("BringItemTask requires exactly one of itemId or questItem");
            }
            return new BringItemTask(itemId, questItem, amount, hasComponent, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

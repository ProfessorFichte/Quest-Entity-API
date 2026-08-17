package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestAPI;
import com.qeapi.item.QuestItemDefinition;
import com.qeapi.quest.QuestProgress;
import com.qeapi.util.FlexibleListCodec;
import com.qeapi.util.TextMutator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// target_entity_ids/tags only pick which entities are eligible; resolution to one concrete target happens once at accept time, in QuestEventHandler.resolveDeliveryTargetIfNeeded
public record DeliverItemTask(
        Optional<ResourceLocation> itemId,
        Optional<QuestItemDefinition> questItem,
        int amount,
        Optional<ResourceLocation> hasComponent,
        List<ResourceLocation> targetEntityIds,
        List<TagKey<EntityType<?>>> targetEntityTags,
        Optional<Integer> taskOrder,
        Optional<String> choiceGroup,
        Optional<ResourceLocation> textureOverrideId
) implements QuestTask {

    public static final MapCodec<DeliverItemTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("item_id").forGetter(DeliverItemTask::itemId),
                    QuestItemDefinition.CODEC.optionalFieldOf("quest_item").forGetter(DeliverItemTask::questItem),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(DeliverItemTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("has_component").forGetter(DeliverItemTask::hasComponent),
                    FlexibleListCodec.listOrSingle(ResourceLocation.CODEC).optionalFieldOf("target_entity_ids", List.of()).forGetter(DeliverItemTask::targetEntityIds),
                    FlexibleListCodec.listOrSingle(TagKey.codec(Registries.ENTITY_TYPE)).optionalFieldOf("target_entity_tags", List.of()).forGetter(DeliverItemTask::targetEntityTags),
                    Codec.INT.optionalFieldOf("task_order").forGetter(DeliverItemTask::taskOrder),
                    Codec.STRING.optionalFieldOf("choice_group").forGetter(DeliverItemTask::choiceGroup),
                    ResourceLocation.CODEC.optionalFieldOf("texture_override_id").forGetter(DeliverItemTask::textureOverrideId)
            ).apply(instance, DeliverItemTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestAPI.id("deliver_item");
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
        return "task.quest_api.deliver_item";
    }

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

    public boolean matchesTargetSelector(Entity candidate) {
        ResourceLocation candidateId = BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());

        if (!targetEntityIds.isEmpty() && targetEntityIds.stream().noneMatch(id -> candidateId.equals(id))) {
            return false;
        }
        if (!targetEntityTags.isEmpty() && targetEntityTags.stream().noneMatch(tag -> candidate.getType().is(tag))) {
            return false;
        }

        return !targetEntityIds.isEmpty() || !targetEntityTags.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ResourceLocation> itemId = Optional.empty();
        private Optional<QuestItemDefinition> questItem = Optional.empty();
        private int amount = 1;
        private Optional<ResourceLocation> hasComponent = Optional.empty();
        private List<ResourceLocation> targetEntityIds = List.of();
        private List<TagKey<EntityType<?>>> targetEntityTags = List.of();
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

        public Builder targetEntityId(ResourceLocation id) {
            return targetEntityIds(id);
        }

        public Builder targetEntityId(String id) {
            return targetEntityId(ResourceLocation.parse(id));
        }

        public Builder targetEntityTag(TagKey<EntityType<?>> tag) {
            return targetEntityTags(tag);
        }

        @SafeVarargs
        public final Builder targetEntityTags(TagKey<EntityType<?>>... tags) {
            this.targetEntityTags = List.of(tags);
            return this;
        }

        public Builder targetEntityIds(ResourceLocation... ids) {
            this.targetEntityIds = List.of(ids);
            return this;
        }

        public Builder targetEntityIds(String... ids) {
            this.targetEntityIds = java.util.Arrays.stream(ids).map(ResourceLocation::parse).toList();
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

        public DeliverItemTask build() {
            if (itemId.isEmpty() == questItem.isEmpty()) {
                throw new IllegalStateException("DeliverItemTask requires exactly one of itemId or questItem");
            }
            if (targetEntityIds.isEmpty() && targetEntityTags.isEmpty()) {
                throw new IllegalStateException("DeliverItemTask requires targetEntityIds or targetEntityTags");
            }
            return new DeliverItemTask(itemId, questItem, amount, hasComponent,
                    targetEntityIds, targetEntityTags, taskOrder, choiceGroup, textureOverrideId);
        }
    }
}

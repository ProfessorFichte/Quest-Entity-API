package com.qeapi.quest.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.qeapi.QuestEntityAPI;
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

// items are consumed when the quest reward is claimed
public record BringItemTask(
        ResourceLocation itemId,
        int amount,
        Optional<ResourceLocation> hasComponent
) implements QuestTask {

    public static final MapCodec<BringItemTask> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("item_id").forGetter(BringItemTask::itemId),
                    Codec.INT.optionalFieldOf("amount", 1).forGetter(BringItemTask::amount),
                    ResourceLocation.CODEC.optionalFieldOf("has_component").forGetter(BringItemTask::hasComponent)
            ).apply(instance, BringItemTask::new)
    );

    @Override
    public ResourceLocation getTypeId() {
        return QuestEntityAPI.id("bring_item");
    }

    @Override
    public Component getDisplayText(QuestProgress progress, int taskIndex) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        String itemName = item != null ? item.getDescription().getString() : itemId.toString();

        return TextMutator.mutate(
                Component.translatable(getDefaultTranslationKey()),
                Map.of(
                        "item_amount", String.valueOf(amount),
                        "item_name", itemName
                )
        );
    }

    @Override
    public String getDefaultTranslationKey() {
        return "task.qe_api.bring_item";
    }

    // Progress for this task is computed directly from inventory contents when claiming
    // rewards (see NeoForgeNetworking/FabricNetworking's BringItemTask handling), rather
    // than incrementally tracked like other tasks - isComplete's default still applies.
    @Override
    public int getTargetAmount() {
        return amount;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        ResourceLocation stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!stackItemId.equals(itemId)) {
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
        private ResourceLocation itemId;
        private int amount = 1;
        private Optional<ResourceLocation> hasComponent = Optional.empty();

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

        public Builder hasComponent(ResourceLocation componentId) {
            this.hasComponent = Optional.of(componentId);
            return this;
        }

        public BringItemTask build() {
            if (itemId == null) {
                throw new IllegalStateException("BringItemTask requires itemId");
            }
            return new BringItemTask(itemId, amount, hasComponent);
        }
    }
}
